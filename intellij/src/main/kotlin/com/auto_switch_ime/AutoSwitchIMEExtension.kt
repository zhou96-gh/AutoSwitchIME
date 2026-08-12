package com.auto_switch_ime

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.common.ModeChangeListener
import com.maddyhome.idea.vim.extension.VimExtension
import com.maddyhome.idea.vim.newapi.ij
import com.maddyhome.idea.vim.state.mode.Mode
import com.auto_switch_ime.caret.CaretColorManager
import com.auto_switch_ime.core.ime.ImeStateDetector
import com.auto_switch_ime.ime.AutoSwitchIMEController
import com.auto_switch_ime.settings.AutoSwitchIMESettings
import com.auto_switch_ime.util.AutoSwitchIMELogger
import com.auto_switch_ime.util.VimModeChecker

/**
 * IdeaVim 扩展：监听 Vim 模式变化，自动切换输入法并更新光标颜色
 *
 * 模式切换规则：
 * - Insert → 评估正则规则：中文规则→中文, 大写规则→大写, 默认→英文
 * - Normal → 始终保持英文，光标颜色显示实际输入状态
 * - 其他 normal-like 模式 → 进入时默认英文，之后允许手动切换，光标颜色显示实际输入状态
 */
class AutoSwitchIMEExtension : VimExtension, ModeChangeListener {

    private val controller: AutoSwitchIMEController by lazy {
        ApplicationManager.getApplication().getService(AutoSwitchIMEController::class.java)
    }

    override fun getName(): String = "auto_switch_ime"

    override fun init() {
        val settings = AutoSwitchIMESettings.instance
        AutoSwitchIMELogger.info("init() called, enabled=${settings.enabled}, log=[E:${settings.logError} W:${settings.logWarn} I:${settings.logInfo} D:${settings.logDebug}]")
        AutoSwitchIMELogger.info("WeaselServer path: ${controller.resolvePath() ?: "(not found)"}")

        if (!settings.enabled) {
            AutoSwitchIMELogger.info("AutoSwitchIME IME is disabled")
            return
        }

        AutoSwitchIMELogger.info("AutoSwitchIME IME extension initialized")

        // 初始化：检测当前 IME 状态并更新所有编辑器的光标颜色
        initializeImeState()

        // 注册模式变化监听器
        injector.listenersNotifier.modeChangeListeners.add(this)
        AutoSwitchIMELogger.info("ModeChangeListener registered")
    }

    /**
     * 初始化 IME 状态：检测当前输入法状态并更新所有编辑器的光标颜色
     */
    private fun initializeImeState() {
        try {
            val state = ImeStateDetector.getCurrentState(controller.stateWatcher, controller.getTrackedState())
            AutoSwitchIMELogger.info("Initializing IME state: ascii=${state.isAsciiMode}, caps=${state.isCapsLock}")

            val editors = EditorFactory.getInstance().allEditors
            for (editor in editors) {
                if (!editor.isDisposed) {
                    CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
                    val fileName = FileDocumentManager.getInstance().getFile(editor.document)?.name ?: "(unnamed)"
                    AutoSwitchIMELogger.debug("Initialized caret color for editor: $fileName")
                }
            }

            if (editors.isEmpty()) {
                AutoSwitchIMELogger.debug("No editors available during initialization, will initialize on editor creation")
            } else {
                AutoSwitchIMELogger.info("Initialized caret color for ${editors.size} editor(s)")
            }
        } catch (e: Exception) {
            AutoSwitchIMELogger.warn("Failed to initialize IME state", e)
        }
    }

    override fun modeChanged(editor: VimEditor, oldMode: Mode) {
        if (!AutoSwitchIMESettings.instance.enabled) return

        val currentMode = editor.mode
        AutoSwitchIMELogger.info("modeChanged: $oldMode → $currentMode")

        ApplicationManager.getApplication().invokeLater {
            val ijEditor = editor.ij
            if (ijEditor.isDisposed) {
                AutoSwitchIMELogger.debug("Editor disposed, skipping")
                return@invokeLater
            }

            val isNormalLikeMode = VimModeChecker.isNormalLikeMode(
                currentMode,
                ijEditor.selectionModel.hasSelection()
            )
            val isStrictNormalMode = VimModeChecker.isStrictNormalMode(
                currentMode,
                ijEditor.selectionModel.hasSelection()
            )
            controller.requestEditorUpdate(
                editor = ijEditor,
                source = "AutoSwitchIMEExtension",
                normalLikeOverride = isNormalLikeMode,
                strictNormalOverride = isStrictNormalMode
            )
        }
    }

    override fun dispose() {
        injector.listenersNotifier.modeChangeListeners.remove(this)
        AutoSwitchIMELogger.debug("AutoSwitchIME IME extension disposed")
    }
}
