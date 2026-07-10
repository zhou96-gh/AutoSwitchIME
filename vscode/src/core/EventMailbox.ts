export class EventMailbox<T> {
  private queue: T[] = [];
  private draining = false;
  private idleResolvers: Array<() => void> = [];

  constructor(
    private readonly handle: (event: T) => Promise<void>,
    private readonly onError: (error: Error) => void = () => {},
  ) {}

  post(event: T, replacePending?: (pending: T) => boolean): void {
    if (replacePending) {
      this.queue = this.queue.filter((pending) => !replacePending(pending));
    }
    this.queue.push(event);
    this.startDrain();
  }

  waitForIdle(): Promise<void> {
    if (!this.draining && this.queue.length === 0) {
      return Promise.resolve();
    }
    return new Promise((resolve) => this.idleResolvers.push(resolve));
  }

  clear(predicate: (pending: T) => boolean): void {
    this.queue = this.queue.filter((pending) => !predicate(pending));
  }

  private startDrain(): void {
    if (this.draining) return;
    this.draining = true;
    void this.drain();
  }

  private async drain(): Promise<void> {
    while (this.queue.length > 0) {
      const event = this.queue.shift()!;
      try {
        await this.handle(event);
      } catch (error) {
        this.onError(error instanceof Error ? error : new Error(String(error)));
      }
    }

    this.draining = false;
    const resolvers = this.idleResolvers;
    this.idleResolvers = [];
    for (const resolve of resolvers) resolve();
  }
}
