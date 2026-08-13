package com.auto_switch_ime.core.ime

import com.auto_switch_ime.core.ImeState
import com.auto_switch_ime.core.util.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.TimeUnit

/**
 * IME 状态文件监听器
 * 监听 Rime Lua 脚本写入的状态文件变化
 * 平台无关，通过回调通知状态变化
 */
class RimeStateWatcher(
    private val stateFilePath: String,
    private val logger: Logger,
    private val onStateChanged: (ImeState) -> Unit
) {
    private val sessionTracker = RimeSessionTracker()
    private var watcherThread: Thread? = null
    private var isRunning = false

    @Volatile
    var isComposing: Boolean = false
        private set

    @Volatile
    var lastAsciiMode: Boolean = true
        private set

    @Volatile
    var lastCapsLock: Boolean = false
        private set

    // 防止递归：正在强制切换 IME 时不再处理状态文件变化
    @Volatile
    var isForcingImeSwitch: Boolean = false

    /**
     * 启动监听
     */
    fun start() {
        if (isRunning) {
            logger.debug("StateWatcher already running")
            return
        }

        logger.info("Starting StateWatcher, monitoring: $stateFilePath")
        isRunning = true

        watcherThread = Thread({
            watchLoop()
        }, "AutoSwitchIME-StateWatcher").apply {
            isDaemon = true
            start()
        }

        // 初始化：读取当前状态文件（如果存在）
        readAndApplyState()
    }

    /**
     * 停止监听
     */
    fun stop() {
        isRunning = false
        watcherThread?.interrupt()
        watcherThread = null
        logger.info("StateWatcher stopped")
    }

    private fun watchLoop() {
        val stateFile = File(stateFilePath)

        while (isRunning && !Thread.currentThread().isInterrupted) {
            var shouldRestart = false
            var watchService: java.nio.file.WatchService? = null
            try {
                val parentDir = stateFile.parentFile.toPath()
                val service = FileSystems.getDefault().newWatchService()
                watchService = service
                val fileName = stateFile.name

                // 监听父目录
                parentDir.register(
                    service,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE
                )

                logger.debug("Watching directory: $parentDir for $fileName changes")

                while (isRunning && !Thread.currentThread().isInterrupted) {
                    val watchKey = service.poll(500, TimeUnit.MILLISECONDS)
                    if (watchKey == null) {
                        readAndApplyState()
                        continue
                    }

                    for (event in watchKey.pollEvents()) {
                        val context = event.context()?.toString() ?: continue
                        if (context == fileName) {
                            readAndApplyState()
                            break
                        }
                    }

                    // 重置 watch key 以继续监听
                    if (!watchKey.reset()) {
                        logger.warn("Watch key no longer valid, restarting watcher")
                        shouldRestart = true
                        break
                    }
                }
            } catch (e: InterruptedException) {
                logger.debug("StateWatcher interrupted")
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                logger.warn("Error in StateWatcher: ${e.message}", e)
                shouldRestart = isRunning && !Thread.currentThread().isInterrupted
            } finally {
                try {
                    watchService?.close()
                } catch (_: Exception) {
                    // Ignore close failures during watcher restart/shutdown.
                }
            }

            if (shouldRestart && isRunning && !Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    logger.debug("StateWatcher interrupted")
                    Thread.currentThread().interrupt()
                }
            } else {
                break
            }
        }
    }

    fun refresh() {
        readAndApplyState()
    }

    private fun readAndApplyState() {
        // 防止递归
        if (isForcingImeSwitch) {
            logger.debug("Skipping state file read during forced IME switch")
            return
        }

        try {
            val update = readStateFile(stateFilePath) ?: return
            if (!sessionTracker.accept(update)) return
            val state = update.state

            // 检测状态是否发生变化
            if (state.isAsciiMode != lastAsciiMode || state.isCapsLock != lastCapsLock || state.isComposing != isComposing) {
                logger.info(
                    "IME state changed: ascii=${state.isAsciiMode}, caps=${state.isCapsLock}, composing=${state.isComposing}"
                )
                lastAsciiMode = state.isAsciiMode
                lastCapsLock = state.isCapsLock
                isComposing = state.isComposing

                // 通知回调
                onStateChanged(state)
            }
        } catch (e: Exception) {
            // 忽略解析错误（文件可能正在写入中）
            logger.debug("Failed to parse state file (may be in progress): ${e.message}")
        }
    }

    private fun readStateFile(path: String): RimeSessionState? {
        val stateFile = File(path)
        if (!stateFile.exists()) return null

        val content = stateFile.readText(Charsets.UTF_8).trim()
        if (content.isEmpty()) return null
        return parseRimeSessionStateJson(content)
    }

}

internal data class RimeSessionState(
    val state: ImeState,
    val sessionToken: String,
    val sequence: Long
)

internal class RimeSessionTracker {
    private var currentSessionToken: String? = null
    private var lastSequence: Long = 0

    fun accept(update: RimeSessionState): Boolean {
        if (update.sessionToken == currentSessionToken && update.sequence <= lastSequence) return false

        currentSessionToken = update.sessionToken
        lastSequence = update.sequence
        return true
    }
}

internal fun parseRimeSessionStateJson(json: String): RimeSessionState? {
    return try {
        val root = Json.parseToJsonElement(json).jsonObject
        if (root["protocol_version"]?.jsonPrimitive?.intOrNull != 2) return null
        if (root["provider"]?.jsonPrimitive?.contentOrNull != "rime") return null
        val asciiMode = root["ascii_mode"]?.jsonPrimitive?.booleanOrNull ?: return null
        val capsLock = root["caps_lock"]?.jsonPrimitive?.booleanOrNull ?: return null
        val isComposing = root["is_composing"]?.jsonPrimitive?.booleanOrNull ?: return null
        val sessionToken = root["session_token"]?.jsonPrimitive?.takeIf { it.isString }?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: return null
        val sequence = root["sequence"]?.jsonPrimitive?.longOrNull?.takeIf { it >= 1 } ?: return null
        root["timestamp"]?.jsonPrimitive?.longOrNull?.takeIf { it >= 1 } ?: return null

        RimeSessionState(
            state = ImeState(asciiMode, capsLock, isComposing),
            sessionToken = sessionToken,
            sequence = sequence
        )
    } catch (e: Exception) {
        null
    }
}
