import {
  ImeAsciiModeSwitcher,
  ImeCapsLockSwitcher,
  ImeProvider,
  ImeState,
  Logger,
} from '../core/types';
import { SystemImeProvider } from './system/SystemImeProvider';

/** 合并输入法级可选能力与系统级默认能力，并维护统一 ImeState。 */
export class ImeGateway {
  onStateChanged?: (state: ImeState) => void;

  private trackedState: ImeState = {
    isAsciiMode: true,
    isCapsLock: false,
    isComposing: false,
  };
  private ownsCapsLock = false;
  private lastPublishedState: ImeState | null = null;

  constructor(
    private readonly provider: ImeProvider,
    private readonly system: SystemImeProvider,
    private readonly logger: Logger,
  ) {}

  start(): void {
    this.provider.start();
  }

  getTrackedState(): ImeState {
    return this.trackedState;
  }

  getCurrentState(): ImeState {
    this.refreshState();
    return this.trackedState;
  }

  refreshState(): void {
    const source = this.provider.stateSource;
    this.trackedState = {
      isAsciiMode: source?.readAsciiMode?.()
        ?? this.system.readAsciiMode?.()
        ?? this.trackedState.isAsciiMode,
      isCapsLock: source?.readCapsLock?.()
        ?? this.system.readCapsLock?.()
        ?? this.trackedState.isCapsLock,
      isComposing: source?.readComposing?.()
        ?? this.system.readComposing?.()
        ?? false,
    };
    this.publishState();
  }

  isComposing(): boolean {
    const composing = this.provider.stateSource?.readComposing?.()
      ?? this.system.readComposing?.()
      ?? false;
    this.trackedState = { ...this.trackedState, isComposing: composing };
    this.publishState();
    return composing;
  }

  async setAsciiMode(
    ascii: boolean,
    shouldContinue: () => boolean = () => true,
    forceLowercase = false,
  ): Promise<void> {
    if (!shouldContinue()) return;
    if (this.trackedState.isCapsLock && (this.ownsCapsLock || forceLowercase)) {
      if (!await this.switchCapsLock(false, shouldContinue)) return;
      this.ownsCapsLock = false;
      this.refreshState();
    }

    if (this.trackedState.isAsciiMode !== ascii) {
      const switched = await this.asciiModeSwitcher().switchAsciiMode(
        ascii,
        shouldContinue,
      );
      if (!switched) {
        this.logger.warn(`${this.provider.name} failed to switch ASCII mode to ${ascii}`);
        return;
      }
    }
    if (shouldContinue()) this.refreshState();
  }

  async ensureAsciiMode(
    shouldContinue: () => boolean = () => true,
  ): Promise<void> {
    if (!shouldContinue()) return;
    if (!this.trackedState.isAsciiMode) {
      const switched = await this.asciiModeSwitcher().switchAsciiMode(
        true,
        shouldContinue,
      );
      if (!switched) {
        this.logger.warn(`${this.provider.name} failed to ensure ASCII mode`);
        return;
      }
    }
    if (shouldContinue()) this.refreshState();
  }

  async setCapsMode(
    shouldContinue: () => boolean = () => true,
  ): Promise<void> {
    if (!shouldContinue()) return;
    if (this.trackedState.isAsciiMode && this.trackedState.isCapsLock) return;

    if (!this.trackedState.isAsciiMode) {
      const switched = await this.asciiModeSwitcher().switchAsciiMode(
        true,
        shouldContinue,
      );
      if (!switched) {
        this.logger.warn(`${this.provider.name} failed to enter ASCII mode before CapsLock`);
        return;
      }
      this.refreshState();
    }
    if (!shouldContinue()) return;

    if (!this.trackedState.isCapsLock) {
      this.ownsCapsLock = await this.switchCapsLock(true, shouldContinue);
      if (!this.ownsCapsLock) {
        this.logger.warn(`${this.provider.name} failed to enable CapsLock`);
        return;
      }
    } else {
      this.ownsCapsLock = false;
    }
    if (shouldContinue()) this.refreshState();
  }

  async releaseOwnedCapsLock(): Promise<void> {
    if (!this.ownsCapsLock) return;
    await this.switchCapsLock(false, () => true);
    this.ownsCapsLock = false;
    this.refreshState();
  }

  dispose(): void {
    this.provider.dispose();
  }

  private asciiModeSwitcher(): ImeAsciiModeSwitcher {
    return this.provider.asciiModeSwitcher ?? this.system;
  }

  private capsLockSwitcher(): ImeCapsLockSwitcher {
    return this.provider.capsLockSwitcher ?? this.system;
  }

  private switchCapsLock(
    enabled: boolean,
    shouldContinue: () => boolean,
  ): Promise<boolean> {
    return this.capsLockSwitcher().switchCapsLock(enabled, shouldContinue);
  }

  private publishState(): void {
    if (this.lastPublishedState && sameState(this.lastPublishedState, this.trackedState)) {
      return;
    }
    this.lastPublishedState = this.trackedState;
    this.onStateChanged?.(this.trackedState);
  }
}

function sameState(left: ImeState, right: ImeState): boolean {
  return left.isAsciiMode === right.isAsciiMode
    && left.isCapsLock === right.isCapsLock
    && left.isComposing === right.isComposing;
}
