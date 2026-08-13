/**
 * IME 状态文件监听器
 * 从 Kotlin core/ime/StateWatcher.kt 移植
 * 监听 Rime Lua 脚本写入的当前应用 session 状态文件
 */

import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { ImeState, Logger } from './types';

const RIME_STATE_FILE_NAME = 'ime-state-rime-v2.json';

/**
 * 解析 Rime session 状态 JSON；不完整写入直接返回 null。
 */
export interface RimeSessionState {
  state: ImeState;
  sessionToken: string;
  sequence: number;
}

export function parseRimeSessionState(content: string): RimeSessionState | null {
  try {
    const data = JSON.parse(content) as Record<string, unknown>;
    if (
      data.protocol_version !== 2 ||
      data.provider !== 'rime' ||
      typeof data.session_token !== 'string' || !data.session_token ||
      typeof data.sequence !== 'number' ||
      !Number.isSafeInteger(data.sequence) || data.sequence < 1 ||
      typeof data.ascii_mode !== 'boolean' ||
      typeof data.caps_lock !== 'boolean' ||
      typeof data.is_composing !== 'boolean' ||
      typeof data.timestamp !== 'number' ||
      !Number.isSafeInteger(data.timestamp) || data.timestamp < 1
    ) {
      return null;
    }

    return {
      state: {
        isAsciiMode: data.ascii_mode,
        isCapsLock: data.caps_lock,
        isComposing: data.is_composing,
      },
      sessionToken: data.session_token,
      sequence: data.sequence,
    };
  } catch {
    return null;
  }
}

export class RimeSessionTracker {
  private currentSessionToken: string | undefined;
  private lastSequence = 0;
  accept(update: RimeSessionState): boolean {
    if (
      update.sessionToken === this.currentSessionToken &&
      update.sequence <= this.lastSequence
    ) {
      return false;
    }

    this.currentSessionToken = update.sessionToken;
    this.lastSequence = update.sequence;
    return true;
  }
}

export class RimeStateWatcher {
  private watcher: fs.FSWatcher | null = null;
  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private isRunning = false;

  isComposing = false;
  lastAsciiMode = true;
  lastCapsLock = false;

  /** 防止递归：正在强制切换 IME 时跳过状态文件读取 */
  isForcingImeSwitch = false;

  private readonly stateFilePath: string;
  private readonly sessionTracker: RimeSessionTracker;

  constructor(
    private logger: Logger,
    private onStateChanged: (state: ImeState) => void,
  ) {
    const fileName = RIME_STATE_FILE_NAME;
    this.stateFilePath = path.join(
      process.env.TEMP || os.tmpdir(),
      fileName,
    );
    this.sessionTracker = new RimeSessionTracker();
  }

  start(): void {
    if (this.isRunning) {
      this.logger.debug('StateWatcher already running');
      return;
    }

    this.logger.info(`Starting StateWatcher, monitoring: ${this.stateFilePath}`);
    this.isRunning = true;

    // fs.watch 用于即时通知，轮询用于兜底 Windows 上可能丢失的文件事件。
    this.startFileWatch();
    this.startPolling();

    // 初始化：读取当前状态
    this.readAndApplyState();
  }

  stop(): void {
    this.isRunning = false;
    if (this.watcher) {
      this.watcher.close();
      this.watcher = null;
    }
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
    this.logger.info('StateWatcher stopped');
  }

  refresh(): void {
    this.readAndApplyState();
  }

  private startFileWatch(): void {
    const dir = path.dirname(this.stateFilePath);
    const fileName = path.basename(this.stateFilePath);

    try {
      this.watcher = fs.watch(dir, (eventType, changedFile) => {
        if (changedFile === fileName) {
          this.readAndApplyState();
        }
      });
      this.watcher.on('error', (err: Error) => {
        this.logger.warn('fs.watch error, continuing with polling', err);
        this.watcher?.close();
        this.watcher = null;
      });
    } catch (e) {
      this.logger.warn('fs.watch not available, using polling', e as Error);
    }
  }

  /**
   * 轮询回退方案（500ms 间隔）
   */
  private startPolling(): void {
    if (this.pollTimer) return;
    this.pollTimer = setInterval(() => {
      if (!this.isRunning) return;
      this.readAndApplyState();
    }, 500);
  }

  private readAndApplyState(): void {
    if (this.isForcingImeSwitch) {
      this.logger.debug('Skipping state file read during forced IME switch');
      return;
    }

    try {
      const update = this.readStateFile(this.stateFilePath);
      if (!update) return;
      if (!this.sessionTracker.accept(update)) return;
      const state = update.state;

      if (
        state.isAsciiMode !== this.lastAsciiMode ||
        state.isCapsLock !== this.lastCapsLock ||
        state.isComposing !== this.isComposing
      ) {
        this.logger.info(
          `IME state changed: ascii=${state.isAsciiMode}, caps=${state.isCapsLock}, composing=${state.isComposing}`,
        );
        this.lastAsciiMode = state.isAsciiMode;
        this.lastCapsLock = state.isCapsLock;
        this.isComposing = state.isComposing;

        this.onStateChanged(state);
      }
    } catch (e) {
      this.logger.debug('Failed to parse state file (may be in progress)');
    }
  }

  private readStateFile(stateFilePath: string): RimeSessionState | null {
    if (!fs.existsSync(stateFilePath)) return null;
    const content = fs.readFileSync(stateFilePath, 'utf-8').trim();
    if (!content) return null;
    return parseRimeSessionState(content);
  }
}
