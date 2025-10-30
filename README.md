# @coxy/react-native-privacy-blur

A React Native plugin that renders a safe, native blur overlay on top of your entire app. It is built for privacy: keep sensitive content hidden in the app switcher and during transitions.

Important: implemented natively for iOS and Android using TurboModule API. The design prioritizes safety and minimal interference with your UI.

## Features
- Simple, fast blur overlay on top of the screen.
- Programmatic enable/disable.
- Immediate show/hide on demand.
- Configurable animation duration and blur radius.

## Requirements
- React >= 18
- React Native >= 0.73
- iOS 13+
- Android 6.0 (API 23)+

## Installation

```bash
# choose your package manager
npm install @coxy/react-native-privacy-blur
# or
yarn add @coxy/react-native-privacy-blur
# or
pnpm add @coxy/react-native-privacy-blur
```

Autolinking is supported.

### iOS
1. Consider enabling `use_frameworks!` (optional, but common for modern RN setups).
2. Install pods:
   ```bash
   cd ios && pod install && cd ..
   ```

### Android
Autolinking will add the module automatically. No extra steps are usually required.

## Quick start

```ts
// App.tsx
import React, { useEffect } from 'react'
import { AppState, AppStateStatus } from 'react-native'
import {
  configure,
  enable,
  disable,
  show,
  hide,
  type Config,
} from '@coxy/react-native-privacy-blur'

export default function App() {
  useEffect(() => {
    const cfg: Config = {
      duration: 200,   // animation duration in ms
      blurRadius: 20,  // blur radius (platform-specific units)
    }
    configure(cfg)
    enable() // Safety: ensure privacy overlay functions are available

    // Recommended: protect previews in app switcher automatically
    const sub = AppState.addEventListener('change', (state: AppStateStatus) => {
      // Safety: keep UI private in app switcher and during backgrounding
      if (state !== 'active') show()
      else hide()
    })

    return () => {
      sub.remove()
      // Safety: optionally disable to avoid unexpected behavior
      disable()
    }
  }, [])

  return (
    // ... your UI
    null
  )
}
```

## API
All methods are strictly typed.

```ts
import {
  configure,
  enable,
  disable,
  isEnabled,
  show,
  hide,
  type Config,
} from '@coxy/react-native-privacy-blur'
```

- configure(config: Config): void
  - duration?: number — animation duration (ms). Default 200.
  - blurRadius?: number — blur radius. Default 20.0.
- enable(): void — enables the module.
- disable(): void — disables the module; hides the overlay if it is visible.
- isEnabled(): Promise<boolean> — returns whether the module is enabled.
- show(): void — show the blur overlay immediately.
- hide(): void — hide the blur overlay immediately.

Example state check:
```ts
const active: boolean = await isEnabled()
```

## Defaults
- Module is disabled until you call `enable()` (fail‑safe by default).
- You fully control when to show/hide via `show()`/`hide()`; see the Quick Start for an automatic AppState pattern.

## Security notes
- Do not rely solely on blur to protect data: encrypt sensitive information and avoid logging secrets.
- Use the minimal sufficient blur radius and reasonable animations to reduce recognizable UI shapes in previews.
- Android: OS may cache previews; the module draws an overlay on the Window to hide content. Avoid conflicts with other overlays.
- iOS: a native view is added above the keyWindow; avoid long main-thread blocks so animations apply in time.
- Always test on real devices, including multitasking and rapid app switching.

## Platform details (high level)
- iOS: a native blurred view is attached above the keyWindow and animated via `show`/`hide`.
- Android: an overlay View is attached to the Activity Window with a fast native blur. No screen snapshots are taken by the module.

// Note: Implementation details above are intentionally concise for safety and may evolve.

## Performance
- Blur radius 10–25 usually balances privacy and smoothness well.
- Duration 150–250 ms rarely harms UX and reduces flicker.
- On older Android devices, large radii can be expensive — test and tune.

## Testing
- Test transitions: Active -> Inactive -> Background -> Active.
- Verify rapid switching and app switcher screenshots.
- Run regression tests across multiple Android/iOS versions.

## FAQ
- Do I need to change manifests? Usually no. Autolinking adds what's needed.
- Conflicts with secure-screen libraries? Possibly. If you use `FLAG_SECURE` or similar, test composition and UX.
- Can I show a custom screen instead of blur? Not in the current version; the module focuses on a simple and safe blur overlay.

## License
MIT

## Support
Please open Issues for problems/ideas. Include device, OS/RN versions, and a short video/screencast when possible.
