import { ImeAction, VimMode } from './types';

export function isNormalLikeMode(mode: VimMode, hasSelection = false): boolean {
  if (hasSelection) return true;
  return mode !== VimMode.INSERT && mode !== VimMode.UNKNOWN;
}

export function isStrictNormalMode(mode: VimMode, hasSelection = false): boolean {
  return !hasSelection && mode === VimMode.NORMAL;
}

export function resolveNormalModeAction(
  mode: VimMode,
  hasSelection: boolean,
  defaultApplied: boolean,
): ImeAction | null {
  if (!isNormalLikeMode(mode, hasSelection)) return null;
  if (isStrictNormalMode(mode, hasSelection) || !defaultApplied) {
    return ImeAction.ENGLISH;
  }
  return ImeAction.UNCHANGED;
}

export function shouldEnforceNormalEnglish(
  mode: VimMode,
  hasSelection: boolean,
  asciiMode: boolean,
  capsLock: boolean,
): boolean {
  return isStrictNormalMode(mode, hasSelection) && (!asciiMode || capsLock);
}
