export interface CoordinatorRequest<T> {
  editorId: T;
  generation: number;
}

export class CoordinatorState<T> {
  private activeEditorId: T | null = null;
  private windowFocused = false;
  private enabled = true;
  private generation = 0;
  private shuttingDown = false;

  focusEditor(editorId: T): void {
    if (this.shuttingDown) return;
    if (this.windowFocused && this.activeEditorId === editorId) return;
    this.activeEditorId = editorId;
    this.windowFocused = true;
    this.generation += 1;
  }

  loseFocus(editorId?: T): void {
    if (editorId !== undefined && this.activeEditorId !== editorId) return;
    this.activeEditorId = null;
    this.windowFocused = false;
    this.generation += 1;
  }

  setEnabled(value: boolean): void {
    if (this.enabled === value) return;
    this.enabled = value;
    if (!value) {
      this.activeEditorId = null;
    }
    this.generation += 1;
  }

  newRequest(editorId: T): CoordinatorRequest<T> | null {
    if (
      this.shuttingDown ||
      !this.enabled ||
      !this.windowFocused ||
      this.activeEditorId !== editorId
    ) {
      return null;
    }
    this.generation += 1;
    return { editorId, generation: this.generation };
  }

  invalidateRequests(): void {
    this.generation += 1;
  }

  isCurrent(request: CoordinatorRequest<T>): boolean {
    return (
      !this.shuttingDown &&
      this.enabled &&
      this.windowFocused &&
      this.activeEditorId === request.editorId &&
      this.generation === request.generation
    );
  }

  shutdown(): void {
    if (this.shuttingDown) return;
    this.shuttingDown = true;
    this.activeEditorId = null;
    this.windowFocused = false;
    this.generation += 1;
  }

  isShuttingDown(): boolean {
    return this.shuttingDown;
  }
}
