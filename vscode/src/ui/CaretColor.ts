import * as vscode from 'vscode';
import { ImeState, InputDisplayMode, inputDisplayModeFor } from '../core/types';

const WORKBENCH = 'workbench';
const CUSTOMIZATIONS = 'colorCustomizations';
const CURSOR_FG = 'editorCursor.foreground';
const TERMINAL_CURSOR_FG = 'terminalCursor.foreground';

function readColorCustomizations(): Record<string, unknown> {
  return {
    ...(vscode.workspace
      .getConfiguration(WORKBENCH)
      .get<Record<string, unknown>>(CUSTOMIZATIONS) ?? {}),
  };
}

async function writeColorCustomizations(
  obj: Record<string, unknown>,
): Promise<void> {
  await vscode.workspace
    .getConfiguration(WORKBENCH)
    .update(
      CUSTOMIZATIONS,
      Object.keys(obj).length > 0 ? obj : undefined,
      vscode.ConfigurationTarget.Global,
    );
}

export class CaretColorManager implements vscode.Disposable {
  private currentMode: InputDisplayMode | null = null;
  private originalCursorColor: string | undefined;

  constructor(
    private chineseColor: string = '#00CC66',
    private englishColor: string = '#FFFFFF',
    private capsColor: string = '#FFCC00',
  ) {
    const all = readColorCustomizations();
    this.originalCursorColor = all[CURSOR_FG] as string | undefined;
  }

  async updateCaretColor(state: ImeState): Promise<void> {
    const mode = inputDisplayModeFor(state);
    if (mode === this.currentMode) return;
    this.currentMode = mode;

    const all = readColorCustomizations();
    const color = this.colorFor(mode);
    all[CURSOR_FG] = color;
    all[TERMINAL_CURSOR_FG] = color;
    await writeColorCustomizations(all);
  }

  async restoreCaretColor(): Promise<void> {
    if (this.currentMode === null) return;
    this.currentMode = null;
    const all = readColorCustomizations();
    this.restoreOriginalColors(all);
    await writeColorCustomizations(all);
  }

  async dispose(): Promise<void> {
    const all = readColorCustomizations();
    this.restoreOriginalColors(all);
    await writeColorCustomizations(all);
  }

  private restoreOriginalColors(all: Record<string, unknown>): void {
    if (this.originalCursorColor) {
      all[CURSOR_FG] = this.originalCursorColor;
      all[TERMINAL_CURSOR_FG] = this.originalCursorColor;
    } else {
      delete all[CURSOR_FG];
      delete all[TERMINAL_CURSOR_FG];
    }
  }

  private colorFor(mode: InputDisplayMode): string {
    switch (mode) {
      case InputDisplayMode.CHINESE:
        return this.chineseColor;
      case InputDisplayMode.CAPS:
        return this.capsColor;
      default:
        return this.englishColor;
    }
  }
}
