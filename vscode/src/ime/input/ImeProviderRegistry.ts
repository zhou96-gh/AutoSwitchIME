import { ImeConfig, ImeProvider, ImeType } from '../../core/types';

type ProviderFactory = (config: ImeConfig) => ImeProvider;

export class ImeProviderRegistry {
  private readonly providers = new Map<ImeType, ProviderFactory>();

  register(type: ImeType, factory: ProviderFactory): void {
    this.providers.set(type, factory);
  }

  create(config: ImeConfig): ImeProvider {
    const factory = this.providers.get(config.type);
    if (!factory) {
      throw new Error(`IME provider not found for type: ${config.type}`);
    }
    return factory(config);
  }

  supportedTypes(): ImeType[] {
    return [...this.providers.keys()];
  }
}
