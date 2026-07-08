/**
 * 状态栏 IME 模式指示器
 * 在 VSCode 底部状态栏显示当前 IME 模式
 */

import * as vscode from 'vscode';
import { ImeAction, ImeState } from '../core/types';
import { VimMode } from '../core/types';

export class ImeStatusBar implements vscode.Disposable {
  private imeItem: vscode.StatusBarItem;
  private modeItem: vscode.StatusBarItem;

  constructor() {
    // IME 状态指示（右侧）
    this.imeItem = vscode.window.createStatusBarItem(
      vscode.StatusBarAlignment.Right,
      100,
    );
    this.imeItem.tooltip = '当前输入法模式';
    this.imeItem.show();

    // Vim 模式指示（右侧，紧跟 IME 指示器）
    this.modeItem = vscode.window.createStatusBarItem(
      vscode.StatusBarAlignment.Right,
      99,
    );
    this.modeItem.show();
  }

  /** 更新 IME 状态显示 */
  updateImeState(state: ImeState, action: ImeAction): void {
    let text: string;
    let color: string;

    if (state.isCapsLock) {
      text = '⬆ CAPS';
      color = '#FFFF00';
    } else {
      switch (action) {
        case ImeAction.CHINESE:
          text = '中';
          color = '#00FF00';
          break;
        case ImeAction.ENGLISH:
        default:
          text = 'EN';
          color = '#FFFFFF';
          break;
      }
    }

    this.imeItem.text = text;
    this.imeItem.color = color;
  }

  /** 更新 Vim 模式显示 */
  updateVimMode(mode: VimMode): void {
    const labels: Record<string, string> = {
      normal: '-- NORMAL --',
      visual: '-- VISUAL --',
      'visual-line': '-- V-LINE --',
      'visual-block': '-- V-BLOCK --',
      insert: '-- INSERT --',
      replace: '-- REPLACE --',
      command: '-- COMMAND --',
    };

    this.modeItem.text = labels[mode] ?? '';
    this.modeItem.color = '#999999';
  }

  dispose(): void {
    this.imeItem.dispose();
    this.modeItem.dispose();
  }
}
