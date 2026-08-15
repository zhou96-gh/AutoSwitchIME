import { execFile, execSync } from 'child_process';
import { promisify } from 'util';
import * as fs from 'fs';
import * as path from 'path';
import {
  ImeAsciiModeSwitcher,
  ImePartialState,
  ImeProvider,
  ImeStateSource,
  ImeType,
  Logger,
} from '../../core/types';
import { nativeRimeInputState, nativeWaitForRimeStateChange } from '../system/native';

const execFileAsync = promisify(execFile);
const SEARCH_BASE_DIRS = [
  'C:\\Program Files (x86)\\Rime',
  'C:\\Program Files\\Rime',
  'D:\\Program Files\\Rime',
];

function detectWeaselServer(logger?: Logger): string | null {
  const fromRegistry = readRegistry(logger);
  return fromRegistry ?? scanDirectories(logger);
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
      if (fs.existsSync(serverPath)) candidates.push({ version, path: serverPath });
    }
  }
  candidates.sort((a, b) => compareVersions(b.version, a.version));
  const result = candidates[0]?.path ?? null;
  if (result) logger?.info(`找到 WeaselServer (扫描): ${result}`);
  return result;
}

function compareVersions(a: string, b: string): number {
  const pa = a.split('.').map(Number);
  const pb = b.split('.').map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const difference = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (difference !== 0) return difference;
  }
  return 0;
}

/** Rime 通过 Lua 共享内存提供中英文/输入中状态，并使用 Weasel 指令切换中英文。 */
export class RimeImeProvider implements ImeProvider, ImeAsciiModeSwitcher, ImeStateSource {
  readonly type = ImeType.RIME;
  readonly name = 'Rime/Weasel';
  readonly asciiModeSwitcher = this;
  readonly stateSource = this;

  private weaselServerPath: string | null;
  private warnedMissing = false;
  private stateAvailable = false;

  constructor(
    private logger: Logger,
    weaselPath?: string,
  ) {
    this.weaselServerPath = weaselPath ?? detectWeaselServer(logger);
    this.logger.info(`WeaselServer path: ${this.weaselServerPath ?? '(not found)'}`);
  }

  start(): void {
  }

  readState(): ImePartialState {
    const actual = nativeRimeInputState();
    this.stateAvailable = actual != null;
    return actual == null ? {} : {
      isAsciiMode: actual.isAsciiMode,
      isComposing: actual.isComposing,
    };
  }

  isAvailable(): boolean {
    return this.stateAvailable;
  }

  supportsChangeNotifications(): boolean {
    return true;
  }

  async waitForStateChange(timeoutMillis: number): Promise<boolean> {
    const result = await nativeWaitForRimeStateChange(timeoutMillis);
    if (result < 0) {
      await new Promise((resolve) => setTimeout(resolve, timeoutMillis));
    }
    return result > 0;
  }

  async switchAsciiMode(
    ascii: boolean,
    shouldContinue: () => boolean = () => true,
  ): Promise<boolean> {
    return this.switchImeMode(
      ascii ? '/ascii' : '/nascii',
      shouldContinue,
    );
  }

  dispose(): void {
  }

  private async switchImeMode(
    arg: string,
    shouldContinue: () => boolean,
  ): Promise<boolean> {
    const exePath = this.weaselServerPath;
    if (!exePath) {
      if (!this.warnedMissing) {
        this.warnedMissing = true;
        const msg = 'WeaselServer.exe 未找到，请检查 autoSwitchIME.weaselServerPath 设置';
        this.logger.warn(msg);
      }
      return false;
    }
    if (!fs.existsSync(exePath)) {
      this.logger.warn(`WeaselServer.exe 不存在: ${exePath}`);
      return false;
    }
    try {
      if (!shouldContinue()) return false;
      await execFileAsync(exePath, [arg], { timeout: 3000, windowsHide: true });
      this.logger.info(`IME 切换: ${arg}`);
      return true;
    } catch (error) {
      this.logger.warn(`WeaselServer.exe 调用失败: ${arg}`, error as Error);
      return false;
    }
  }
}
