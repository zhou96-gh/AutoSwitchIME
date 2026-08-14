import * as fs from 'fs';
import * as path from 'path';

let dllPath: string | null = null;
let lib: ReturnType<typeof loadLib> | null = null;

function loadLib() {
  const koffi = require('koffi') as typeof import('koffi');
  const dll = koffi.load(dllPath!);
  function tryFunc(name: string, ret: string, params: string[] = []) {
    try { return dll.func(name, ret, params); } catch { return null; }
  }
  return {
    read: dll.func('ime_caps_read', 'int', []),
    toggle: dll.func('ime_caps_toggle', 'int', []),
    set: dll.func('ime_caps_set', 'int', ['int']),
    foregroundWindow: tryFunc('ime_foreground_window', 'intptr', []),
    conversionStatus: tryFunc('ime_get_conversion_status', 'int64', []),
    setAsciiMode: tryFunc('ime_set_ascii_mode', 'int', ['int']),
    composing: tryFunc('ime_is_composing', 'int', []),
  };
}

export function initNative(extensionPath: string): void {
  const candidates = [
    path.join(extensionPath, 'bin', 'ime_sys.dll'),
    path.join(extensionPath, '..', 'ime-sys', 'target', 'x86_64-pc-windows-gnu', 'release', 'ime_sys.dll'),
  ];
  for (const p of candidates) {
    if (fs.existsSync(p)) {
      dllPath = p;
      break;
    }
  }
  if (!dllPath) return;
  try {
    lib = loadLib();
  } catch {
    lib = null;
  }
}

export function isNativeAvailable(): boolean {
  return lib !== null;
}

export function nativeCapsRead(): boolean {
  return lib !== null && lib.read() !== 0;
}

export function nativeCapsToggle(): boolean {
  return lib !== null && lib.toggle() !== 0;
}

export function nativeCapsSet(on: boolean): boolean {
  return lib !== null && lib.set(on ? 1 : 0) !== 0;
}

export function nativeForegroundWindow(): bigint {
  const value = lib?.foregroundWindow?.();
  return typeof value === 'bigint' ? value : BigInt(value ?? 0);
}

export function nativeIsComposing(): number {
  return lib?.composing?.() ?? -1;
}

export interface SystemImeStatus {
  isOpen: boolean;
  isAsciiMode: boolean;
  conversionMode: number;
}

export function nativeSystemImeStatus(): SystemImeStatus | null {
  const value = lib?.conversionStatus?.();
  if (value === null || value === undefined) return null;

  return decodeSystemImeStatus(typeof value === 'bigint' ? value : BigInt(value));
}

export function nativeSetAsciiMode(ascii: boolean): boolean {
  return (lib?.setAsciiMode?.(ascii ? 1 : 0) ?? 0) !== 0;
}

export function decodeSystemImeStatus(packed: bigint): SystemImeStatus | null {
  if (packed < 0n) return null;
  const conversionMode = Number(packed & 0xffff_ffffn);
  const isOpen = (packed & (1n << 32n)) !== 0n;
  return {
    isOpen,
    isAsciiMode: !isOpen || (conversionMode & 0x01) === 0,
    conversionMode,
  };
}
