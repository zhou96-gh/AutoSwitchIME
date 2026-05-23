package com.rimevim.ime

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.rimevim.caret.CaretColorManager
import com.rimevim.util.RimeVimLogger
import com.rimevim.util.VimModeChecker
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
 *
 * Normal 模式强制英文：
 * 当用户在 Normal 模式下手动切换输入法为中文时，本监听器会检测到状态变化，
 * 并强制将 IME 切换回英文，确保 Normal 模式始终为英文输入。
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

    @Volatile
    private var lastIsComposing: Boolean? = null

    // 当前 composing 状态（供 ImeStateDetector 查询）
    @Volatile
    var isComposing: Boolean = false
        private set

    // 防止递归：正在强制切换 IME 时不再处理状态文件变化
    @Volatile
    private var isForcingImeSwitch = false

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
        // 防止递归：正在强制切换 IME 时不再处理状态文件变化
        if (isForcingImeSwitch) {
            RimeVimLogger.debug("Skipping state file read during forced IME switch")
            return
        }

        try {
            if (!stateFile.exists()) return

            val content = stateFile.readText(Charsets.UTF_8).trim()
            if (content.isEmpty()) return

            val state = parseStateJson(content) ?: return

            // 检测状态是否发生变化
            if (state.asciiMode != lastAsciiMode || state.capsLock != lastCapsLock || state.isComposing != lastIsComposing) {
                RimeVimLogger.info(
                    "Rime state changed: ascii=${state.asciiMode}, caps=${state.capsLock}, composing=${state.isComposing}" +
                        " (was: ascii=$lastAsciiMode, caps=$lastCapsLock, composing=$lastIsComposing)"
                )
                lastAsciiMode = state.asciiMode
                lastCapsLock = state.capsLock
                lastIsComposing = state.isComposing

                // 更新 composing 状态（供 ImeStateDetector 查询）
                isComposing = state.isComposing

                // 同步更新 RimeController 的内部跟踪状态
                syncControllerState(state.asciiMode, state.capsLock)

                // Normal 模式强制英文：当用户在 Normal 模式下手动切换为中文时，强制回英文
                if (!state.asciiMode && isVimModeRequiresEnglish()) {
                    RimeVimLogger.info("Normal mode detected, forcing IME back to English (was manually switched to Chinese)")
                    forceEnglishMode()
                    return  // forceEnglishMode 会更新光标颜色，直接返回
                }

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
     * 检查当前是否处于 Normal/Visual/Select 模式（要求英文输入）
     * 使用统一的 VimModeChecker 工具类
     */
    private fun isVimModeRequiresEnglish(): Boolean {
        return VimModeChecker.isInNormalMode()
    }

    /**
     * 强制切换 IME 为英文模式
     * 设置标志防止递归处理状态文件变化
     */
    private fun forceEnglishMode() {
        isForcingImeSwitch = true
        try {
            // 使用 CountDownLatch 等待 invokeLater 完成
            val latch = java.util.concurrent.CountDownLatch(1)

            ApplicationManager.getApplication().invokeLater {
                try {
                    // 检查 IntelliJ 窗口是否聚焦，避免影响其他窗口
                    val isFocused = EditorFactory.getInstance().allEditors.any { it.contentComponent.hasFocus() }
                    if (!isFocused) {
                        RimeVimLogger.debug("IntelliJ window not focused, skipping force English mode")
                        return@invokeLater
                    }

                    val controller = ApplicationManager.getApplication()
                        .getService(RimeController::class.java)
                    controller?.setAsciiMode(true)
                    RimeVimLogger.info("Forced IME to English mode for Normal/Visual mode (window focused)")
                } catch (e: Exception) {
                    RimeVimLogger.warn("Failed to force English mode: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }

            // 等待 invokeLater 执行完成（最多 1 秒）
            latch.await(1, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            RimeVimLogger.warn("Exception in forceEnglishMode: ${e.message}")
        } finally {
            // 等待额外时间让 WeaselServer 执行完成和 Lua 脚本写入新状态
            Thread.sleep(200)
            isForcingImeSwitch = false
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
            // 简单 JSON 解析：{"ascii_mode": true, "caps_lock": false, "is_composing": false, "timestamp": 123}
            val asciiMode = extractBoolean(json, "ascii_mode") ?: return null
            val capsLock = extractBoolean(json, "caps_lock") ?: false
            val isComposing = extractBoolean(json, "is_composing") ?: false
            RimeState(asciiMode, capsLock, isComposing)
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
        val capsLock: Boolean,
        val isComposing: Boolean = false
    )
}
