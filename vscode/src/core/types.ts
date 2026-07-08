/**
 * 平台无关的核心类型定义
 * 从 Kotlin core/ 模块移植
 */

/** IME 状态 */
export interface ImeState {
  isAsciiMode: boolean;
  isCapsLock: boolean;
  isComposing: boolean;
}

/** IME 动作 */
export enum ImeAction {
  /** 切换到中文模式 */
  CHINESE = 'CHINESE',
  /** 切换到大写模式 */
  CAPS = 'CAPS',
  /** 切换到英文模式 */
  ENGLISH = 'ENGLISH',
  /** 保持当前状态 */
  UNCHANGED = 'UNCHANGED',
}

/** 输入法类型 */
export enum ImeType {
  RIME = 'rime',
  SOGOU = 'sogou',
  MS_PINYIN = 'ms_pinyin',
  CUSTOM = 'custom',
}

/** 输入法配置 */
export interface ImeConfig {
  type: ImeType;
  weaselServerPath?: string;
  customSwitchScript?: string;
}

/** Vim 模式 */
export enum VimMode {
  NORMAL = 'normal',
  VISUAL = 'visual',
  VISUAL_LINE = 'visual-line',
  VISUAL_BLOCK = 'visual-block',
  INSERT = 'insert',
  REPLACE = 'replace',
  COMMAND = 'command',
  UNKNOWN = 'unknown',
}

/** 平台无关的日志接口 */
export interface Logger {
  info(msg: string): void;
  warn(msg: string, e?: Error): void;
  debug(msg: string): void;
  error(msg: string, e?: Error): void;
}

/** VSCode 输出通道实现的 Logger */
export class VSCodeLogger implements Logger {
  constructor(private outputChannel: { appendLine(value: string): void }) {}

  info(msg: string): void {
    this.outputChannel.appendLine(`[INFO] ${msg}`);
  }
  warn(msg: string, e?: Error): void {
    this.outputChannel.appendLine(`[WARN] ${msg}${e ? ' ' + e.message : ''}`);
  }
  debug(msg: string): void {
    this.outputChannel.appendLine(`[DEBUG] ${msg}`);
  }
  error(msg: string, e?: Error): void {
    this.outputChannel.appendLine(`[ERROR] ${msg}${e ? ' ' + e.message : ''}`);
  }
}

/** IME Provider 接口（平台无关） */
export interface ImeProvider {
  readonly name: string;

  /** 切换中英文模式 */
  setAsciiMode(ascii: boolean): Promise<void>;

  /** 切换大写模式 */
  setCapsMode(): Promise<void>;

  /** 是否正在输入 */
  isComposing(): Promise<boolean>;

  /** 获取当前跟踪的 IME 状态 */
  getTrackedState(): ImeState;

  /** 同步内部跟踪状态 */
  syncTrackedState(ascii: boolean, caps: boolean): void;

  /** 释放资源 */
  dispose(): void;
}
