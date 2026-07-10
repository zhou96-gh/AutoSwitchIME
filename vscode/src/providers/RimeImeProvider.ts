import { execFile, execSync } from 'child_process';
import { promisify } from 'util';
import * as fs from 'fs';
import * as path from 'path';
import { ImeProvider, ImeState, ImeType, Logger } from '../core/types';
import { StateWatcher } from '../core/StateWatcher';
import { initNative, isNativeAvailable, nativeCapsRead, nativeCapsSet, nativeIsComposing } from '../core/native';

const execFileAsync = promisify(execFile);

const SEARCH_BASE_DIRS = [
  'C:\\Program Files (x86)\\Rime',
  'C:\\Program Files\\Rime',
  'D:\\Program Files\\Rime',
];

function detectWeaselServer(logger?: Logger): string | null {
  const fromRegistry = readRegistry(logger);
  if (fromRegistry) return fromRegistry;
  return scanDirectories(logger);
}

function readRegistry(logger?: Logger): string | null {
  try {
    const output = execSync(
      'reg query "HKLM\\SOFTWARE\\Rime\\Weasel" /v WeaselRoot',
      { encoding: 'utf-8', timeout: 5000 },
    );
    const match = output.match(/WeaselRoot\s+REG_SZ\s+(.+)/);
    if (match?.[1]) {
      const serverPath = path.join(match[1].trim(), 'WeaselServer.exe');
      if (fs.existsSync(serverPath)) {
        logger?.info(`找到 WeaselServer (注册表): ${serverPath}`);
        return serverPath;
      }
    }
  } catch {
  }
  return null;
}

function scanDirectories(logger?: Logger): string | null {
  const candidates: { version: string; path: string }[] = [];

  for (const basePath of SEARCH_BASE_DIRS) {
    if (!fs.existsSync(basePath)) continue;

    let entries: fs.Dirent[];
    try {
      entries = fs.readdirSync(basePath, { withFileTypes: true });
    } catch {
      continue;
    }

    for (const entry of entries) {
      if (!entry.isDirectory() || !entry.name.startsWith('weasel-')) continue;

      const version = entry.name.substring('weasel-'.length);
      const serverPath = path.join(basePath, entry.name, 'WeaselServer.exe');
      if (fs.existsSync(serverPath)) {
        candidates.push({ version, path: serverPath });
      }
    }
  }

  if (candidates.length === 0) return null;

  candidates.sort((a, b) => compareVersions(b.version, a.version));
  const result = candidates[0].path;
  logger?.info(`找到 WeaselServer (扫描): ${result}`);
  return result;
}

function compareVersions(a: string, b: string): number {
  const pa = a.split('.').map(Number);
  const pb = b.split('.').map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const va = pa[i] ?? 0;
    const vb = pb[i] ?? 0;
    if (va !== vb) return va - vb;
  }
  return 0;
}

export class RimeImeProvider implements ImeProvider {
  readonly name = 'Rime/Weasel';
  readonly stateWatcher: StateWatcher;

  /** 状态文件变化回调（粘贴 Lua bridge 写入的最新状态） */
  onStateChanged?: (state: ImeState) => void;

  currentAsciiMode = true;
  private weaselServerPath: string | null;
  private warnedMissing = false;
  private ownsCapsLock = false;
  /** 状态文件中的 isComposing（延迟更新），native 检测失败时 fallback */
  private fileIsComposing = false;

  constructor(
    private logger: Logger,
    weaselPath?: string,
    private showWarning?: (msg: string) => void,
    extensionPath?: string,
  ) {
    this.weaselServerPath = weaselPath ?? detectWeaselServer(logger);
    this.logger.info(`WeaselServer path: ${this.weaselServerPath ?? '(not found)'}`);

    if (extensionPath) {
      initNative(extensionPath);
    }
    this.logger.info(`Native DLL: ${isNativeAvailable() ? 'loaded' : 'unavailable'}`);

    this.stateWatcher = new StateWatcher(
      ImeType.RIME,
      logger,
      (state: ImeState) => this.onImeStateChanged(state),
    );
  }

  start(): void {
    this.stateWatcher.start();
  }

  async setAsciiMode(
    ascii: boolean,
    shouldContinue: () => boolean = () => true,
  ): Promise<void> {
    if (!shouldContinue()) return;
    this.stateWatcher.isForcingImeSwitch = true;
    try {
      const capsOn = nativeCapsRead();

      const alreadyAscii = this.currentAsciiMode === ascii;
      if (alreadyAscii && !capsOn) return;

      // 只关闭插件自己开启的 CapsLock，不影响用户手动开启的全局 CapsLock。
      if (capsOn && this.ownsCapsLock) {
        if (!shouldContinue()) return;
        await this.forceCapsOff(shouldContinue);
        if (!shouldContinue()) return;
        this.ownsCapsLock = false;
      }

      if (alreadyAscii) return;
      if (!shouldContinue()) return;

      this.currentAsciiMode = ascii;
      await this.switchImeMode(ascii ? '/ascii' : '/nascii');
    } finally {
      this.stateWatcher.isForcingImeSwitch = false;
    }
  }

  /** 确保 Weasel 在英文模式，但保留 CapsLock 状态不变
   *  用于 poll timer 处理手动 CapsLock：用户自己按了 CapsLock，不应该被自动关闭
   */
  async ensureAsciiMode(
    shouldContinue: () => boolean = () => true,
  ): Promise<void> {
    if (!shouldContinue()) return;
    this.stateWatcher.isForcingImeSwitch = true;
    try {
      if (!this.currentAsciiMode) {
        if (!shouldContinue()) return;
        this.currentAsciiMode = true;
        await this.switchImeMode('/ascii');
      }
    } finally {
      this.stateWatcher.isForcingImeSwitch = false;
    }
  }

  async setCapsMode(
    shouldContinue: () => boolean = () => true,
  ): Promise<void> {
    if (!shouldContinue()) return;
    this.stateWatcher.isForcingImeSwitch = true;
    try {
      const capsOnBefore = nativeCapsRead();
      if (this.currentAsciiMode && capsOnBefore) return;

      // Caps 模式 = WeaselServer 英文模式 + CapsLock 开启
      // 确保 WeaselServer 在英文模式（/ascii），这样输出大写英文字母
      if (!this.currentAsciiMode) {
        if (!shouldContinue()) return;
        this.currentAsciiMode = true;
        await this.switchImeMode('/ascii');
      }

      // 开启 CapsLock（含重试 + 验证）
      if (!shouldContinue()) return;
      if (!nativeCapsRead()) {
        await this.forceCapsOn(shouldContinue);
        this.ownsCapsLock = nativeCapsRead();
        if (!shouldContinue()) return;
      } else {
        this.ownsCapsLock = false;
      }

      this.onStateChanged?.({
        isAsciiMode: true,
        isCapsLock: nativeCapsRead(),
        isComposing: false,
      });
    } finally {
      this.stateWatcher.isForcingImeSwitch = false;
    }
  }

  async releaseOwnedCapsLock(): Promise<void> {
    if (!this.ownsCapsLock) return;
    if (nativeCapsRead()) {
      await this.forceCapsOff();
    }
    this.ownsCapsLock = false;
    this.onStateChanged?.({
      isAsciiMode: this.currentAsciiMode,
      isCapsLock: nativeCapsRead(),
      isComposing: this.fileIsComposing,
    });
  }

  /** 强制开启 CapsLock，最多重试 5 次，每次间隔 50ms */
  private async forceCapsOn(shouldContinue: () => boolean): Promise<void> {
    for (let i = 0; i < 5; i++) {
      if (nativeCapsRead()) return;
      if (!isNativeAvailable() || !shouldContinue()) return;
      nativeCapsSet(true);
      await this.sleepAsync(50);
    }
  }

  /** 强制关闭 CapsLock，最多重试 5 次，每次间隔 50ms */
  private async forceCapsOff(
    shouldContinue: () => boolean = () => true,
  ): Promise<void> {
    for (let i = 0; i < 5; i++) {
      if (!nativeCapsRead()) return;
      if (!isNativeAvailable() || !shouldContinue()) return;
      nativeCapsSet(false);
      await this.sleepAsync(50);
    }
  }

  private sleepAsync(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  async isComposing(): Promise<boolean> {
    const native = nativeIsComposing();
    if (native >= 0) return native === 1;
    return this.fileIsComposing;
  }

  getTrackedState(): ImeState {
    return {
      isAsciiMode: this.currentAsciiMode,
      isCapsLock: nativeCapsRead(),
      isComposing: this.fileIsComposing,
    };
  }

  getPhysicalCapsLockState(): boolean {
    return nativeCapsRead();
  }

  syncTrackedState(ascii: boolean, _caps: boolean): void {
    this.currentAsciiMode = ascii;
  }

  dispose(): void {
    this.stateWatcher.stop();
  }

  private onImeStateChanged(state: ImeState): void {
    this.currentAsciiMode = state.isAsciiMode;
    this.fileIsComposing = state.isComposing;
    this.onStateChanged?.({
      ...state,
      isCapsLock: nativeCapsRead(),
    });
  }

  private async switchImeMode(arg: string): Promise<void> {
    const exePath = this.weaselServerPath;
    if (!exePath) {
      if (!this.warnedMissing) {
        this.warnedMissing = true;
        const msg = `WeaselServer.exe 未找到，请检查 autoSwitchIME.weaselServerPath 设置`;
        this.logger.warn(msg);
        this.showWarning?.(msg);
      }
      return;
    }

    if (!fs.existsSync(exePath)) {
      this.logger.warn(`WeaselServer.exe 不存在: ${exePath}`);
      this.showWarning?.(`WeaselServer.exe 不存在，请检查路径: ${exePath}`);
      return;
    }

    try {
      this.logger.debug(`执行: ${exePath} ${arg}`);
      await execFileAsync(exePath, [arg], {
        timeout: 3000,
        windowsHide: true,
      });
      this.logger.info(`IME 切换: ${arg}`);
    } catch (e) {
      this.logger.warn(`WeaselServer.exe 调用失败: ${arg}`, e as Error);
    }
  }
}
