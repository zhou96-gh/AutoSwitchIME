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
import com.auto_switch_ime.core.ImeAction
import com.auto_switch_ime.core.ime.ImeStateDetector
import com.auto_switch_ime.ime.AutoSwitchIMEController
import com.auto_switch_ime.settings.AutoSwitchIMESettings
import com.auto_switch_ime.util.ActionDeduplicator
import com.auto_switch_ime.util.AutoSwitchIMELogger
import com.auto_switch_ime.util.InsertModeDecision
import com.auto_switch_ime.util.VimModeChecker

/**
 * IdeaVim 扩展：监听 Vim 模式变化，自动切换输入法并更新光标颜色
 *
 * 模式切换规则：
 * - Insert → 评估正则规则：中文规则→中文, 大写规则→大写, 默认→英文
 * - 其他 Vim 模式 → 按 Normal 处理，强制英文（ASCII）
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

            if (!ijEditor.contentComponent.hasFocus()) {
                AutoSwitchIMELogger.debug("AutoSwitchIMEExtension: editor not focused, skipping IME switch")
                return@invokeLater
            }

            val isNormalLikeMode = VimModeChecker.isNormalLikeMode(
                currentMode,
                ijEditor.selectionModel.hasSelection()
            )

            if (!isNormalLikeMode && !controller.getTrackedState().isAsciiMode) {
                val isComposing = ImeStateDetector.isComposing(controller.stateWatcher)
                if (isComposing) {
                    AutoSwitchIMELogger.info("AutoSwitchIMEExtension: Rime is composing, skipping IME switch")
                    val state = controller.getTrackedState()
                    CaretColorManager.updateCaretColor(ijEditor, state.isAsciiMode, state.isCapsLock)
                    return@invokeLater
                }
            }

            if (isNormalLikeMode) {
                if (ActionDeduplicator.shouldSkip(ijEditor, ImeAction.ENGLISH)) {
                    AutoSwitchIMELogger.debug("AutoSwitchIMEExtension: duplicated English action skipped")
                } else {
                    AutoSwitchIMELogger.info("Normal-like mode → forcing ASCII (English)")
                }
                controller.setAsciiMode(true)
                CaretColorManager.updateCaretColor(ijEditor, true, false)
                return@invokeLater
            }

            when (currentMode) {
                is Mode.INSERT -> {
                    val decision = InsertModeDecision.evaluate(ijEditor)
                    AutoSwitchIMELogger.info("Insert context: before='${decision.context.before}', after='${decision.context.after}'")
                    val action = decision.action
                    if (ActionDeduplicator.shouldSkip(ijEditor, action)) {
                        AutoSwitchIMELogger.debug("AutoSwitchIMEExtension: duplicated $action action skipped")
                        return@invokeLater
                    }

                    when (action) {
                        ImeAction.CHINESE -> {
                            AutoSwitchIMELogger.info("Insert mode → Chinese (regex matched)")
                            controller.setAsciiMode(false)
                            CaretColorManager.updateCaretColor(ijEditor, false, false)
                        }
                        ImeAction.CAPS -> {
                            AutoSwitchIMELogger.info("Insert mode → Caps (regex matched)")
                            controller.setCapsMode()
                            CaretColorManager.updateCaretColor(ijEditor, true, true)
                        }
                        ImeAction.ENGLISH -> {
                            AutoSwitchIMELogger.info("Insert mode → English (default)")
                            controller.setAsciiMode(true)
                            CaretColorManager.updateCaretColor(ijEditor, true, false)
                        }
                        ImeAction.UNCHANGED -> {
                            AutoSwitchIMELogger.debug("Insert mode → IME unchanged")
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    override fun dispose() {
        injector.listenersNotifier.modeChangeListeners.remove(this)
        AutoSwitchIMELogger.debug("AutoSwitchIME IME extension disposed")
    }
}
