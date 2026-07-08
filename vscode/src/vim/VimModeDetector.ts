/**
 * VSCodeVim 模式检测器
 * 通过 vscodevim 扩展的 API 获取当前 Vim 模式
 */

import * as vscode from 'vscode';
import { VimMode } from '../core/types';

/** VSCodeVim 对外暴露的模式字符串 */
type VimModeString =
  | 'Normal'
  | 'Visual'
  | 'V-Line'
  | 'V-Block'
  | 'Insert'
  | 'Replace'
  | 'Command';

/**
 * 将 vscodevim 的模式字符串映射为 VimMode 枚举
 */
function mapMode(mode: VimModeString): VimMode {
  switch (mode) {
    case 'Normal':
      return VimMode.NORMAL;
    case 'Visual':
      return VimMode.VISUAL;
    case 'V-Line':
      return VimMode.VISUAL_LINE;
    case 'V-Block':
      return VimMode.VISUAL_BLOCK;
    case 'Insert':
      return VimMode.INSERT;
    case 'Replace':
      return VimMode.REPLACE;
    case 'Command':
      return VimMode.COMMAND;
    default:
      return VimMode.UNKNOWN;
  }
}

/**
 * 判断是否为可视化模式
 */
export function isVisualMode(mode: VimMode): boolean {
  return (
    mode === VimMode.VISUAL ||
    mode === VimMode.VISUAL_LINE ||
    mode === VimMode.VISUAL_BLOCK
  );
}

/**
 * 判断模式是否需要 IME 操作
 * Normal/Visual/Select 模式强制英文
 * Insert/Replace 模式按规则切换
 */
/**
 * 判断模式是否需要 IME 操作
 * 当 vscodevim 未安装时 mode 为 UNKNOWN，视为可编辑模式（始终使用规则评估）
 */
export function isEditableMode(mode: VimMode): boolean {
  return mode !== VimMode.NORMAL &&
    mode !== VimMode.VISUAL &&
    mode !== VimMode.VISUAL_LINE &&
    mode !== VimMode.VISUAL_BLOCK &&
    mode !== VimMode.COMMAND;
}

/**
 * Vim 模式检测器
 * 通过 vscodevim 的 StatusBarItem 文本解析当前模式
 *
 * 注意：vscodevim 没有公开的模式变化 API，
 * 通过监听其状态栏文本变化来推断模式。
 */
export class VimModeDetector implements vscode.Disposable {
  private _currentMode: VimMode = VimMode.UNKNOWN;
  private disposables: vscode.Disposable[] = [];
  private watcher: ReturnType<typeof setInterval> | null = null;

  /** 模式变化回调 */
  onModeChanged?: (mode: VimMode) => void;

  get currentMode(): VimMode {
    return this._currentMode;
  }

  constructor() {
    // 定期检查 vscodevim 是否已激活
    this.watcher = setInterval(() => {
      this.detectMode();
    }, 200);
  }

  private detectMode(): void {
    const vimExt = vscode.extensions.getExtension('vscodevim.vim');
    if (!vimExt?.isActive) {
      return;
    }

    // 通过 vscodevim API 获取当前模式
    const vimApi = vimExt.exports;
    if (!vimApi) return;

    try {
      // vscodevim exposes getMode() through its API
      const modeObj =
        typeof vimApi.getMode === 'function' ? vimApi.getMode() : null;
      if (modeObj?.mode) {
        const newMode = mapMode(modeObj.mode as VimModeString);
        if (newMode !== this._currentMode) {
          this._currentMode = newMode;
          this.onModeChanged?.(newMode);
        }
      }
    } catch {
      // vscodevim API 可能不可用
    }
  }

  dispose(): void {
    if (this.watcher) {
      clearInterval(this.watcher);
      this.watcher = null;
    }
    for (const d of this.disposables) {
      d.dispose();
    }
    this.disposables = [];
  }
}
