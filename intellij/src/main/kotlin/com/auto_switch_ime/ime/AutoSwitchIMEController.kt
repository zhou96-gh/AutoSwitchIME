package com.auto_switch_ime.ime

import com.auto_switch_ime.adapter.IntelliJLogger
import com.auto_switch_ime.caret.CaretColorManager
import com.auto_switch_ime.core.ImeAction
import com.auto_switch_ime.core.ImeConfig
import com.auto_switch_ime.core.ImeState
import com.auto_switch_ime.core.ImeType
import com.auto_switch_ime.core.NormalModePolicy
import com.auto_switch_ime.core.coordinator.CoordinatorRequest
import com.auto_switch_ime.core.coordinator.CoordinatorState
import com.auto_switch_ime.core.ime.ImeStateDetector
import com.auto_switch_ime.core.ime.NativeImeSys
import com.auto_switch_ime.core.ime.RimeImeProvider
import com.auto_switch_ime.core.ime.StateWatcher
import com.auto_switch_ime.core.ime.WeaselPathDetector
import com.auto_switch_ime.settings.AutoSwitchIMESettings
import com.auto_switch_ime.util.ActionDeduplicator
import com.auto_switch_ime.util.AutoSwitchIMELogger
import com.auto_switch_ime.util.InsertModeDecision
import com.auto_switch_ime.util.VimModeChecker
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import kotlinx.coroutines.runBlocking
import java.util.ArrayDeque
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.APP)
class AutoSwitchIMEController : Disposable {
    private val logger = IntelliJLogger
    private val switchExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AutoSwitchIME-Coordinator").apply { isDaemon = true }
    }
    private val coordinatorState = CoordinatorState<Editor>()
    private val disposed = AtomicBoolean(false)
    private val mailboxLock = Any()
    private val events = ArrayDeque<CoordinatorEvent>()
    private val normalLikeDefaultsApplied = Collections.synchronizedMap(WeakHashMap<Editor, Boolean>())
    private var drainScheduled = false

    private val providerDelegate = lazy {
        val config = ImeConfig(type = ImeType.RIME)
        RimeImeProvider(config, logger).also { imeProvider ->
            imeProvider.onStateChanged = ::onPhysicalStateChanged
            imeProvider.start()
        }
    }
    private val provider: RimeImeProvider by providerDelegate

    val stateWatcher: StateWatcher get() = provider.stateWatcher

    fun getTrackedState(): ImeState = provider.getTrackedState()

    fun setAsciiMode(ascii: Boolean) {
        runBlocking { provider.setAsciiMode(ascii) }
    }

    fun setCapsMode() {
        runBlocking { provider.setCapsMode() }
    }

    fun releaseOwnedCapsLock() {
        runBlocking { provider.releaseOwnedCapsLock() }
    }

    fun resolvePath(): String? = WeaselPathDetector.detect(logger)

    fun requestEditorUpdate(
        editor: Editor,
        source: String,
        normalLikeOverride: Boolean? = null,
        strictNormalOverride: Boolean? = null
    ) {
        if (disposed.get()) return

        val captureEvent = captureEvent@{
            if (disposed.get() || editor.isDisposed) return@captureEvent

            val settings = AutoSwitchIMESettings.instance
            coordinatorState.setEnabled(settings.enabled)
            if (!settings.enabled || !editor.contentComponent.hasFocus()) return@captureEvent

            coordinatorState.focusEditor(editor)
            val normalLike = normalLikeOverride ?: VimModeChecker.isNormalLikeMode(editor)
            val strictNormal = strictNormalOverride ?: VimModeChecker.isStrictNormalMode(editor)
            val decision = if (normalLike) null else InsertModeDecision.evaluate(editor, settings)
            if (!normalLike) normalLikeDefaultsApplied.remove(editor)
            val action = NormalModePolicy.resolveAction(
                normalLike,
                strictNormal,
                normalLikeDefaultsApplied[editor] == true
            ) ?: decision!!.action
            val duplicated = ActionDeduplicator.shouldSkip(editor, action)

            if (duplicated && !normalLike) {
                AutoSwitchIMELogger.debug("$source: duplicated $action action skipped")
                return@captureEvent
            }

            val request = coordinatorState.newRequest(editor) ?: return@captureEvent
            postEvent(
                CoordinatorEvent.EditorContext(
                    editor = editor,
                    request = request,
                    action = action,
                    normalLike = normalLike,
                    context = decision?.context,
                    source = source
                )
            )
        }

        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            captureEvent()
        } else {
            app.invokeLater { captureEvent() }
        }
    }

    fun onEditorFocusLost(editor: Editor) {
        if (disposed.get()) return
        normalLikeDefaultsApplied.remove(editor)
        if (!coordinatorState.loseFocus(editor)) return
        postEvent(CoordinatorEvent.FocusLost(editor), discardPendingContexts = true)
    }

    fun onEditorFocusGained(editor: Editor) {
        requestEditorUpdate(editor, "EditorFocus")
    }

    private fun onPhysicalStateChanged(state: ImeState) {
        if (disposed.get()) return
        postEvent(CoordinatorEvent.PhysicalStateChanged(state))
    }

    private fun postEvent(event: CoordinatorEvent, discardPendingContexts: Boolean = false) {
        if (disposed.get() && event !is CoordinatorEvent.Shutdown) return

        synchronized(mailboxLock) {
            if (discardPendingContexts || event is CoordinatorEvent.EditorContext) {
                events.removeIf { it is CoordinatorEvent.EditorContext }
            }
            events.addLast(event)
            if (drainScheduled) return
            drainScheduled = true
            try {
                switchExecutor.execute(::drainEvents)
            } catch (_: RejectedExecutionException) {
                drainScheduled = false
            }
        }
    }

    private fun drainEvents() {
        while (true) {
            val event = synchronized(mailboxLock) {
                if (events.isEmpty()) {
                    drainScheduled = false
                    return
                }
                events.removeFirst()
            }

            try {
                when (event) {
                    is CoordinatorEvent.EditorContext -> handleEditorContext(event)
                    is CoordinatorEvent.FocusLost -> handleFocusLost(event)
                    is CoordinatorEvent.PhysicalStateChanged -> handlePhysicalStateChanged(event.state)
                    CoordinatorEvent.Shutdown -> handleShutdown()
                }
            } catch (e: Exception) {
                logger.warn("IME coordinator event failed", e)
            }
        }
    }

    private fun handleEditorContext(event: CoordinatorEvent.EditorContext) {
        if (!isCurrent(event)) return

        if (!event.normalLike && !provider.getTrackedState().isAsciiMode) {
            val composing = ImeStateDetector.isComposing(provider.stateWatcher)
            if (composing) {
                AutoSwitchIMELogger.debug("${event.source}: Rime is composing, skipping IME switch")
                updateCaretWhenCurrent(event, provider.getTrackedState())
                return
            }
        }

        event.context?.let {
            AutoSwitchIMELogger.info("Insert context: before='${it.before}', after='${it.after}'")
        }

        if (event.normalLike && event.action == ImeAction.UNCHANGED) {
            ApplicationManager.getApplication().invokeLater {
                if (isCurrent(event)) CaretColorManager.restoreCaretColor(event.editor)
            }
            return
        }

        val isCurrent = { isCurrent(event) }
        runBlocking {
            when (event.action) {
                ImeAction.CHINESE -> provider.setAsciiMode(false, isCurrent)
                ImeAction.CAPS -> provider.setCapsMode(isCurrent)
                ImeAction.ENGLISH -> provider.setAsciiMode(true, isCurrent)
                ImeAction.UNCHANGED -> return@runBlocking
            }
        }

        if (!isCurrent()) return
        if (event.normalLike) {
            normalLikeDefaultsApplied[event.editor] = true
            ApplicationManager.getApplication().invokeLater {
                if (isCurrent(event)) CaretColorManager.restoreCaretColor(event.editor)
            }
            return
        }

        val targetState = when (event.action) {
            ImeAction.CHINESE -> ImeState(false, false)
            ImeAction.CAPS -> ImeState(true, true)
            ImeAction.ENGLISH -> ImeState(true, false)
            ImeAction.UNCHANGED -> return
        }
        updateCaretWhenCurrent(event, targetState)
    }

    private fun updateCaretWhenCurrent(event: CoordinatorEvent.EditorContext, state: ImeState) {
        ApplicationManager.getApplication().invokeLater {
            if (isCurrent(event)) {
                CaretColorManager.updateCaretColor(event.editor, state.isAsciiMode, state.isCapsLock)
            }
        }
    }

    private fun isCurrent(event: CoordinatorEvent.EditorContext): Boolean {
        val platformFocused = !event.editor.isDisposed && event.editor.contentComponent.hasFocus()
        val foregroundProcessId = NativeImeSys.imeForegroundProcessId()
        val sameProcess = foregroundProcessId != 0L && foregroundProcessId == ProcessHandle.current().pid()
        return coordinatorState.isCurrent(event.request, platformFocused && sameProcess)
    }

    private fun handleFocusLost(event: CoordinatorEvent.FocusLost) {
        AutoSwitchIMELogger.debug("Editor focus lost, releasing owned CapsLock: ${event.editor.hashCode()}")
        runBlocking { provider.releaseOwnedCapsLock() }
    }

    private fun handlePhysicalStateChanged(state: ImeState) {
        ActionDeduplicator.invalidate()
        ApplicationManager.getApplication().invokeLater {
            if (disposed.get()) return@invokeLater
            val focusedEditor = EditorFactory.getInstance().allEditors.firstOrNull {
                !it.isDisposed && it.contentComponent.hasFocus()
            } ?: return@invokeLater
            if (VimModeChecker.isNormalLikeMode(focusedEditor)) {
                CaretColorManager.restoreCaretColor(focusedEditor)
                if (NormalModePolicy.shouldEnforceEnglish(
                        VimModeChecker.isStrictNormalMode(focusedEditor),
                        state.isAsciiMode
                    )) {
                    requestEditorUpdate(focusedEditor, "PhysicalStateChanged")
                }
                return@invokeLater
            }

            CaretColorManager.updateCaretColor(focusedEditor, state.isAsciiMode, state.isCapsLock)
        }
    }

    private fun handleShutdown() {
        if (providerDelegate.isInitialized()) {
            runBlocking { provider.releaseOwnedCapsLock() }
        }
    }

    override fun dispose() {
        if (!disposed.compareAndSet(false, true)) return

        coordinatorState.shutdown()
        postEvent(CoordinatorEvent.Shutdown, discardPendingContexts = true)
        switchExecutor.shutdown()
        try {
            if (!switchExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                switchExecutor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            switchExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        } finally {
            if (providerDelegate.isInitialized()) {
                provider.dispose()
            }
        }
    }

    private sealed interface CoordinatorEvent {
        data class EditorContext(
            val editor: Editor,
            val request: CoordinatorRequest<Editor>,
            val action: ImeAction,
            val normalLike: Boolean,
            val context: InsertModeDecision.Context?,
            val source: String
        ) : CoordinatorEvent

        data class FocusLost(val editor: Editor) : CoordinatorEvent
        data class PhysicalStateChanged(val state: ImeState) : CoordinatorEvent
        data object Shutdown : CoordinatorEvent
    }

    companion object {
        fun getInstance(): AutoSwitchIMEController = service()
    }
}
