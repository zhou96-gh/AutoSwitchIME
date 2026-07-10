package com.auto_switch_ime.core.ime

import com.auto_switch_ime.core.ImeState
import com.auto_switch_ime.core.util.Logger
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.TimeUnit

/**
 * IME 状态文件监听器
 * 监听 Rime Lua 脚本写入的状态文件变化
 * 平台无关，通过回调通知状态变化
 */
class StateWatcher(
    private val stateFilePath: String,
    private val logger: Logger,
    private val onStateChanged: (ImeState) -> Unit
) {
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
                // 确保状态文件存在
                if (!stateFile.exists()) {
                    stateFile.parentFile?.mkdirs()
                    stateFile.createNewFile()
                    logger.debug("Created initial state file: $stateFilePath")
                }

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
                    val watchKey = service.poll(500, TimeUnit.MILLISECONDS) ?: continue

                    for (event in watchKey.pollEvents()) {
                        val context = event.context()?.toString() ?: continue
                        if (context.contains("ime-state") || context == fileName) {
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

    private fun readAndApplyState() {
        // 防止递归
        if (isForcingImeSwitch) {
            logger.debug("Skipping state file read during forced IME switch")
            return
        }

        try {
            val stateFile = File(stateFilePath)
            if (!stateFile.exists()) return

            val content = stateFile.readText(Charsets.UTF_8).trim()
            if (content.isEmpty()) return

            val state = parseImeStateJson(content) ?: return

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

}

internal fun parseImeStateJson(json: String): ImeState? {
    return try {
        val asciiMode = extractBoolean(json, "ascii_mode") ?: return null
        val capsLock = extractBoolean(json, "caps_lock") ?: false
        val isComposing = extractBoolean(json, "is_composing") ?: false
        ImeState(asciiMode, capsLock, isComposing)
    } catch (e: Exception) {
        null
    }
}

private fun extractBoolean(json: String, key: String): Boolean? {
    val pattern = """"$key"\s*:\s*(true|false)"""
    val regex = Regex(pattern)
    val match = regex.find(json) ?: return null
    return match.groupValues[1].toBoolean()
}
