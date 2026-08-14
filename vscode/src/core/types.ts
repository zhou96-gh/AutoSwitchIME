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

/** 输入法级别可以只提供其中一部分状态。 */
export interface ImeStateSource {
  readAsciiMode?(): boolean | null;
  readCapsLock?(): boolean | null;
  readComposing?(): boolean | null;
}

export interface ImeAsciiModeSwitcher {
  /** false 表示输入法专用切换失败，不再降级到系统级切换。 */
  switchAsciiMode(
    ascii: boolean,
    shouldContinue: () => boolean,
  ): Promise<boolean>;
}

export interface ImeCapsLockSwitcher {
  /** false 表示输入法专用切换失败，不再降级到系统级切换。 */
  switchCapsLock(
    enabled: boolean,
    shouldContinue: () => boolean,
  ): Promise<boolean>;
}

/** 输入法专用能力容器；未提供的能力由 ImeGateway 交给系统级 Provider。 */
export interface ImeProvider {
  readonly type: ImeType;
  readonly name: string;

  readonly stateSource?: ImeStateSource;
  readonly asciiModeSwitcher?: ImeAsciiModeSwitcher;
  readonly capsLockSwitcher?: ImeCapsLockSwitcher;

  start(): void;
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
