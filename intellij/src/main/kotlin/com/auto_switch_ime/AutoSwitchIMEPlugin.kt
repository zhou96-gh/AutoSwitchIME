package com.auto_switch_ime

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.Alarm
import com.auto_switch_ime.caret.CaretColorManager
import com.auto_switch_ime.core.ime.ImeStateDetector
import com.auto_switch_ime.core.ime.NativeImeSys
import com.auto_switch_ime.ime.AutoSwitchIMEController
import com.auto_switch_ime.ime.AutoSwitchIMEStateWatcher
import com.auto_switch_ime.settings.AutoSwitchIMESettings
import com.auto_switch_ime.util.AutoSwitchIMELogger
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.Collections
import java.util.WeakHashMap

/**
 * 插件入口点：IDE 启动时注册编辑器监听器
 * 负责光标颜色更新和输入法切换（独立于 IdeaVim）
 *
 * 无 IdeaVim 时：始终按 Insert 模式处理，使用正则规则切换输入法
 * 有 IdeaVim 时：由 AutoSwitchIMEExtension 处理模式切换，本方法仅更新光标颜色
 */
class AutoSwitchIMEPlugin : ProjectActivity {

    // Cached service references – avoid getService() reflection lookup on every call
    private var cachedController: AutoSwitchIMEController? = null
    private var cachedStateWatcher: AutoSwitchIMEStateWatcher? = null

    // Debounce alarm for caretPositionChanged – coalesce rapid h/j/k/l key repeats
    // 独立 Disposable 做 parent：不注册到 project，避免 Project 关闭时 Alarm 同步 dispose 导致 "Already disposed"
    private val alarmParentDisposable = Disposer.newDisposable("AutoSwitchIME-CaretAlarm")
    private lateinit var caretDebounceAlarm: Alarm
    @Volatile
    private var lastCaretEditor: Editor? = null
    private val focusReleaseEditors = Collections.newSetFromMap(WeakHashMap<Editor, Boolean>())

    override suspend fun execute(project: Project) {
        try {
            AutoSwitchIMELogger.info("AutoSwitchIME IME plugin starting...")

            NativeImeSys.load(AutoSwitchIMELogger)

            val settings = AutoSwitchIMESettings.instance

            if (!settings.enabled) {
                AutoSwitchIMELogger.info("AutoSwitchIME IME is disabled in settings")
                return
            }

            // 初始化防抖 Alarm（独立 parent disposable，确保 Alarm 在 Project 关闭期间仍可用）
            caretDebounceAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, alarmParentDisposable)

            // 启动 Rime 状态文件监听器（通过 Service 获取并缓存）
            cachedStateWatcher = ApplicationManager.getApplication().getService(AutoSwitchIMEStateWatcher::class.java)
            cachedStateWatcher?.start()
            AutoSwitchIMELogger.info("AutoSwitchIMEStateWatcher started for manual IME switching detection")

            val ctrl = ApplicationManager.getApplication().getService(AutoSwitchIMEController::class.java)
            cachedController = ctrl

            AutoSwitchIMELogger.info("AutoSwitchIME IME initialized for project: ${project.name}")

            EditorFactory.getInstance().allEditors.forEach { registerFocusReleaseListener(it) }

            // 初始化：检测当前 IME 状态并更新光标颜色
            initializeImeState(project)

            // 监听所有编辑器的插入/退出事件
            setupEditorListeners(project)
        } catch (e: Exception) {
            // 使用 IntelliJ 原生 logger 确保异常一定能输出
            thisLogger().error("[AutoSwitchIME] Failed to initialize AutoSwitchIME IME plugin", e)
        }
    }

    private fun setupEditorListeners(project: Project) {
        val editorFactory = EditorFactory.getInstance()

        // 监听编辑器创建事件
        editorFactory.addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                if (editor.isDisposed) return

                registerFocusReleaseListener(editor)

                // 初始化编辑器状态
                updateEditorState(editor)
            }
        }, project)

        // 监听文档变化（输入时更新状态）
        editorFactory.eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (project.isDisposed) return
                val editor = EditorFactory.getInstance().allEditors.firstOrNull { it.document == event.document }
                    ?: return
                if (editor.isDisposed) return

                ApplicationManager.getApplication().invokeLater {
                    if (editor.isDisposed) return@invokeLater
                    updateEditorState(editor)
                }
            }
        }, project)

        // 监听光标变化（使用 Alarm 防抖，合并快速连续的光标移动事件）
        editorFactory.eventMulticaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                val editor = event.editor
                if (editor.isDisposed || project.isDisposed) return

                lastCaretEditor = editor
                if (!::caretDebounceAlarm.isInitialized) return
                try {
                    caretDebounceAlarm.cancelAllRequests()
                    caretDebounceAlarm.addRequest({
                        val currentEditor = lastCaretEditor
                        if (currentEditor != null && !currentEditor.isDisposed) {
                            ApplicationManager.getApplication().invokeLater {
                                if (!currentEditor.isDisposed) {
                                    updateEditorState(currentEditor)
                                }
                            }
                        }
                    }, 50)
                } catch (_: Exception) {
                    // Alarm already disposed (race condition during project shutdown)
                }
            }
        }, project)
    }

    private fun registerFocusReleaseListener(editor: Editor) {
        if (!focusReleaseEditors.add(editor)) return
        editor.contentComponent.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent?) {
                val controller = cachedController ?: ApplicationManager.getApplication()
                    .getService(AutoSwitchIMEController::class.java)
                    .also { cachedController = it }
                    ?: return
                controller.onEditorFocusLost(editor)
            }

            override fun focusGained(e: FocusEvent?) {
                val controller = cachedController ?: ApplicationManager.getApplication()
                    .getService(AutoSwitchIMEController::class.java)
                    .also { cachedController = it }
                    ?: return
                controller.onEditorFocusGained(editor)
            }
        })
    }

    /**
     * 更新编辑器状态 — 只需切换输入法，颜色由状态文件回调自动更新
     *
     * 无 IdeaVim 时：按 Insert 模式处理，使用正则规则切换输入法
     * 有 IdeaVim 时：只有 Insert 模式执行规则切换，其他 Vim 模式保留输入和原生光标表现
     *
     * 注意：
     * - Rime 正在输入（显示候选词窗口）时跳过输入法切换
     */
    private fun updateEditorState(editor: Editor) {
        if (!AutoSwitchIMESettings.instance.enabled) return
        val controller = cachedController ?: ApplicationManager.getApplication()
            .getService(AutoSwitchIMEController::class.java)
            .also { cachedController = it }
            ?: return
        controller.requestEditorUpdate(editor, "AutoSwitchIMEPlugin")
    }

    /**
     * 初始化 IME 状态：检测当前输入法状态并更新所有编辑器的光标颜色
     */
    private fun initializeImeState(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val ctrl = ApplicationManager.getApplication().getService(AutoSwitchIMEController::class.java)
                val state = if (ctrl != null) {
                    ImeStateDetector.getCurrentState(ctrl.stateWatcher, ctrl.getTrackedState())
                } else {
                    com.auto_switch_ime.core.ImeState(true, false)
                }
                AutoSwitchIMELogger.info("Initializing IME state: ascii=${state.isAsciiMode}, caps=${state.isCapsLock}")

                // 更新所有已打开编辑器的光标颜色
                val editors = EditorFactory.getInstance().allEditors
                for (editor in editors) {
                    if (!editor.isDisposed) {
                        if (com.auto_switch_ime.util.VimModeChecker.isNormalLikeMode(editor)) {
                            CaretColorManager.restoreCaretColor(editor)
                        } else {
                            CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
                        }
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
    }
}
