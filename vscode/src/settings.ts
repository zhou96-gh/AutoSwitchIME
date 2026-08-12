/**
 * 设置管理器
 * 读取 VSCode contributes.configuration 配置
 */

import * as vscode from 'vscode';
import { ImeConfig, ImeType } from './core/types';
import { RuleSet } from './core/RuleEvaluator';

const CONFIG_SECTION = 'autoSwitchIME';
const CONFIG_KEYS = [
  'enabled',
  'imeType',
  'weaselServerPath',
  'customSwitchScript',
  'chineseBeforeRegex',
  'chineseAfterRegex',
  'capsBeforeRegex',
  'capsAfterRegex',
  'caretDebounceMs',
  'chineseCaretColor',
  'englishCaretColor',
  'capsCaretColor',
  'showStatusBarIndicator',
] as const;

/** 完整插件配置 */
export interface PluginSettings {
  enabled: boolean;
  imeConfig: ImeConfig;
  rules: RuleSet;
  caretDebounceMs: number;
  chineseCaretColor: string;
  englishCaretColor: string;
  capsCaretColor: string;
  showStatusBar: boolean;
}

/**
 * 获取完整插件配置
 */
export function getSettings(): PluginSettings {
  const config = vscode.workspace.getConfiguration(CONFIG_SECTION);

  const imeTypeStr = config.get<string>('imeType', 'rime');

  return {
    enabled: config.get<boolean>('enabled', true),
    imeConfig: {
      type: mapImeType(imeTypeStr),
      weaselServerPath: config.get<string>('weaselServerPath') || undefined,
      customSwitchScript: config.get<string>('customSwitchScript') || undefined,
    },
    rules: {
      chineseBeforeRegex: config.get<string>('chineseBeforeRegex', ''),
      chineseAfterRegex: config.get<string>('chineseAfterRegex', ''),
      capsBeforeRegex: config.get<string>('capsBeforeRegex', ''),
      capsAfterRegex: config.get<string>('capsAfterRegex', ''),
    },
    caretDebounceMs: config.get<number>('caretDebounceMs', 50),
    chineseCaretColor: config.get<string>('chineseCaretColor', '#00FF00'),
    englishCaretColor: config.get<string>('englishCaretColor', '#FFFFFF'),
    capsCaretColor: config.get<string>('capsCaretColor', '#FFFF00'),
    showStatusBar: config.get<boolean>('showStatusBarIndicator', true),
  };
}

function mapImeType(str: string): ImeType {
  switch (str) {
    case 'rime':
      return ImeType.RIME;
    case 'sogou':
      return ImeType.SOGOU;
    case 'ms_pinyin':
      return ImeType.MS_PINYIN;
    case 'custom':
      return ImeType.CUSTOM;
    default:
      return ImeType.RIME;
  }
}

/**
 * 监听配置变化
 */
export function onSettingsChanged(
  callback: (settings: PluginSettings) => void,
): vscode.Disposable {
  return vscode.workspace.onDidChangeConfiguration((e) => {
    if (e.affectsConfiguration(CONFIG_SECTION)) {
      callback(getSettings());
    }
  });
}

/** 清除插件配置在各作用域的覆盖值，使其回落到 package.json 默认值。 */
export async function restoreDefaultSettings(): Promise<void> {
  const config = vscode.workspace.getConfiguration(CONFIG_SECTION);
  for (const key of CONFIG_KEYS) {
    await config.update(key, undefined, vscode.ConfigurationTarget.Global);
  }

  if (vscode.workspace.workspaceFile || vscode.workspace.workspaceFolders?.length) {
    for (const key of CONFIG_KEYS) {
      await config.update(key, undefined, vscode.ConfigurationTarget.Workspace);
    }
  }

  for (const folder of vscode.workspace.workspaceFolders ?? []) {
    const folderConfig = vscode.workspace.getConfiguration(CONFIG_SECTION, folder.uri);
    for (const key of CONFIG_KEYS) {
      await folderConfig.update(key, undefined, vscode.ConfigurationTarget.WorkspaceFolder);
    }
  }
}
