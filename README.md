# nvlide

Per-app volume sliders for Nothing phones, in the Nothing OS volume panel style.

Press a volume key → the stock panel shows as usual, with a small nvlide pill beside it. Tap the pill → one
slider for every app that has audio open. Levels are remembered per app and re-applied every time it plays
again, even after you close it or reboot. nvlide never takes over the volume keys.

Runs without root via [Shizuku](https://shizuku.rikka.app/).

> **Status: first version, not yet tested on a device.** See [PLAN.md](PLAN.md) for the design, decisions and
> milestones.

## Build

```bash
./gradlew testDebugUnitTest assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Install it, open nvlide and follow the setup checklist
(Shizuku running, Shizuku permission, accessibility service, battery optimisation off).

Inspired by [KrishBnsl/ntvslide](https://github.com/KrishBnsl/ntvslide).
