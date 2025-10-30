import type { TurboModule } from 'react-native'
import { TurboModuleRegistry } from 'react-native'

export type Config = {
  duration?: number // default 200 milliseconds (0.2 seconds)
  blurRadius?: number // default 20.0
}

export interface Spec extends TurboModule {
  configure(config: Config): void
  enable(): void
  disable(): void
  isEnabled(): Promise<boolean>
  showNow(): void
  hideNow(): void
}

export default TurboModuleRegistry.getEnforcing<Spec>('PrivacyBlur')
