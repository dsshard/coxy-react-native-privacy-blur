import NativePrivacyBlur from './NativePrivacyBlur'
import type { Config } from './NativePrivacyBlurSpec'

export type { Config }

export const configure = (config: Config) => NativePrivacyBlur.configure(config)
export const enable = () => NativePrivacyBlur.enable()
export const disable = () => NativePrivacyBlur.disable()
export const isEnabled = () => NativePrivacyBlur.isEnabled()
export const show = () => NativePrivacyBlur.showNow()
export const hide = () => NativePrivacyBlur.hideNow()
