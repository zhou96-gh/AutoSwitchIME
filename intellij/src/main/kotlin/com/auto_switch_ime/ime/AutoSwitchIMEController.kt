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
import com.auto_switch_ime.core.ime.ImeGateway
import com.auto_switch_ime.core.ime.input.ImeProviderFactory
import com.auto_switch_ime.core.ime.input.RimeImeProvider
import com.auto_switch_ime.core.ime.input.WeaselPathDetector
import com.auto_switch_ime.core.ime.system.NativeImeSys
import com.auto_switch_ime.core.ime.system.SystemImeProviderRegistry
import com.auto_switch_ime.core.ime.system.SystemType
import com.auto_switch_ime.core.ime.system.WindowsSystemImeProvider
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
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.Timer

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
    private val strictNormalEditors = Collections.synchronizedMap(WeakHashMap<Editor, Boolean>())
    private var drainScheduled = false
    private val physicalStatePollTimer = Timer(200) { requestPhysicalStateRefresh() }.apply {
        isRepeats = true
        start()
    }
    private val keyEventDispatcher = java.awt.KeyEventDispatcher { event ->
        if (event.id == KeyEvent.KEY_RELEASED && AutoSwitchIMESettings.instance.enabled) {
            requestPhysicalStateRefresh()
        }
        false
    }

    init {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyEventDispatcher)
    }

    private val gatewayDelegate = lazy {
        ImeProviderFactory.register(ImeType.RIME) { config, providerLogger ->
            RimeImeProvider(config, providerLogger)
        }
        val settings = AutoSwitchIMESettings.instance
        val config = ImeConfig(
            type = ImeType.fromConfig(settings.imeType),
            weaselServerPath = settings.weaselServerPath.ifBlank { null }
        )
        val provider = ImeProviderFactory.createProvider(config, logger)
        val systems = SystemImeProviderRegistry().apply {
            register(SystemType.WINDOWS, ::WindowsSystemImeProvider)
        }
        val system = systems.create(SystemType.current())
        ImeGateway(provider, system, logger).also { gateway ->
            gateway.onStateChanged = ::onPhysicalStateChanged
            gateway.start()
        }
    }
    private val gateway: ImeGateway by gatewayDelegate

    fun getTrackedState(): ImeState = gateway.getTrackedState()

    fun getCurrentState(): ImeState {
        val editor = EditorFactory.getInstance().allEditors.firstOrNull(::isPlatformEditorFocused)
            ?: return gateway.getTrackedState()
        return if (isPlatformEditorFocused(editor)) gateway.getCurrentState() else gateway.getTrackedState()
    }

    fun setAsciiMode(ascii: Boolean) {
        runBlocking { gateway.setAsciiMode(ascii) }
    }

    fun setCapsMode() {
        runBlocking { gateway.setCapsMode() }
    }

    fun releaseOwnedCapsLock() {
        runBlocking { gateway.releaseOwnedCapsLock() }
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
            strictNormalEditors[editor] = strictNormal
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
                    strictNormal = strictNormal,
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
        strictNormalEditors.remove(editor)
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

    private fun requestPhysicalStateRefresh() {
        if (disposed.get()) return
        val focusedEditor = EditorFactory.getInstance().allEditors.firstOrNull {
            !it.isDisposed && it.contentComponent.hasFocus()
        } ?: return
        postEvent(CoordinatorEvent.RefreshPhysicalState(focusedEditor))
    }

    private fun postEvent(event: CoordinatorEvent, discardPendingContexts: Boolean = false) {
        if (disposed.get() && event !is CoordinatorEvent.Shutdown) return

        synchronized(mailboxLock) {
            if (discardPendingContexts || event is CoordinatorEvent.EditorContext) {
                events.removeIf { it is CoordinatorEvent.EditorContext }
            }
            if (event is CoordinatorEvent.PhysicalStateChanged) {
                events.removeIf { it is CoordinatorEvent.PhysicalStateChanged }
            }
            if (event is CoordinatorEvent.RefreshPhysicalState) {
                events.removeIf { it is CoordinatorEvent.RefreshPhysicalState }
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
                    is CoordinatorEvent.RefreshPhysicalState -> handleRefreshPhysicalState(event.editor)
                    CoordinatorEvent.Shutdown -> handleShutdown()
                }
            } catch (e: Exception) {
                logger.warn("IME coordinator event failed", e)
            }
        }
    }

    private fun handleEditorContext(event: CoordinatorEvent.EditorContext) {
        if (!isCurrent(event)) return
        gateway.refreshState()
        if (!isCurrent(event)) return

        if (!event.normalLike && !gateway.getTrackedState().isAsciiMode) {
            val composing = gateway.isComposing()
            if (composing) {
                AutoSwitchIMELogger.debug("${event.source}: IME is composing, skipping switch")
                updateCaretWhenCurrent(event, gateway.getTrackedState())
                return
            }
        }

        event.context?.let {
            AutoSwitchIMELogger.info("Insert context: before='${it.before}', after='${it.after}'")
        }

        val isCurrent = { isCurrent(event) }
        if (event.action != ImeAction.UNCHANGED) {
            runBlocking {
                when (event.action) {
                    ImeAction.CHINESE -> gateway.setAsciiMode(false, isCurrent)
                    ImeAction.CAPS -> gateway.setCapsMode(isCurrent)
                    ImeAction.ENGLISH -> gateway.setAsciiMode(true, isCurrent, event.strictNormal)
                    ImeAction.UNCHANGED -> Unit
                }
            }
        }

        if (!isCurrent()) return
        if (event.normalLike) {
            normalLikeDefaultsApplied[event.editor] = true
        }
        updateCaretWhenCurrent(event, gateway.getTrackedState())
    }

    private fun updateCaretWhenCurrent(event: CoordinatorEvent.EditorContext, state: ImeState) {
        ApplicationManager.getApplication().invokeLater {
            if (isCurrent(event)) {
                CaretColorManager.updateCaretColor(event.editor, state)
            }
        }
    }

    private fun isCurrent(event: CoordinatorEvent.EditorContext): Boolean {
        return coordinatorState.isCurrent(event.request, isPlatformEditorFocused(event.editor))
    }

    private fun isPlatformEditorFocused(editor: Editor): Boolean {
        if (editor.isDisposed || !editor.contentComponent.hasFocus()) return false
        val foregroundProcessId = NativeImeSys.imeForegroundProcessId()
        return foregroundProcessId != 0L && foregroundProcessId == ProcessHandle.current().pid()
    }

    private fun handleRefreshPhysicalState(editor: Editor) {
        if (!isPlatformEditorFocused(editor)) return
        gateway.refreshState()
    }

    private fun handleFocusLost(event: CoordinatorEvent.FocusLost) {
        AutoSwitchIMELogger.debug("Editor focus lost, releasing owned CapsLock: ${event.editor.hashCode()}")
        runBlocking { gateway.releaseOwnedCapsLock() }
    }

    private fun handlePhysicalStateChanged(state: ImeState) {
        ActionDeduplicator.invalidate()
        ApplicationManager.getApplication().invokeLater {
            if (disposed.get()) return@invokeLater
            val focusedEditor = EditorFactory.getInstance().allEditors.firstOrNull {
                !it.isDisposed && it.contentComponent.hasFocus()
            } ?: return@invokeLater
            CaretColorManager.updateCaretColor(focusedEditor, state)
            if (NormalModePolicy.shouldEnforceEnglish(
                    strictNormalEditors[focusedEditor] == true,
                    state.isAsciiMode,
                    state.isCapsLock
                )) {
                requestEditorUpdate(focusedEditor, "PhysicalStateChanged")
            }
        }
    }

    private fun handleShutdown() {
        if (gatewayDelegate.isInitialized()) {
            runBlocking { gateway.releaseOwnedCapsLock() }
        }
    }

    override fun dispose() {
        if (!disposed.compareAndSet(false, true)) return

        physicalStatePollTimer.stop()
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyEventDispatcher)
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
            if (gatewayDelegate.isInitialized()) {
                gateway.dispose()
            }
        }
    }

    private sealed interface CoordinatorEvent {
        data class EditorContext(
            val editor: Editor,
            val request: CoordinatorRequest<Editor>,
            val action: ImeAction,
            val normalLike: Boolean,
            val strictNormal: Boolean,
            val context: InsertModeDecision.Context?,
            val source: String
        ) : CoordinatorEvent

        data class FocusLost(val editor: Editor) : CoordinatorEvent
        data class PhysicalStateChanged(val state: ImeState) : CoordinatorEvent
        data class RefreshPhysicalState(val editor: Editor) : CoordinatorEvent
        data object Shutdown : CoordinatorEvent
    }

    companion object {
        fun getInstance(): AutoSwitchIMEController = service()
    }
}
