import * as vscode from 'vscode';
import { ImeCoordinator } from './ImeCoordinator';
import { ImeGateway } from './ime/ImeGateway';
import { ImeProviderRegistry } from './ime/input/ImeProviderRegistry';
import { RimeImeProvider } from './ime/input/RimeImeProvider';
import { initNative, isNativeAvailable } from './ime/system/native';
import {
  currentSystemType,
  SystemImeProviderRegistry,
  SystemType,
  WindowsSystemImeProvider,
} from './ime/system/SystemImeProvider';
import { ImeType, VSCodeLogger, VimMode } from './core/types';
import { VimModeDetector } from './vim/VimModeDetector';
import { CaretColorManager } from './ui/CaretColor';
import { ImeStatusBar } from './ui/StatusBar';
import {
  getSettings,
  onSettingsChanged,
  PluginSettings,
  restoreDefaultSettings,
} from './settings';

let outputChannel: vscode.OutputChannel;
let logger: VSCodeLogger;
let imeGateway: ImeGateway;
let modeDetector: VimModeDetector;
let caretColor: CaretColorManager;
let statusBar: ImeStatusBar | null = null;
let coordinator: ImeCoordinator | null = null;
let settings: PluginSettings;
let disposables: vscode.Disposable[] = [];
let caretDebounceTimer: ReturnType<typeof setTimeout> | null = null;
let physicalStatePollTimer: ReturnType<typeof setInterval> | null = null;
let stateSourceWarningVisible = false;

export function activate(context: vscode.ExtensionContext): void {
  outputChannel = vscode.window.createOutputChannel('Auto Switch IME');
  logger = new VSCodeLogger(outputChannel);
  logger.info('AutoSwitchIME VSCode extension starting...');

  context.subscriptions.push(
    vscode.commands.registerCommand('autoSwitchIME.restoreDefaults', async () => {
      await restoreDefaultSettings();
      const reload = await vscode.window.showInformationMessage(
        'Auto Switch IME 已恢复默认设置，重新加载窗口后全部生效。',
        '重新加载窗口',
      );
      if (reload === '重新加载窗口') {
        await vscode.commands.executeCommand('workbench.action.reloadWindow');
      }
    }),
  );

  settings = getSettings();
  if (!settings.enabled) {
    logger.info('AutoSwitchIME is disabled in settings');
    return;
  }

  initNative(context.extensionPath);
  logger.info(`Native DLL: ${isNativeAvailable() ? 'loaded' : 'unavailable'}`);

  const providers = new ImeProviderRegistry();
  providers.register(ImeType.RIME, (config) => new RimeImeProvider(
      logger,
      config.weaselServerPath,
      (msg) => vscode.window.showWarningMessage(msg),
    ));
  const provider = providers.create(settings.imeConfig);
  const systems = new SystemImeProviderRegistry();
  systems.register(SystemType.WINDOWS, () => new WindowsSystemImeProvider());
  const system = systems.create(currentSystemType());
  imeGateway = new ImeGateway(provider, system, logger);

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
    imeGateway,
    modeDetector,
    caretColor,
    () => statusBar,
    () => settings,
    logger,
  );
  imeGateway.onStateChanged = (state) => coordinator?.onPhysicalStateChanged(state);
  imeGateway.onStateSourceAvailabilityChanged = onStateSourceAvailabilityChanged;
  imeGateway.onStateChangeSignal = () => coordinator?.pollPhysicalState();
  imeGateway.start();

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

  if (!imeGateway.supportsStateChangeNotifications()) {
    physicalStatePollTimer = setInterval(() => coordinator?.pollPhysicalState(), 200);
  }
  logger.info('AutoSwitchIME VSCode extension activated');
}

export async function deactivate(): Promise<void> {
  await coordinator?.shutdown();

  if (caretDebounceTimer) {
    clearTimeout(caretDebounceTimer);
    caretDebounceTimer = null;
  }
  if (physicalStatePollTimer) {
    clearInterval(physicalStatePollTimer);
    physicalStatePollTimer = null;
  }

  for (const disposable of disposables) {
    disposable.dispose();
  }
  disposables = [];

  modeDetector?.dispose();
  await caretColor?.dispose();
  statusBar?.dispose();
  imeGateway?.dispose();
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

function onStateSourceAvailabilityChanged(available: boolean): void {
  if (available) {
    stateSourceWarningVisible = false;
    return;
  }
  if (stateSourceWarningVisible) return;
  stateSourceWarningVisible = true;
  void vscode.window.showWarningMessage(
    'AutoSwitchIME 已暂停：当前输入法状态源不可用。请部署或重新部署对应的输入法状态组件。',
  );
}
