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

export const AVAILABLE_IME_TYPES: readonly ImeType[] = [ImeType.RIME];

export function availableImeTypeFor(value: string | undefined): ImeType {
  return AVAILABLE_IME_TYPES.includes(value as ImeType)
    ? value as ImeType
    : ImeType.RIME;
}

/** 输入法配置 */
export interface ImeConfig {
  type: ImeType;
  weaselServerPath?: string;
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
  readonly type: ImeType;
  readonly name: string;

  onStateChanged?: (state: ImeState) => void;

  start(): void;

  /** 切换中英文模式 */
  setAsciiMode(
    ascii: boolean,
    shouldContinue?: () => boolean,
    forceLowercase?: boolean,
  ): Promise<void>;

  /** 确保输入法处于英文模式，但不改变 CapsLock。 */
  ensureAsciiMode(shouldContinue?: () => boolean): Promise<void>;

  /** 切换大写模式 */
  setCapsMode(shouldContinue?: () => boolean): Promise<void>;

  /** 释放插件自身开启的 CapsLock */
  releaseOwnedCapsLock(): Promise<void>;

  /** 是否正在输入 */
  isComposing(): Promise<boolean>;

  /** 获取当前跟踪的 IME 状态 */
  getTrackedState(): ImeState;

  /** 获取当前实际 IME 状态，检测不可用时回退到跟踪状态。 */
  getCurrentState(): ImeState;

  /** 主动刷新 Provider 的状态源。 */
  refreshState(): void;

  /** 同步内部跟踪状态 */
  syncTrackedState(ascii: boolean, caps: boolean): void;

  /** 释放资源 */
  dispose(): void;
}

export enum InputDisplayMode {
  ENGLISH = 'ENGLISH',
  CHINESE = 'CHINESE',
  CAPS = 'CAPS',
}

export function inputDisplayModeFor(state: ImeState): InputDisplayMode {
  if (state.isCapsLock) return InputDisplayMode.CAPS;
  return state.isAsciiMode ? InputDisplayMode.ENGLISH : InputDisplayMode.CHINESE;
}
