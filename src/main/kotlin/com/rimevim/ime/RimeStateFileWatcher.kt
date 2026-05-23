package com.rimevim.ime

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.rimevim.caret.CaretColorManager
import com.rimevim.util.RimeVimLogger
import java.io.File
import java.nio.file.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Rime 状态文件监听器
 * 监听 Weasel Lua 脚本写入的状态文件变化，同步更新光标颜色
 *
 * 架构：
 * Weasel Lua 脚本 → 写入 %TEMP%\rimevim-state.json → 本监听器检测变化 → 更新光标颜色
 */
class RimeStateFileWatcher {

    private var watcherThread: Thread? = null
    private var isRunning = false

    // 状态文件路径
    private val stateFile: File by lazy {
        File(System.getenv("TEMP") ?: ".", "rimevim-state.json")
    }

    // 上次解析的状态
    @Volatile
    private var lastAsciiMode: Boolean? = null

    @Volatile
    private var lastCapsLock: Boolean? = null

    /**
     * 启动文件监听
     */
    fun start() {
        if (isRunning) {
            RimeVimLogger.debug("RimeStateFileWatcher already running")
            return
        }

        RimeVimLogger.info("Starting RimeStateFileWatcher, monitoring: ${stateFile.absolutePath}")
        isRunning = true

        watcherThread = Thread({
            watchLoop()
        }, "RimeVim-StateFileWatcher").apply {
            isDaemon = true
            start()
        }

        // 初始化：读取当前状态文件（如果存在）
        readAndApplyState()
    }

    /**
     * 停止文件监听
     */
    fun stop() {
        isRunning = false
        watcherThread?.interrupt()
        watcherThread = null
        RimeVimLogger.info("RimeStateFileWatcher stopped")
    }

    /**
     * 文件监听主循环
     * 使用 Java NIO WatchService 监听文件变化
     */
    private fun watchLoop() {
        try {
            // 确保状态文件存在
            if (!stateFile.exists()) {
                stateFile.parentFile?.mkdirs()
                stateFile.createNewFile()
                RimeVimLogger.debug("Created initial state file: ${stateFile.absolutePath}")
            }

            val parentDir = stateFile.parentFile.toPath()
            val watchService = FileSystems.getDefault().newWatchService()

            // 监听父目录（因为 WatchService 只能监听目录）
            parentDir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE
            )

            RimeVimLogger.debug("Watching directory: $parentDir for rimevim-state.json changes")

            while (isRunning && !Thread.currentThread().isInterrupted) {
                val watchKey = watchService.poll(500, TimeUnit.MILLISECONDS) ?: continue

                for (event in watchKey.pollEvents()) {
                    val fileName = event.context()?.toString() ?: continue
                    if (fileName == "rimevim-state.json") {
                        RimeVimLogger.debug("State file change detected")
                        readAndApplyState()
                        break
                    }
                }

                // 重置 watch key 以继续监听
                if (!watchKey.reset()) {
                    RimeVimLogger.warn("Watch key no longer valid, restarting watcher")
                    watchService.close()
                    // 重新创建 watcher
                    Thread.sleep(1000)
                    if (isRunning) {
                        watchLoop()
                    }
                    return
                }
            }

            watchService.close()
        } catch (e: InterruptedException) {
            RimeVimLogger.debug("RimeStateFileWatcher interrupted")
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            RimeVimLogger.warn("Error in RimeStateFileWatcher", e)
        }
    }

    /**
     * 读取状态文件并应用光标颜色变化
     */
    private fun readAndApplyState() {
        try {
            if (!stateFile.exists()) return

            val content = stateFile.readText(Charsets.UTF_8).trim()
            if (content.isEmpty()) return

            val state = parseStateJson(content) ?: return

            // 检测状态是否发生变化
            if (state.asciiMode != lastAsciiMode || state.capsLock != lastCapsLock) {
                RimeVimLogger.info(
                    "Rime state changed: ascii=${state.asciiMode}, caps=${state.capsLock}" +
                        " (was: ascii=$lastAsciiMode, caps=$lastCapsLock)"
                )
                lastAsciiMode = state.asciiMode
                lastCapsLock = state.capsLock

                // 同步更新 RimeController 的内部跟踪状态
                syncControllerState(state.asciiMode, state.capsLock)

                // 在 EDT 中更新光标颜色
                ApplicationManager.getApplication().invokeLater {
                    updateAllCaretColors(state.asciiMode, state.capsLock)
                }
            }
        } catch (e: Exception) {
            // 忽略解析错误（文件可能正在写入中）
            RimeVimLogger.debug("Failed to parse state file (may be in progress): ${e.message}")
        }
    }

    /**
     * 同步 RimeController 的内部跟踪状态
     * 确保自动切换时基于正确的当前状态
     */
    private fun syncControllerState(asciiMode: Boolean, capsLock: Boolean) {
        try {
            val controller = ApplicationManager.getApplication()
                .getService(RimeController::class.java)
            controller?.syncTrackedState(asciiMode, capsLock)
        } catch (e: Exception) {
            RimeVimLogger.debug("Failed to sync controller state: ${e.message}")
        }
    }

    /**
     * 解析状态 JSON
     * 手动解析避免引入 JSON 库依赖
     */
    private fun parseStateJson(json: String): RimeState? {
        return try {
            // 简单 JSON 解析：{"ascii_mode": true, "caps_lock": false, "timestamp": 123}
            val asciiMode = extractBoolean(json, "ascii_mode") ?: return null
            val capsLock = extractBoolean(json, "caps_lock") ?: false
            RimeState(asciiMode, capsLock)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从 JSON 中提取布尔值
     */
    private fun extractBoolean(json: String, key: String): Boolean? {
        val pattern = """"$key"\s*:\s*(true|false)"""
        val regex = Regex(pattern)
        val match = regex.find(json) ?: return null
        return match.groupValues[1].toBoolean()
    }

    /**
     * 更新所有编辑器的光标颜色
     */
    private fun updateAllCaretColors(isAsciiMode: Boolean, isCapsLock: Boolean) {
        val editors = EditorFactory.getInstance().allEditors
        for (editor in editors) {
            if (!editor.isDisposed) {
                CaretColorManager.updateCaretColor(editor, isAsciiMode, isCapsLock)
            }
        }
        if (editors.isNotEmpty()) {
            RimeVimLogger.debug("Updated caret color for ${editors.size} editor(s)")
        }
    }

    /**
     * Rime 状态数据类
     */
    data class RimeState(
        val asciiMode: Boolean,
        val capsLock: Boolean
    )
}
