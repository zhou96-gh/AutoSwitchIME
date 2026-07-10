import * as vscode from 'vscode';
import { CoordinatorRequest, CoordinatorState } from './core/CoordinatorState';
import { EventMailbox } from './core/EventMailbox';
import { evaluateRules } from './core/RuleEvaluator';
import { ImeAction, ImeState, Logger, VimMode } from './core/types';
import { RimeImeProvider } from './providers/RimeImeProvider';
import { PluginSettings } from './settings';
import { CaretColorManager } from './ui/CaretColor';
import { ImeStatusBar } from './ui/StatusBar';
import { isNormalLikeMode, VimModeDetector } from './vim/VimModeDetector';

type EditorRequest = CoordinatorRequest<vscode.TextEditor>;

type CoordinatorEvent =
  | {
      kind: 'editor-context';
      editor: vscode.TextEditor;
      request: EditorRequest;
      action: ImeAction;
      normalLike: boolean;
      vimMode: VimMode;
      before: string;
      after: string;
    }
  | {
      kind: 'initialize-editor';
      editor: vscode.TextEditor;
      request: EditorRequest;
      state: ImeState;
    }
  | { kind: 'physical-state'; state: ImeState }
  | { kind: 'caps-changed'; capsLock: boolean }
  | { kind: 'focus-lost' }
  | { kind: 'shutdown' };

export class ImeCoordinator {
  private readonly state = new CoordinatorState<vscode.TextEditor>();
  private readonly mailbox: EventMailbox<CoordinatorEvent>;
  private lastCapsState: boolean;
  private lastActionKey: string | null = null;
  private lastUpdateTime = 0;
  private throttleRetryTimer: ReturnType<typeof setTimeout> | null = null;
  private throttledEditor: vscode.TextEditor | null = null;
  private programmaticCapsChange = false;
  private programmaticCapsChangeTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly provider: RimeImeProvider,
    private readonly modeDetector: VimModeDetector,
    private readonly caretColor: CaretColorManager,
    private readonly getStatusBar: () => ImeStatusBar | null,
    private readonly getSettings: () => PluginSettings,
    private readonly logger: Logger,
  ) {
    this.lastCapsState = provider.getTrackedState().isCapsLock;
    this.mailbox = new EventMailbox(
      (event) => this.handleEvent(event),
      (error) => this.logger.warn('IME coordinator event failed', error),
    );
  }

  requestEditorUpdate(editor: vscode.TextEditor): void {
    if (!this.isEditorActive(editor)) return;

    const now = Date.now();
    if (now - this.lastUpdateTime < 50) {
      this.state.invalidateRequests();
      this.throttledEditor = editor;
      if (!this.throttleRetryTimer) {
        this.throttleRetryTimer = setTimeout(() => {
          this.throttleRetryTimer = null;
          const pendingEditor = this.throttledEditor;
          this.throttledEditor = null;
          if (pendingEditor) this.requestEditorUpdate(pendingEditor);
        }, 50 - (now - this.lastUpdateTime));
      }
      return;
    }
    this.lastUpdateTime = now;

    const settings = this.getSettings();
    this.state.setEnabled(settings.enabled);
    if (!settings.enabled) return;

    this.state.focusEditor(editor);
    const vimMode = this.modeDetector.currentMode;
    const normalLike = isNormalLikeMode(vimMode, hasSelection(editor));
    const { before, after } = getLineContextText(editor, this.logger);
    const action = normalLike
      ? ImeAction.ENGLISH
      : evaluateRules(before, after, settings.rules);

    if (this.shouldSkipAction(editor, action) && !normalLike) {
      this.logger.debug(`Duplicated ${action} action skipped`);
      return;
    }

    const request = this.state.newRequest(editor);
    if (!request) return;

    this.mailbox.post(
      {
        kind: 'editor-context',
        editor,
        request,
        action,
        normalLike,
        vimMode,
        before,
        after,
      },
      isPendingEditorEvent,
    );
  }

  initializeEditor(editor: vscode.TextEditor): void {
    if (!this.isEditorActive(editor)) return;

    this.state.focusEditor(editor);
    const normalLike = isNormalLikeMode(
      this.modeDetector.currentMode,
      hasSelection(editor),
    );
    if (normalLike) {
      this.requestEditorUpdate(editor);
      return;
    }

    const request = this.state.newRequest(editor);
    if (!request) return;
    this.mailbox.post(
      {
        kind: 'initialize-editor',
        editor,
        request,
        state: this.provider.getTrackedState(),
      },
      isPendingEditorEvent,
    );
  }

  onWindowFocusChanged(focused: boolean): void {
    if (focused) {
      const editor = vscode.window.activeTextEditor;
      if (editor) this.state.focusEditor(editor);
      return;
    }

    this.state.loseFocus();
    this.mailbox.clear(isPendingEditorEvent);
    this.mailbox.post({ kind: 'focus-lost' });
  }

  onPhysicalStateChanged(state: ImeState): void {
    this.mailbox.post(
      { kind: 'physical-state', state },
      (pending) => pending.kind === 'physical-state',
    );
  }

  pollCapsLock(): void {
    if (!this.getSettings().enabled || !vscode.window.state.focused) return;
    const current = this.provider.getTrackedState().isCapsLock;
    if (current === this.lastCapsState) return;
    this.lastCapsState = current;
    this.mailbox.post(
      { kind: 'caps-changed', capsLock: current },
      (pending) => pending.kind === 'caps-changed',
    );
  }

  setEnabled(enabled: boolean): void {
    this.state.setEnabled(enabled);
    if (!enabled) {
      this.mailbox.clear(isPendingEditorEvent);
      this.mailbox.post({ kind: 'focus-lost' });
    }
  }

  async shutdown(): Promise<void> {
    if (this.state.isShuttingDown()) {
      await this.mailbox.waitForIdle();
      return;
    }

    this.state.shutdown();
    this.mailbox.clear(() => true);
    this.mailbox.post({ kind: 'shutdown' });
    await this.mailbox.waitForIdle();
    this.clearTimers();
  }

  private async handleEvent(event: CoordinatorEvent): Promise<void> {
    switch (event.kind) {
      case 'editor-context':
        await this.handleEditorContext(event);
        break;
      case 'initialize-editor':
        if (this.isCurrent(event.editor, event.request)) {
          this.applyColorAndStatus(actionFromState(event.state), event.state);
        }
        break;
      case 'physical-state':
        this.handlePhysicalState(event.state);
        break;
      case 'caps-changed':
        await this.handleCapsChanged(event.capsLock);
        break;
      case 'focus-lost':
        await this.releaseOwnedCapsLock();
        break;
      case 'shutdown':
        await this.releaseOwnedCapsLock();
        break;
    }
  }

  private async handleEditorContext(
    event: Extract<CoordinatorEvent, { kind: 'editor-context' }>,
  ): Promise<void> {
    if (!this.isCurrent(event.editor, event.request)) return;

    const modeBefore = this.provider.currentAsciiMode;
    if (!event.normalLike && !modeBefore) {
      const composing = await this.provider.isComposing();
      if (!this.isCurrent(event.editor, event.request)) return;
      if (composing) {
        this.logger.debug('Rime is composing, skipping IME switch');
        this.applyColorAndStatus(actionFromState(this.provider.getTrackedState()), this.provider.getTrackedState());
        return;
      }
    }

    if (event.normalLike) {
      this.logger.debug(`Normal-like Vim mode: ${event.vimMode}, forcing ASCII`);
    } else {
      this.logger.debug(
        `Insert mode: before="${event.before}", after="${event.after}", action=${event.action}`,
      );
    }

    const isCurrent = () => this.isCurrent(event.editor, event.request);
    switch (event.action) {
      case ImeAction.CHINESE:
        this.markProgrammaticCapsChangeIfNeeded(false);
        await this.provider.setAsciiMode(false, isCurrent);
        break;
      case ImeAction.CAPS:
        this.markProgrammaticCapsChangeIfNeeded(true);
        await this.provider.setCapsMode(isCurrent);
        break;
      case ImeAction.ENGLISH:
        this.markProgrammaticCapsChangeIfNeeded(false);
        await this.provider.setAsciiMode(true, isCurrent);
        break;
      case ImeAction.UNCHANGED:
        return;
    }

    if (!isCurrent()) return;
    const state = stateFromAction(event.action);
    this.applyColorAndStatus(event.action, state, event.normalLike);
  }

  private handlePhysicalState(state: ImeState): void {
    this.lastActionKey = null;
    if (!this.getSettings().enabled || !vscode.window.state.focused) return;

    const editor = vscode.window.activeTextEditor;
    if (!editor) return;
    if (isNormalLikeMode(this.modeDetector.currentMode, hasSelection(editor))) {
      if (!state.isAsciiMode || state.isCapsLock) {
        this.requestEditorUpdate(editor);
      } else {
        this.applyColorAndStatus(ImeAction.ENGLISH, state, true);
      }
      return;
    }

    this.applyColorAndStatus(actionFromState(state), state);
  }

  private async handleCapsChanged(current: boolean): Promise<void> {
    if (!this.getSettings().enabled || !vscode.window.state.focused) return;
    const editor = vscode.window.activeTextEditor;
    if (!editor) return;

    if (isNormalLikeMode(this.modeDetector.currentMode, hasSelection(editor))) {
      this.requestEditorUpdate(editor);
      return;
    }

    if (this.programmaticCapsChange) {
      this.clearProgrammaticCapsChange();
      const state = this.provider.getTrackedState();
      this.applyColorAndStatus(actionFromState(state), state);
      return;
    }

    if (current) {
      this.state.focusEditor(editor);
      const request = this.state.newRequest(editor);
      if (!request) return;
      const isCurrent = () => this.isCurrent(editor, request);
      if (!this.provider.currentAsciiMode) {
        await this.provider.ensureAsciiMode(isCurrent);
      }
      if (!isCurrent()) return;
      this.applyColorAndStatus(
        ImeAction.CAPS,
        { isAsciiMode: true, isCapsLock: true, isComposing: false },
      );
      return;
    }

    const asciiMode = this.provider.currentAsciiMode;
    this.applyColorAndStatus(
      asciiMode ? ImeAction.ENGLISH : ImeAction.CHINESE,
      { isAsciiMode: asciiMode, isCapsLock: false, isComposing: false },
    );
  }

  private isCurrent(
    editor: vscode.TextEditor,
    request: EditorRequest,
  ): boolean {
    return (
      this.state.isCurrent(request) &&
      this.isEditorActive(editor)
    );
  }

  private isEditorActive(editor: vscode.TextEditor): boolean {
    return (
      !this.state.isShuttingDown() &&
      this.getSettings().enabled &&
      vscode.window.state.focused &&
      vscode.window.activeTextEditor === editor
    );
  }

  private shouldSkipAction(
    editor: vscode.TextEditor,
    action: ImeAction,
  ): boolean {
    const key = [
      editor.document.uri.toString(),
      editor.document.version,
      editor.selection.active.line,
      editor.selection.active.character,
      action,
    ].join('|');
    const skip = key === this.lastActionKey;
    this.lastActionKey = key;
    return skip;
  }

  private applyColorAndStatus(
    action: ImeAction,
    state?: ImeState,
    forceColor = false,
  ): void {
    const actualCapsLock = this.provider.getTrackedState().isCapsLock;
    const effectiveAction = actualCapsLock ? ImeAction.CAPS : action;
    this.caretColor.updateCaretColor(effectiveAction, forceColor);
    if (state) {
      this.getStatusBar()?.updateImeState(
        { ...state, isCapsLock: actualCapsLock },
        effectiveAction,
      );
    }
  }

  private markProgrammaticCapsChangeIfNeeded(targetCapsLock: boolean): void {
    if (this.provider.getTrackedState().isCapsLock === targetCapsLock) return;
    this.clearProgrammaticCapsChange();
    this.programmaticCapsChange = true;
    this.programmaticCapsChangeTimer = setTimeout(() => {
      this.programmaticCapsChange = false;
      this.programmaticCapsChangeTimer = null;
    }, 1000);
  }

  private clearProgrammaticCapsChange(): void {
    this.programmaticCapsChange = false;
    if (this.programmaticCapsChangeTimer) {
      clearTimeout(this.programmaticCapsChangeTimer);
      this.programmaticCapsChangeTimer = null;
    }
  }

  private async releaseOwnedCapsLock(): Promise<void> {
    await this.provider.releaseOwnedCapsLock();
    this.lastCapsState = this.provider.getTrackedState().isCapsLock;
    this.clearProgrammaticCapsChange();
  }

  private clearTimers(): void {
    if (this.throttleRetryTimer) {
      clearTimeout(this.throttleRetryTimer);
      this.throttleRetryTimer = null;
    }
    this.throttledEditor = null;
    this.clearProgrammaticCapsChange();
  }
}

function isPendingEditorEvent(event: CoordinatorEvent): boolean {
  return event.kind === 'editor-context' || event.kind === 'initialize-editor';
}

function hasSelection(editor: vscode.TextEditor | undefined): boolean {
  return !!editor && !editor.selection.isEmpty;
}

function actionFromState(state: ImeState): ImeAction {
  if (state.isCapsLock) return ImeAction.CAPS;
  return state.isAsciiMode ? ImeAction.ENGLISH : ImeAction.CHINESE;
}

function stateFromAction(action: ImeAction): ImeState {
  switch (action) {
    case ImeAction.CHINESE:
      return { isAsciiMode: false, isCapsLock: false, isComposing: false };
    case ImeAction.CAPS:
      return { isAsciiMode: true, isCapsLock: true, isComposing: false };
    case ImeAction.ENGLISH:
      return { isAsciiMode: true, isCapsLock: false, isComposing: false };
    case ImeAction.UNCHANGED:
      return { isAsciiMode: true, isCapsLock: false, isComposing: false };
  }
}

function getLineContextText(
  editor: vscode.TextEditor,
  logger: Logger,
): { before: string; after: string } {
  try {
    const document = editor.document;
    const position = editor.selection.active;
    const line = document.lineAt(position.line);
    const beforeStart = Math.max(line.range.start.character, position.character - 20);
    const afterEnd = Math.min(line.range.end.character, position.character + 20);
    const before = document.getText(
      new vscode.Range(position.line, beforeStart, position.line, position.character),
    );
    const after = document.getText(
      new vscode.Range(position.line, position.character, position.line, afterEnd),
    );
    return { before, after };
  } catch (error) {
    logger.warn('Failed to get line context text', error as Error);
    return { before: '', after: '' };
  }
}
