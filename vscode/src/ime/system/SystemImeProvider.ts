import {
  ImeAsciiModeSwitcher,
  ImeCapsLockSwitcher,
  ImeStateSource,
} from '../../core/types';
import {
  isNativeAvailable,
  nativeCapsRead,
  nativeCapsSet,
  nativeIsComposing,
  nativeSetAsciiMode,
  nativeSystemImeStatus,
} from './native';

export enum SystemType {
  WINDOWS = 'windows',
  MACOS = 'macos',
  LINUX = 'linux',
  UNKNOWN = 'unknown',
}

export function currentSystemType(platform = process.platform): SystemType {
  switch (platform) {
    case 'win32':
      return SystemType.WINDOWS;
    case 'darwin':
      return SystemType.MACOS;
    case 'linux':
      return SystemType.LINUX;
    default:
      return SystemType.UNKNOWN;
  }
}

export interface SystemImeProvider
  extends ImeStateSource, ImeAsciiModeSwitcher, ImeCapsLockSwitcher {
  readonly type: SystemType;
}

type SystemProviderFactory = () => SystemImeProvider;

export class SystemImeProviderRegistry {
  private readonly providers = new Map<SystemType, SystemProviderFactory>();

  register(type: SystemType, factory: SystemProviderFactory): void {
    this.providers.set(type, factory);
  }

  create(type: SystemType): SystemImeProvider {
    const factory = this.providers.get(type);
    if (!factory) {
      throw new Error(`System IME provider not found for type: ${type}`);
    }
    return factory();
  }

  supportedTypes(): SystemType[] {
    return [...this.providers.keys()];
  }
}

export class WindowsSystemImeProvider implements SystemImeProvider {
  readonly type = SystemType.WINDOWS;

  readAsciiMode(): boolean | null {
    return nativeSystemImeStatus()?.isAsciiMode ?? null;
  }

  readCapsLock(): boolean | null {
    return isNativeAvailable() ? nativeCapsRead() : null;
  }

  readComposing(): boolean {
    return nativeIsComposing() === 1;
  }

  async switchAsciiMode(
    ascii: boolean,
    shouldContinue: () => boolean,
  ): Promise<boolean> {
    for (let attempt = 0; attempt < 3; attempt++) {
      if (!shouldContinue()) return false;
      if (nativeSetAsciiMode(ascii)) return true;
      await delay(50);
    }
    return false;
  }

  async switchCapsLock(
    enabled: boolean,
    shouldContinue: () => boolean,
  ): Promise<boolean> {
    for (let attempt = 0; attempt < 5; attempt++) {
      if (!shouldContinue()) return false;
      if (isNativeAvailable() && nativeCapsRead() === enabled) return true;
      nativeCapsSet(enabled);
      await delay(50);
    }
    return isNativeAvailable() && nativeCapsRead() === enabled;
  }
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}
