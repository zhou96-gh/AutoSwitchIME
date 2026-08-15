import {
  ImeCapsLockSwitcher,
  ImePartialState,
  ImeProvider,
  ImeStateSource,
  ImeState,
  Logger,
} from '../core/types';
import { SystemImeProvider } from './system/SystemImeProvider';

/** 合并输入法级可选能力与系统级默认能力，并维护统一 ImeState。 */
export class ImeGateway {
  onStateChanged?: (state: ImeState) => void;
  onStateSourceAvailabilityChanged?: (available: boolean) => void;
  onStateChangeSignal?: () => void;

  private trackedState: ImeState = {
    isAsciiMode: true,
    isCapsLock: false,
    isComposing: false,
  };
  private ownsCapsLock = false;
  private lastPublishedState: ImeState | null = null;
  private stateSourceAvailable: boolean;
  private availabilityKnown: boolean;
  private watchingStateChanges = false;

  constructor(
    private readonly provider: ImeProvider,
    private readonly system: SystemImeProvider,
    private readonly logger: Logger,
  ) {
    this.stateSourceAvailable = provider.stateSource == null;
    this.availabilityKnown = provider.stateSource == null;
  }

  start(): void {
    this.provider.start();
    this.startStateChangeWatcher();
  }

  getTrackedState(): ImeState {
    return this.trackedState;
  }

  isStateSourceAvailable(): boolean {
    return this.stateSourceAvailable;
  }

  supportsStateChangeNotifications(): boolean {
    return this.provider.stateSource?.supportsChangeNotifications?.() === true;
  }

  getCurrentState(): ImeState {
    this.refreshState();
    return this.trackedState;
  }

  refreshState(): void {
    const source = this.provider.stateSource;
    const specific = readPartialState(source);
    this.updateStateSourceAvailability(source?.isAvailable?.() ?? true);
    if (!this.stateSourceAvailable) return;

    const systemAsciiMode = this.system.readAsciiMode?.();
    this.trackedState = {
      isAsciiMode: specific.isAsciiMode
        ?? systemAsciiMode
        ?? this.trackedState.isAsciiMode,
      isCapsLock: specific.isCapsLock
        ?? this.system.readCapsLock?.()
        ?? this.trackedState.isCapsLock,
      isComposing: specific.isComposing
        ?? this.system.readComposing?.()
        ?? false,
    };
    this.publishState();
  }

  isComposing(): boolean {
    if (!this.stateSourceAvailable) return false;
    const composing = readPartialState(this.provider.stateSource).isComposing
      ?? this.system.readComposing?.()
      ?? false;
    this.trackedState = { ...this.trackedState, isComposing: composing };
    this.publishState();
    return composing;
  }

  async setAsciiMode(
    ascii: boolean,
    shouldContinue: () => boolean = () => true,
    forceAsciiMode = false,
  ): Promise<void> {
    if (!this.stateSourceAvailable || !shouldContinue()) return;
    if (this.trackedState.isCapsLock && (this.ownsCapsLock || forceAsciiMode)) {
      if (!await this.switchCapsLock(false, shouldContinue)) return;
      this.ownsCapsLock = false;
      this.refreshState();
    }

    if (this.trackedState.isAsciiMode !== ascii
      || forceAsciiMode
      || this.hasUnobservedProviderAsciiState()) {
      const switched = await this.switchAsciiMode(ascii, shouldContinue);
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
    if (!this.stateSourceAvailable || !shouldContinue()) return;
    if (!this.trackedState.isAsciiMode || this.hasUnobservedProviderAsciiState()) {
      const switched = await this.switchAsciiMode(true, shouldContinue);
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
    if (!this.stateSourceAvailable || !shouldContinue()) return;
    if (this.trackedState.isAsciiMode && this.trackedState.isCapsLock) return;

    if (!this.trackedState.isAsciiMode || this.hasUnobservedProviderAsciiState()) {
      const switched = await this.switchAsciiMode(true, shouldContinue);
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
    this.watchingStateChanges = false;
    this.provider.dispose();
  }

  private hasUnobservedProviderAsciiState(): boolean {
    return this.provider.asciiModeSwitcher != null
      && readPartialState(this.provider.stateSource).isAsciiMode == null;
  }

  private async switchAsciiMode(
    ascii: boolean,
    shouldContinue: () => boolean,
  ): Promise<boolean> {
    return (this.provider.asciiModeSwitcher ?? this.system).switchAsciiMode(
      ascii,
      shouldContinue,
    );
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
    if (!this.stateSourceAvailable) return;
    if (this.lastPublishedState && sameState(this.lastPublishedState, this.trackedState)) {
      return;
    }
    this.lastPublishedState = this.trackedState;
    this.onStateChanged?.(this.trackedState);
  }

  private updateStateSourceAvailability(available: boolean): void {
    if (this.availabilityKnown && this.stateSourceAvailable === available) return;
    this.availabilityKnown = true;
    this.stateSourceAvailable = available;
    if (available) {
      this.logger.info(`${this.provider.name} state source available; AutoSwitchIME resumed`);
    } else {
      this.logger.warn(`${this.provider.name} state source unavailable; AutoSwitchIME suspended`);
    }
    this.onStateSourceAvailabilityChanged?.(available);
  }

  private startStateChangeWatcher(): void {
    const source = this.provider.stateSource;
    if (!source?.supportsChangeNotifications?.() || !source.waitForStateChange) return;
    this.watchingStateChanges = true;
    void this.watchStateChanges(source);
  }

  private async watchStateChanges(source: ImeStateSource): Promise<void> {
    while (this.watchingStateChanges) {
      const changed = await source.waitForStateChange!(1000);
      if (!this.watchingStateChanges) return;
      if (changed || !this.stateSourceAvailable) this.onStateChangeSignal?.();
    }
  }
}

function readPartialState(source?: ImeStateSource): ImePartialState {
  return source?.readState?.() ?? {
    isAsciiMode: source?.readAsciiMode?.(),
    isCapsLock: source?.readCapsLock?.(),
    isComposing: source?.readComposing?.(),
  };
}

function sameState(left: ImeState, right: ImeState): boolean {
  return left.isAsciiMode === right.isAsciiMode
    && left.isCapsLock === right.isCapsLock
    && left.isComposing === right.isComposing;
}
