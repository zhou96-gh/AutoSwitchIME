import * as vscode from 'vscode';
import { ImeCoordinator } from './ImeCoordinator';
import { VSCodeLogger, VimMode } from './core/types';
import { RimeImeProvider } from './providers/RimeImeProvider';
import { VimModeDetector } from './vim/VimModeDetector';
import { CaretColorManager } from './ui/CaretColor';
import { ImeStatusBar } from './ui/StatusBar';
import { getSettings, onSettingsChanged, PluginSettings } from './settings';

let outputChannel: vscode.OutputChannel;
let logger: VSCodeLogger;
let provider: RimeImeProvider;
let modeDetector: VimModeDetector;
let caretColor: CaretColorManager;
let statusBar: ImeStatusBar | null = null;
let coordinator: ImeCoordinator | null = null;
let settings: PluginSettings;
let disposables: vscode.Disposable[] = [];
let caretDebounceTimer: ReturnType<typeof setTimeout> | null = null;
let capsPollTimer: ReturnType<typeof setInterval> | null = null;

export function activate(context: vscode.ExtensionContext): void {
  outputChannel = vscode.window.createOutputChannel('Auto Switch IME');
  logger = new VSCodeLogger(outputChannel);
  logger.info('AutoSwitchIME VSCode extension starting...');

  settings = getSettings();
  if (!settings.enabled) {
    logger.info('AutoSwitchIME is disabled in settings');
    return;
  }

  provider = new RimeImeProvider(
    logger,
    settings.imeConfig.weaselServerPath,
    (msg) => vscode.window.showWarningMessage(msg),
    context.extensionPath,
  );
  provider.start();

  caretColor = new CaretColorManager(
    settings.chineseCaretColor,
    settings.englishCaretColor,
    settings.capsCaretColor,
  );
  if (settings.showStatusBar) {
    statusBar = new ImeStatusBar();
  }

  modeDetector = new VimModeDetector();
  modeDetector.onModeChanged = onVimModeChanged;

  coordinator = new ImeCoordinator(
    provider,
    modeDetector,
    caretColor,
    () => statusBar,
    () => settings,
    logger,
  );
  provider.onStateChanged = (state) => coordinator?.onPhysicalStateChanged(state);

  setupEditorListeners();
  disposables.push(
    onSettingsChanged((newSettings) => {
      settings = newSettings;
      coordinator?.setEnabled(settings.enabled);
      if (statusBar && !settings.showStatusBar) {
        statusBar.dispose();
        statusBar = null;
      } else if (!statusBar && settings.showStatusBar) {
        statusBar = new ImeStatusBar();
      }
    }),
  );
  context.subscriptions.push({ dispose: deactivate });

  const editor = vscode.window.activeTextEditor;
  if (editor) coordinator.initializeEditor(editor);

  capsPollTimer = setInterval(() => coordinator?.pollCapsLock(), 200);
  logger.info('AutoSwitchIME VSCode extension activated');
}

export async function deactivate(): Promise<void> {
  await coordinator?.shutdown();

  if (caretDebounceTimer) {
    clearTimeout(caretDebounceTimer);
    caretDebounceTimer = null;
  }
  if (capsPollTimer) {
    clearInterval(capsPollTimer);
    capsPollTimer = null;
  }

  for (const disposable of disposables) {
    disposable.dispose();
  }
  disposables = [];

  modeDetector?.dispose();
  await caretColor?.dispose();
  statusBar?.dispose();
  provider?.dispose();
  coordinator = null;

  logger?.info('AutoSwitchIME VSCode extension deactivated');
}

function setupEditorListeners(): void {
  disposables.push(
    vscode.window.onDidChangeWindowState((state) => {
      coordinator?.onWindowFocusChanged(state.focused);
    }),
  );

  disposables.push(
    vscode.window.onDidChangeTextEditorSelection((event) => {
      if (!settings.enabled) return;
      if (caretDebounceTimer) clearTimeout(caretDebounceTimer);
      caretDebounceTimer = setTimeout(() => {
        coordinator?.requestEditorUpdate(event.textEditor);
      }, settings.caretDebounceMs);
    }),
  );

  disposables.push(
    vscode.workspace.onDidChangeTextDocument((event) => {
      if (!settings.enabled) return;
      const editor = vscode.window.activeTextEditor;
      if (editor && editor.document === event.document) {
        coordinator?.requestEditorUpdate(editor);
      }
    }),
  );

  disposables.push(
    vscode.window.onDidChangeActiveTextEditor((editor) => {
      if (editor && settings.enabled) {
        coordinator?.initializeEditor(editor);
      }
    }),
  );
}

async function onVimModeChanged(mode: VimMode): Promise<void> {
  statusBar?.updateVimMode(mode);
  const editor = vscode.window.activeTextEditor;
  if (editor && settings.enabled && vscode.window.state.focused) {
    coordinator?.requestEditorUpdate(editor);
  }
}
