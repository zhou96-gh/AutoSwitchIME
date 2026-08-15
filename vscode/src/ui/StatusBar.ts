/**
 * 状态栏 IME 模式指示器
 * 在 VSCode 底部状态栏显示当前 IME 模式
 */

import * as vscode from 'vscode';
import { ImeState, InputDisplayMode, inputDisplayModeFor } from '../core/types';

export class ImeStatusBar implements vscode.Disposable {
  private imeItem: vscode.StatusBarItem;
  private lastState: ImeState | null = null;

  constructor() {
    this.imeItem = vscode.window.createStatusBarItem(
      vscode.StatusBarAlignment.Right,
      100,
    );
    this.showWaiting();
    this.imeItem.show();
  }

  /** 更新 IME 状态显示 */
  updateImeState(state: ImeState): void {
    this.lastState = state;
    let text: string;
    let color: string;
    let tooltip: string;

    switch (inputDisplayModeFor(state)) {
      case InputDisplayMode.CAPS:
        text = '$(arrow-up) 大写';
        color = '#FFCC00';
        tooltip = 'AutoSwitchIME 当前输入状态：大写英文';
        break;
      case InputDisplayMode.CHINESE:
        text = '$(keyboard) 中文';
        color = '#00CC66';
        tooltip = state.isComposing
          ? 'AutoSwitchIME 当前输入状态：中文（正在输入）'
          : 'AutoSwitchIME 当前输入状态：中文';
        break;
      default:
        text = '$(keyboard) 英文';
        color = '#FFFFFF';
        tooltip = 'AutoSwitchIME 当前输入状态：英文';
        break;
    }

    this.imeItem.text = text;
    this.imeItem.color = color;
    this.imeItem.backgroundColor = undefined;
    this.imeItem.tooltip = tooltip;
  }

  updateStateSourceAvailability(available: boolean): void {
    if (available) {
      if (this.lastState) {
        this.updateImeState(this.lastState);
      } else {
        this.showWaiting();
      }
      return;
    }

    this.imeItem.text = '$(warning) IME 暂停';
    this.imeItem.color = new vscode.ThemeColor('statusBarItem.warningForeground');
    this.imeItem.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground');
    this.imeItem.tooltip = 'Rime 尚未上报当前 VSCode 窗口的输入状态，AutoSwitchIME 已暂停。输入一次后仍未恢复时，请重新部署 Rime 状态组件。';
  }

  dispose(): void {
    this.imeItem.dispose();
  }

  private showWaiting(): void {
    this.imeItem.text = '$(sync~spin) IME 等待';
    this.imeItem.color = new vscode.ThemeColor('statusBar.foreground');
    this.imeItem.backgroundColor = undefined;
    this.imeItem.tooltip = '正在等待当前 VSCode 窗口的实际输入状态';
  }
}
