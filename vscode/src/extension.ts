import * as vscode from 'vscode';
import { VSCodeLogger, VimMode, ImeAction, ImeState } from './core/types';
import { evaluateRules } from './core/RuleEvaluator';
import { RimeImeProvider } from './providers/RimeImeProvider';
import { VimModeDetector, isNormalLikeMode } from './vim/VimModeDetector';
import { CaretColorManager } from './ui/CaretColor';
import { ImeStatusBar } from './ui/StatusBar';
import { getSettings, onSettingsChanged, PluginSettings } from './settings';

let outputChannel: vscode.OutputChannel;
let logger: VSCodeLogger;
let provider: RimeImeProvider;
let modeDetector: VimModeDetector;
let caretColor: CaretColorManager;
let statusBar: ImeStatusBar | null = null;
let settings: PluginSettings;
let disposables: vscode.Disposable[] = [];
let caretDebounceTimer: ReturnType<typeof setTimeout> | null = null;
let capsPollTimer: ReturnType<typeof setInterval> | null = null;
let lastCapsState = false;
let isActive = false;
let updateLock = false;
let updatePending = false;
let throttleRetryTimer: ReturnType<typeof setTimeout> | null = null;
let lastUpdateTime = 0;
let lastActionKey: string | null = null;
/** 标志当前 CapsLock 变化是否由插件自身触发（避免轮询误响应） */
let programmaticCapsChange = false;

export function activate(context: vscode.ExtensionContext): void {
  outputChannel = vscode.window.createOutputChannel('Auto Switch IME');
  logger = new VSCodeLogger(outputChannel);
  logger.info('AutoSwitchIME VSCode extension starting...');

  settings = getSettings();

  if (!settings.enabled) {
    logger.info('AutoSwitchIME is disabled in settings');
    return;
  }

  // 创建 IME Provider
  provider = new RimeImeProvider(
    logger,
    settings.imeConfig.weaselServerPath,
    (msg) => vscode.window.showWarningMessage(msg),
    context.extensionPath,
  );
  provider.start();

  // 创建光标颜色管理器
  caretColor = new CaretColorManager(
    settings.chineseCaretColor,
    settings.englishCaretColor,
    settings.capsCaretColor,
  );

  // 创建状态栏指示器
  if (settings.showStatusBar) {
    statusBar = new ImeStatusBar();
  }

  // 创建 Vim 模式检测器
  modeDetector = new VimModeDetector();
  modeDetector.onModeChanged = onVimModeChanged;

  // 监听编辑器事件
  setupEditorListeners();

  // 监听配置变化
  disposables.push(
    onSettingsChanged((newSettings) => {
      settings = newSettings;
      if (statusBar && !settings.showStatusBar) {
        statusBar.dispose();
        statusBar = null;
      } else if (!statusBar && settings.showStatusBar) {
        statusBar = new ImeStatusBar();
      }
    }),
  );

  // 注册清理
  context.subscriptions.push({ dispose: deactivate });

  provider.onStateChanged = (state: ImeState) => {
    if (!isActive || !settings.enabled) return;
    invalidateLastAction();
    if (isNormalLikeMode(modeDetector.currentMode)) {
      if (!vscode.window.state.focused) return;
      const editor = vscode.window.activeTextEditor;
      if (editor) {
        updateEditorState(editor).catch((err) =>
          logger.warn('updateEditorState failed', err),
        );
      }
      return;
    }

    const action = state.isCapsLock
      ? ImeAction.CAPS
      : state.isAsciiMode ? ImeAction.ENGLISH : ImeAction.CHINESE;
    applyColorAndStatus(action, state);
  };

  isActive = true;

  const editor = vscode.window.activeTextEditor;
  if (editor) {
    initializeEditor(editor);
  }

  lastCapsState = provider.getTrackedState().isCapsLock;
  capsPollTimer = setInterval(() => {
    if (!isActive || !settings.enabled) return;
    const current = provider.getTrackedState().isCapsLock;
    if (current !== lastCapsState) {
      lastCapsState = current;

      if (isNormalLikeMode(modeDetector.currentMode)) {
        if (!vscode.window.state.focused) return;
        const editor = vscode.window.activeTextEditor;
        if (editor) {
          updateEditorState(editor).catch((err) =>
            logger.warn('updateEditorState failed', err),
          );
        }
        return;
      }

      // 跳过插件自身触发的切换，避免冗余调用
      if (programmaticCapsChange) {
        programmaticCapsChange = false;
        const state = provider.getTrackedState();
        const action = current ? ImeAction.CAPS : (state.isAsciiMode ? ImeAction.ENGLISH : ImeAction.CHINESE);
        applyColorAndStatus(action, state);
        return;
      }

      // 用户手动按 CapsLock：确保 IME 模式与 CapsLock 一致
      if (current) {
        // CapsLock 开启 → 必须英文模式才能输出大写字母
        // 注意：用 ensureAsciiMode（不关 CapsLock），不用 setAsciiMode（会关掉 CapsLock）
        if (!provider.currentAsciiMode) {
          provider.ensureAsciiMode().catch(() => {});
        }
        applyColorAndStatus(ImeAction.CAPS, { isAsciiMode: true, isCapsLock: true, isComposing: false });
      } else {
        // CapsLock 关闭 → 恢复上下文决定的模式（下个光标事件触发）
        const modeBefore = provider.currentAsciiMode;
        applyColorAndStatus(
          modeBefore ? ImeAction.ENGLISH : ImeAction.CHINESE,
          { isAsciiMode: modeBefore, isCapsLock: false, isComposing: false },
        );
      }
    }
  }, 200);
  // 还原初始标记（activate 期间没有程序化切换）
  programmaticCapsChange = false;

  logger.info('AutoSwitchIME VSCode extension activated');
}

export async function deactivate(): Promise<void> {
  isActive = false;

  if (caretDebounceTimer) {
    clearTimeout(caretDebounceTimer);
    caretDebounceTimer = null;
  }

  if (throttleRetryTimer) {
    clearTimeout(throttleRetryTimer);
    throttleRetryTimer = null;
  }

  if (capsPollTimer) {
    clearInterval(capsPollTimer);
    capsPollTimer = null;
  }

  for (const d of disposables) {
    d.dispose();
  }
  disposables = [];

  modeDetector?.dispose();
  await caretColor?.dispose();
  statusBar?.dispose();
  provider?.dispose();

  logger?.info('AutoSwitchIME VSCode extension deactivated');
}

function setupEditorListeners(): void {
  // 监听光标位置变化（防抖 50ms）
  disposables.push(
    vscode.window.onDidChangeTextEditorSelection((e) => {
      if (!settings.enabled) return;
      if (caretDebounceTimer) clearTimeout(caretDebounceTimer);
      caretDebounceTimer = setTimeout(() => {
        updateEditorState(e.textEditor).catch((err) =>
          logger.warn('updateEditorState failed', err),
        );
      }, settings.caretDebounceMs);
    }),
  );

  // 监听文档变化
  disposables.push(
    vscode.workspace.onDidChangeTextDocument((e) => {
      if (!settings.enabled) return;
      const editor = vscode.window.activeTextEditor;
      if (editor && editor.document === e.document) {
        updateEditorState(editor).catch((err) =>
          logger.warn('updateEditorState failed', err),
        );
      }
    }),
  );

  // 监听活动编辑器切换
  disposables.push(
    vscode.window.onDidChangeActiveTextEditor((editor) => {
      if (editor && settings.enabled) {
        initializeEditor(editor).catch((err) =>
          logger.warn('initializeEditor failed', err),
        );
      }
    }),
  );
}

async function onVimModeChanged(mode: VimMode): Promise<void> {
  statusBar?.updateVimMode(mode);
  const editor = vscode.window.activeTextEditor;
  if (editor && settings.enabled) {
    await updateEditorState(editor);
  }
}

async function initializeEditor(editor: vscode.TextEditor): Promise<void> {
  const tracked = provider.getTrackedState();
  const normalLikeMode = isNormalLikeMode(modeDetector.currentMode);
  if (normalLikeMode) {
    await updateEditorState(editor);
    return;
  }

  const action = tracked.isCapsLock
    ? ImeAction.CAPS
    : tracked.isAsciiMode ? ImeAction.ENGLISH : ImeAction.CHINESE;
  await applyColorAndStatus(action, tracked);
}

function applyColorAndStatus(action: ImeAction, state?: ImeState, forceColor = false): void {
  caretColor.updateCaretColor(action, forceColor);
  if (state) {
    statusBar?.updateImeState(state, action);
  }
}

async function updateEditorState(editor: vscode.TextEditor): Promise<void> {
  if (!settings.enabled || !isActive) return;
  const now = Date.now();
  if (now - lastUpdateTime < 50) {
    updatePending = true;
    if (!throttleRetryTimer) {
      throttleRetryTimer = setTimeout(() => {
        throttleRetryTimer = null;
        updateEditorState(editor).catch((err) =>
          logger.warn('updateEditorState failed', err),
        );
      }, 50 - (now - lastUpdateTime));
    }
    return;
  }
  lastUpdateTime = now;
  if (updateLock) {
    updatePending = true;
    return;
  }
  updateLock = true;

  try {
    do {
      updatePending = false;

      if (vscode.window.activeTextEditor !== editor) {
        return;
      }

      const vimMode = modeDetector.currentMode;
      const normalLikeMode = isNormalLikeMode(vimMode);
      const modeBefore = provider.currentAsciiMode;
      if (!normalLikeMode && !modeBefore) {
        const isComposing = await provider.isComposing();
        if (isComposing) {
          logger.debug('Rime is composing, skipping IME switch');
          return;
        }
      }

      if (normalLikeMode) {
        if (shouldSkipAction(editor, ImeAction.ENGLISH)) {
          logger.debug('Duplicated English action skipped');
        } else {
          logger.debug(`Normal-like Vim mode: ${vimMode}, forcing ASCII`);
        }
        await provider.setAsciiMode(true);
        applyColorAndStatus(ImeAction.ENGLISH, { isAsciiMode: true, isCapsLock: false, isComposing: false }, true);
      } else {
        const { before, after } = getLineContextText(editor);
        const action = evaluateRules(before, after, settings.rules);

        logger.debug(`Insert mode: before="${before}", after="${after}", action=${action}`);
        if (shouldSkipAction(editor, action)) {
          logger.debug(`Duplicated ${action} action skipped`);
          continue;
        }

        // 标记所有 provider 调用为程序化操作，避免轮询重复响应
        programmaticCapsChange = true;

        switch (action) {
          case ImeAction.CHINESE:
            logger.info('Insert mode: Chinese');
            await provider.setAsciiMode(false);
            applyColorAndStatus(ImeAction.CHINESE, { isAsciiMode: false, isCapsLock: false, isComposing: false });
            break;
          case ImeAction.CAPS:
            logger.info('Insert mode: Caps');
            await provider.setCapsMode();
            applyColorAndStatus(ImeAction.CAPS, { isAsciiMode: true, isCapsLock: true, isComposing: false });
            break;
          case ImeAction.ENGLISH:
            logger.info('Insert mode: English');
            await provider.setAsciiMode(true);
            applyColorAndStatus(ImeAction.ENGLISH, { isAsciiMode: true, isCapsLock: false, isComposing: false });
            break;
        }
      }
    } while (updatePending);
  } finally {
    updateLock = false;
  }
}

function shouldSkipAction(editor: vscode.TextEditor, action: ImeAction): boolean {
  const key = [
    editor.document.uri.toString(),
    editor.document.version,
    editor.selection.active.line,
    editor.selection.active.character,
    action,
  ].join('|');
  const skip = key === lastActionKey;
  lastActionKey = key;
  return skip;
}

function invalidateLastAction(): void {
  lastActionKey = null;
}

function getLineContextText(editor: vscode.TextEditor): {
  before: string;
  after: string;
} {
  try {
    const document = editor.document;
    const position = editor.selection.active;

    const line = document.lineAt(position.line);
    const lineStart = line.range.start;
    const lineEnd = line.range.end;

    const beforeStart = Math.max(lineStart.character, position.character - 20);
    const beforeRange = new vscode.Range(
      position.line,
      beforeStart,
      position.line,
      position.character,
    );
    const before = document.getText(beforeRange);

    const afterEnd = Math.min(lineEnd.character, position.character + 20);
    const afterRange = new vscode.Range(
      position.line,
      position.character,
      position.line,
      afterEnd,
    );
    const after = document.getText(afterRange);

    return { before, after };
  } catch (e) {
    logger.warn('Failed to get line context text', e as Error);
    return { before: '', after: '' };
  }
}
