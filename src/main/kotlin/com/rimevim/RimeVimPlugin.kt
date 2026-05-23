package com.rimevim

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
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.Alarm
import com.rimevim.caret.CaretColorManager
import com.rimevim.ime.ImeStateDetector
import com.rimevim.ime.RimeController
import com.rimevim.ime.RimeStateFileWatcher
import com.rimevim.settings.RimeVimSettings
import com.rimevim.util.RimeVimLogger
import com.rimevim.util.VimModeChecker
import java.util.regex.Pattern

/**
 * 插件入口点：IDE 启动时注册编辑器监听器
 * 负责光标颜色更新和输入法切换（独立于 IdeaVim）
 *
 * 无 IdeaVim 时：始终按 Insert 模式处理，使用正则规则切换输入法
 * 有 IdeaVim 时：由 RimeVimExtension 处理模式切换，本方法仅更新光标颜色
 */
class RimeVimPlugin : ProjectActivity {

    // Regex pattern cache – avoid Pattern.compile() on every caret move
    private val regexCache = HashMap<String, Pattern>(4)

    // Cached service references – avoid getService() reflection lookup on every call
    private var cachedRimeController: RimeController? = null
    private var cachedRimeStateFileWatcher: RimeStateFileWatcher? = null

    // Debounce alarm for caretPositionChanged – coalesce rapid h/j/k/l key repeats
    // Initialized in execute() with project as Disposable parent (required for non-Swing thread)
    private lateinit var caretDebounceAlarm: Alarm
    @Volatile
    private var lastCaretEditor: Editor? = null

    override suspend fun execute(project: Project) {
        try {
            RimeVimLogger.info("RimeVim IME plugin starting...")
            
            val settings = RimeVimSettings.instance
            
            if (!settings.enabled) {
                RimeVimLogger.info("RimeVim IME is disabled in settings")
                return
            }

            // 初始化防抖 Alarm（必须传入 Disposable parent，非 Swing 线程要求）
            caretDebounceAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)

            // 启动 Rime 状态文件监听器（通过 Service 获取并缓存）
            cachedRimeStateFileWatcher = ApplicationManager.getApplication().getService(RimeStateFileWatcher::class.java)
            cachedRimeStateFileWatcher?.start()
            RimeVimLogger.info("RimeStateFileWatcher started for manual IME switching detection")

            RimeVimLogger.info("RimeVim IME initialized for project: ${project.name}")

            // 初始化：检测当前 IME 状态并更新光标颜色
            initializeImeState(project)

            // 监听所有编辑器的插入/退出事件
            setupEditorListeners(project)
        } catch (e: Exception) {
            // 使用 IntelliJ 原生 logger 确保异常一定能输出
            thisLogger().error("[RimeVim] Failed to initialize RimeVim IME plugin", e)
        }
    }

    private fun setupEditorListeners(project: Project) {
        val editorFactory = EditorFactory.getInstance()

        // 监听编辑器创建事件
        editorFactory.addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                if (editor.isDisposed) return

                // 初始化编辑器状态
                updateEditorState(editor)
            }
        }, project)

        // 监听文档变化（输入时更新状态）
        editorFactory.eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
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
                if (editor.isDisposed) return

                lastCaretEditor = editor
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
            }
        }, project)
    }

    /**
     * 更新编辑器状态
     * 无 IdeaVim 时：按 Insert 模式处理，使用正则规则切换输入法并更新光标颜色
     * 有 IdeaVim 时：Normal/Visual 模式仅更新光标颜色，Insert/Replace 模式也执行输入法切换
     *
     * 注意：
     * - Rime 正在输入（显示候选词窗口）时跳过输入法切换
     * - 所有输入法切换通过 RimeController 执行，其内部已更新光标颜色
     * - 此处仅负责根据模式决定调用哪个方法，不重复更新光标颜色
     */
    private fun updateEditorState(editor: Editor) {
        if (!RimeVimSettings.instance.enabled) return

        ApplicationManager.getApplication().invokeLater {
            if (editor.isDisposed) return@invokeLater

            // 编辑器未聚焦时不执行自动切换
            if (!editor.contentComponent.hasFocus()) {
                RimeVimLogger.debug("RimeVimPlugin: editor not focused, skipping IME switch")
                return@invokeLater
            }

            val rimeController = cachedRimeController ?: ApplicationManager.getApplication()
                .getService(RimeController::class.java)
                .also { cachedRimeController = it }
                ?: run {
                    RimeVimLogger.warn("RimeController not available, skipping IME switch")
                    return@invokeLater
                }

            // 检测 Rime 是否正在输入（显示候选词窗口）
            val isComposing = ImeStateDetector.isComposing()
            if (isComposing) {
                RimeVimLogger.debug("Rime is composing, skipping IME switch")
            }

            // 统一逻辑：仅检查是否处于 Normal 模式，其他默认 Insert 模式
            if (VimModeChecker.isInNormalMode()) {
                // Normal/Visual/Select 模式：强制切换英文
                RimeVimLogger.debug("RimeVimPlugin (Normal mode): forcing ASCII (English)")
                if (!isComposing) {
                    rimeController.setAsciiMode(true)
                }
            } else {
                // Insert 模式（或无 IdeaVim）：执行正则规则评估
                val (before, after) = getLineContextText(editor)
                val settings = RimeVimSettings.instance
                val action = evaluateInsertModeRules(before, after, settings)

                if (!isComposing) {
                    when (action) {
                        ImeAction.CHINESE -> {
                            RimeVimLogger.info("RimeVimPlugin (Insert mode): Chinese mode")
                            rimeController.setAsciiMode(false)
                        }
                        ImeAction.CAPS -> {
                            RimeVimLogger.info("RimeVimPlugin (Insert mode): Caps mode")
                            rimeController.setCapsMode()
                        }
                        ImeAction.ENGLISH -> {
                            RimeVimLogger.info("RimeVimPlugin (Insert mode): English mode")
                            rimeController.setAsciiMode(true)
                        }
                        ImeAction.UNCHANGED -> {
                            RimeVimLogger.debug("RimeVimPlugin (Insert mode): IME unchanged")
                        }
                    }
                } else {
                    RimeVimLogger.debug("RimeVimPlugin (Insert mode): skipping due to composing")
                }
            }
        }
    }

    /**
     * 获取光标所在行的上下文文本（不跨行）
     * @return Pair(光标前文本，光标后文本)
     */
    private fun getLineContextText(editor: Editor): Pair<String, String> {
        try {
            val document = editor.document
            val caretOffset = editor.caretModel.primaryCaret.offset
            val lineNumber = document.getLineNumber(caretOffset)
            val lineStart = document.getLineStartOffset(lineNumber)
            val lineEnd = document.getLineEndOffset(lineNumber)

            val beforeStart = maxOf(lineStart, caretOffset - 5)
            val afterEnd = minOf(lineEnd, caretOffset + 5)
            val before = document.getText(com.intellij.openapi.util.TextRange(beforeStart, caretOffset))
            val after = document.getText(com.intellij.openapi.util.TextRange(caretOffset, afterEnd))
            return Pair(before, after)
        } catch (e: Exception) {
            RimeVimLogger.warn("Failed to get line context text", e)
            return Pair("", "")
        }
    }

    /**
     * 根据配置的正则规则评估 Insert 模式下的输入法状态
     * - 中文规则：光标前或光标后任一匹配时切换为中文
     * - 大写规则：光标前或光标后任一匹配时切换为大写
     * 优先级：中文规则 → 大写规则 → 默认英文
     */
    private fun evaluateInsertModeRules(
        before: String,
        after: String,
        settings: RimeVimSettings
    ): ImeAction {
        // 1. 检查中文规则：前后任一匹配
        val chineseBeforeMatch = matchesRegex(settings.insertModeChineseBeforeRegex, before)
        val chineseAfterMatch = matchesRegex(settings.insertModeChineseAfterRegex, after)
        if (chineseBeforeMatch || chineseAfterMatch) {
            RimeVimLogger.debug("Chinese regex matched (before='$before' or after='$after')")
            return ImeAction.CHINESE
        }

        // 2. 检查大写规则：前后任一匹配
        val capsBeforeMatch = matchesRegex(settings.insertModeCapsBeforeRegex, before)
        val capsAfterMatch = matchesRegex(settings.insertModeCapsAfterRegex, after)
        if (capsBeforeMatch || capsAfterMatch) {
            RimeVimLogger.debug("Caps regex matched (before='$before' or after='$after')")
            return ImeAction.CAPS
        }

        // 默认：英文模式
        RimeVimLogger.debug("No regex matched before/after, defaulting to English")
        return ImeAction.ENGLISH
    }

    /**
     * 检查正则是否匹配（空规则视为匹配），复用已编译的 Pattern 避免重复编译
     */
    private fun matchesRegex(pattern: String, text: String): Boolean {
        if (pattern.isBlank()) return true
        return try {
            val compiled = regexCache.getOrPut(pattern) { Pattern.compile(pattern) }
            compiled.matcher(text).find()
        } catch (e: Exception) {
            RimeVimLogger.warn("Invalid regex: $pattern", e)
            false
        }
    }

    /**
     * 初始化 IME 状态：检测当前输入法状态并更新所有编辑器的光标颜色
     */
    private fun initializeImeState(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val state = ImeStateDetector.getCurrentState()
                RimeVimLogger.info("Initializing IME state: ascii=${state.isAsciiMode}, caps=${state.isCapsLock}")

                // 更新所有已打开编辑器的光标颜色
                val editors = EditorFactory.getInstance().allEditors
                for (editor in editors) {
                    if (!editor.isDisposed) {
                        CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
                        val fileName = FileDocumentManager.getInstance().getFile(editor.document)?.name ?: "(unnamed)"
                        RimeVimLogger.debug("Initialized caret color for editor: $fileName")
                    }
                }

                if (editors.isEmpty()) {
                    RimeVimLogger.debug("No editors available during initialization, will initialize on editor creation")
                } else {
                    RimeVimLogger.info("Initialized caret color for ${editors.size} editor(s)")
                }
            } catch (e: Exception) {
                RimeVimLogger.warn("Failed to initialize IME state", e)
            }
        }
    }
}
