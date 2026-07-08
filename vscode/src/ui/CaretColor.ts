import * as vscode from 'vscode';
import { ImeAction } from '../core/types';

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
  private currentAction: ImeAction = ImeAction.ENGLISH;
  private originalCursorColor: string | undefined;

  constructor(
    private chineseColor: string = '#00FF00',
    private englishColor: string = '#FFFFFF',
    private capsColor: string = '#FFFF00',
  ) {
    const all = readColorCustomizations();
    this.originalCursorColor = all[CURSOR_FG] as string | undefined;
  }

  async updateCaretColor(action: ImeAction): Promise<void> {
    if (action === this.currentAction) return;
    this.currentAction = action;

    const all = readColorCustomizations();
    const color = this.colorFor(action);
    all[CURSOR_FG] = color;
    all[TERMINAL_CURSOR_FG] = color;
    await writeColorCustomizations(all);
  }

  async dispose(): Promise<void> {
    const all = readColorCustomizations();
    if (this.originalCursorColor) {
      all[CURSOR_FG] = this.originalCursorColor;
      all[TERMINAL_CURSOR_FG] = this.originalCursorColor;
    } else {
      delete all[CURSOR_FG];
      delete all[TERMINAL_CURSOR_FG];
    }
    await writeColorCustomizations(all);
  }

  private colorFor(action: ImeAction): string {
    switch (action) {
      case ImeAction.CHINESE:
        return this.chineseColor;
      case ImeAction.CAPS:
        return this.capsColor;
      default:
        return this.englishColor;
    }
  }
}
