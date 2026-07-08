/**
 * IME 状态文件监听器
 * 从 Kotlin core/ime/StateWatcher.kt 移植
 * 监听 Rime Lua 脚本写入的 %TEMP%\ime-state-rime.json
 */

import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { ImeState, ImeType, Logger } from './types';

/** 获取输入法状态文件名 */
function getStateFileName(type: ImeType): string {
  switch (type) {
    case ImeType.RIME:
      return 'ime-state-rime.json';
    case ImeType.SOGOU:
      return 'ime-state-sogou.json';
    case ImeType.MS_PINYIN:
      return 'ime-state-mspinyin.json';
    case ImeType.CUSTOM:
      return 'ime-state-custom.json';
  }
}

/**
 * 解析状态 JSON 中的布尔值
 * 使用正则提取，避免 JSON.parse 对不完整文件报错
 */
function parseStateJson(content: string): ImeState | null {
  try {
    const asciiMode = extractBoolean(content, 'ascii_mode');
    if (asciiMode === null) return null;
    const capsLock = extractBoolean(content, 'caps_lock') ?? false;
    const isComposing = extractBoolean(content, 'is_composing') ?? false;
    return { isAsciiMode: asciiMode, isCapsLock: capsLock, isComposing };
  } catch {
    return null;
  }
}

function extractBoolean(json: string, key: string): boolean | null {
  const regex = new RegExp(`"${key}"\\s*:\\s*(true|false)`);
  const match = regex.exec(json);
  return match ? match[1] === 'true' : null;
}

export class StateWatcher {
  private watcher: fs.FSWatcher | null = null;
  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private isRunning = false;

  isComposing = false;
  lastAsciiMode = true;
  lastCapsLock = false;

  /** 防止递归：正在强制切换 IME 时跳过状态文件读取 */
  isForcingImeSwitch = false;

  private readonly stateFilePath: string;

  constructor(
    imeType: ImeType,
    private logger: Logger,
    private onStateChanged: (state: ImeState) => void,
    private capsCheckFn?: () => boolean,
  ) {
    const fileName = getStateFileName(imeType);
    this.stateFilePath = path.join(
      process.env.TEMP || os.tmpdir(),
      fileName,
    );
  }

  start(): void {
    if (this.isRunning) {
      this.logger.debug('StateWatcher already running');
      return;
    }

    this.logger.info(`Starting StateWatcher, monitoring: ${this.stateFilePath}`);
    this.isRunning = true;

    // 确保状态文件存在
    this.ensureStateFile();

    // 尝试使用 fs.watch，回退到轮询
    this.startFileWatch();

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

  private ensureStateFile(): void {
    try {
      if (!fs.existsSync(this.stateFilePath)) {
        const dir = path.dirname(this.stateFilePath);
        fs.mkdirSync(dir, { recursive: true });
        fs.writeFileSync(this.stateFilePath, '{"ascii_mode":true,"caps_lock":false,"is_composing":false}');
        this.logger.debug('Created initial state file');
      }
    } catch (e) {
      this.logger.warn('Failed to create state file', e as Error);
    }
  }

  private startFileWatch(): void {
    const dir = path.dirname(this.stateFilePath);
    const fileName = path.basename(this.stateFilePath);

    try {
      this.watcher = fs.watch(dir, (eventType, changedFile) => {
        if (changedFile && (changedFile.includes('ime-state') || changedFile === fileName)) {
          this.readAndApplyState();
        }
      });
      this.watcher.on('error', (err: Error) => {
        this.logger.warn('fs.watch error, falling back to polling', err);
        this.watcher?.close();
        this.watcher = null;
        this.startPolling();
      });
    } catch (e) {
      this.logger.warn('fs.watch not available, using polling', e as Error);
      this.startPolling();
    }
  }

  /**
   * 轮询回退方案（500ms 间隔）
   */
  private startPolling(): void {
    let lastMtime = 0;
    this.pollTimer = setInterval(() => {
      if (!this.isRunning) return;
      try {
        const stat = fs.statSync(this.stateFilePath);
        if (stat.mtimeMs !== lastMtime) {
          lastMtime = stat.mtimeMs;
          this.readAndApplyState();
        }
      } catch {
        // 文件可能被删除，忽略
      }
    }, 500);
  }

  private readAndApplyState(): void {
    if (this.isForcingImeSwitch) {
      this.logger.debug('Skipping state file read during forced IME switch');
      return;
    }

    try {
      if (!fs.existsSync(this.stateFilePath)) return;

      const content = fs.readFileSync(this.stateFilePath, 'utf-8').trim();
      if (!content) return;

      const state = parseStateJson(content);
      if (!state) return;

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
}
