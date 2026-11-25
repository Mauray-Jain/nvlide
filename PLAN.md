# nvlide — plan

Per-app volume sliders for Nothing phones. The stock Nothing OS volume panel stays as it is; nvlide adds a
small pill beside it that expands into **one slider per app**, drawn in the same style. Each app keeps its
level after it is closed. No root: privileged calls go through [Shizuku](https://shizuku.rikka.app/).

Status: **M1–M5 code written, not yet checked on a device.** M0 (the go/no-go test matrix) is the next step,
run on the phone with the live card in the setup screen.

---

## 1. Goals

1. On a volume key press, a small nvlide pill appears next to the stock panel.
2. Tapping the pill slides out a card with one slider per app that has an open audio player.
3. Each app's level is saved by package name and re-applied whenever that app plays again, even after the
   app or the phone restarts.
4. Works without root, using Shizuku (wireless debugging on Android 11+).

Out of scope: replacing the system panel, consuming or remapping volume keys, boosting above 100 %,
per-app EQ, controlling calls/alarms per app, work profile apps, root backend.

## 2. Decisions

| Topic | Decision |
| --- | --- |
| Panel | **Beside, not replace.** nvlide never consumes volume key events; the stock panel and all system key behaviour (calls, ringing, alarms, camera shutter, cast, Bluetooth, screenshot chord) stay untouched. |
| Expand | Key press → small pill (`♫ 3` = apps playing now) next to the stock panel. Tap → app card slides out. No pill when no app has an open player. |
| Which apps | Every app with **at least one unreleased audio player**, ordered: playing → stopped < 10 s ago → paused/idle. An app drops out when its last player is released (closed, swiped from recents, killed). Players are mapped to apps by **pid → running process** (handles shared uids, skips system processes); nvlide itself is skipped. Same model as the Windows mixer and VolumeManager. |
| Memory | Level + mute saved per package, forever. Re-applied to every new player of a package whose level ≠ 100 % or is muted. Dragging a slider applies immediately. |
| Keys | Stock behaviour only. |
| Timeout | Card stays 3 s after the last touch, even if the stock panel already hid; tap outside closes it. |
| Lock screen | Pill shows on the lock screen like the stock panel; sliders work without unlocking. |
| Layout | **Responsive.** Pill and card follow the stock panel in portrait and landscape: separate offset per orientation in settings, card height scales with the screen (shorter sliders in landscape), horizontal scroll when apps don't fit, recomputed on configuration change. |
| Shizuku down | Every key press shows the pill in a "start Shizuku" state; tap opens Shizuku. |
| Known limits | Screenshots may include the pill/card. Players that ignore the proxy volume won't respond. |
| Package | `com.nvlide`. |
| Player → app | The privileged service maps pid → package (it is the only side that can list every running process) and sends `(package, piid, playing)` per player. Players with uid < 10000 or an unknown pid are dropped there. |
| Player list | Public `AudioManager.registerAudioPlaybackCallback` inside the shell-uid service (needs the Shizuku app v13+, which passes a `Context` to the user service); only the `@SystemApi` getters and `PlayerProxy.setVolume` use reflection. |
| HiddenApiBypass | Not added: the Shizuku user service runs under `app_process`, where hidden-API checks are off. Add it only if M0 shows reflection blocked. |
| Pill state | Shizuku stopped → "Start Shizuku" (opens Shizuku). Permission missing → "Set up nvlide" (opens the setup screen). Still connecting → no pill. Pill shows the number of apps playing now. The overlay follows Shizuku and player changes while shown: the open card closes when the last app goes away, and turns back into the hint pill if Shizuku stops. |
| Pill offset | Per orientation, in dp: x from the right edge, y from the vertical centre. Defaults 88/−160 portrait, 88/−60 landscape; set in the setup screen, which shows the pill live while the sliders move. |
| Card size | Slider height = 28 % of screen height in portrait, 45 % in landscape, clamped to 120–280 dp; width capped to the screen. |
| Back to 100 % | Moving an adjusted app back to 100 % still sends `setVolume(1.0)`; new players of an app at 100 % unmuted get no call. |
| Idle order | Inside a group (playing / recent / idle) apps keep the order of the player list. |
| M0 spike | The setup screen shows the live app card while Shizuku is connected, so the test matrix can be run without the overlay. |
| Release | Minify off (reflection + Shizuku user service by class name). CI builds a debug APK on every push; a `v*` tag also builds the release APK, signed when the `NVLIDE_KEYSTORE_*` secrets are set. |

## 3. Target UI

```
 stock panel        nvlide
 ( 🔊 )
 ┌───┐              ┌──────────────────────────┐
 │ · │   ( ♫ 3 ) →  │  ┌───┐  ┌───┐  ┌───┐     │
 │ █ │   tap        │  │ · │  │ █ │  │ · │     │
 │ ● │              │  │ █ │  │ █ │  │ ● │     │
 │ ♪ │              │  │ ▶ │  │ ◎ │  │ ✈ │     │ ← monochrome app icons
 │ › │              │  └───┘  └───┘  └───┘     │
 └───┘              └──────────────────────────┘
```

Visual tokens (match the Nothing OS panel in the screenshots):

| Token | Value |
| --- | --- |
| Card surface | `#1C1C1E`, 28 dp corner radius |
| Slider track | 40 dp pill, `#2A2A2C` |
| Filled level | `#F2F2F2` |
| Empty part | dotted column, 2 dp dots `#4A4A4C`, 10 dp pitch |
| Zero handle | 12 dp off-white dot |
| Muted app | fill → Nothing red `#D71921`, icon dimmed; tap icon toggles mute |
| Paused app | slider at 60 % opacity |
| Motion | 220 ms slide-out; haptic tick every 10 % |

## 4. Architecture

Kotlin, plain Android Views, `minSdk 31`, `compileSdk`/`targetSdk 35`.

```
app process                                          Shizuku user service (shell uid)
┌─────────────────────────────────────────┐          ┌────────────────────────────────────┐
│ VolumeKeyService (AccessibilityService) │          │ PrivilegedAudioService             │
│   onKeyEvent → show pill, return false  │          │   IAudioService via reflection     │
│   TYPE_ACCESSIBILITY_OVERLAY window     │   AIDL   │   • playback callback → push list  │
│ AppVolumeController                     │◄────────►│   • PlayerProxy.setVolume(f)       │
│ AppVolumeStore (SharedPreferences)      │          └────────────────────────────────────┘
└─────────────────────────────────────────┘
```

Start with the fewest pieces that work; split only when a file gets hard to read.

| Piece | Responsibility |
| --- | --- |
| `NvlideApp` | Creates the one store, backend and controller; re-applies on audio mode change. |
| `VolumeKeyService` | Sees volume keys (never consumes them), forwards key presses and rotation to `ui/Overlay`. |
| `ui/` | `Overlay` (window, timeout, outside tap, rotation), pill, app card, slider. |
| `AppVolumeController` | Joins open players + saved levels, orders apps, applies levels. |
| `AppVolumeStore` | Package → level/mute, in SharedPreferences. |
| `OverlayPrefs` | Pill offset per orientation. |
| `backend/` | Shizuku binding + the privileged service + AIDL. All hidden-API reflection lives only here. |
| `MainActivity` | Setup checklist (Shizuku, permission, accessibility, battery optimisation) and settings (offsets). |

### How the volume is applied

AOSP keeps the volume set through `PlayerProxy.setVolume` in a separate multiplier inside each player
(`PlayerBase.mVolMultiplier`), multiplied with the app's own volume. The app's own `setVolume`, fades, ducking,
pan and start/pause/stop don't touch it, so **no polling is needed**. It is lost only in the cases below, and
each one has an event:

1. Shell process gets playback config changes → pushes `(pid, uid, piid, state)` list to the app.
2. The shell service maps pid → package; the controller groups players by package and updates the card.
3. For packages with a saved level ≠ 100 % or muted → `setVolume` on every player of that package.
4. **New player instance** (next track, app restarted, after reboot) starts at 100 % → step 1 fires → re-applied.
5. **System resets player volume** (e.g. other apps muted during a phone call, reset to 100 % after) → re-apply
   all adjusted packages when audio mode returns to normal (`AudioManager.addOnModeChangedListener`).
6. **Shizuku service reconnects** → re-apply all adjusted packages.

## 5. Risks

| Risk | Check |
| --- | --- |
| Shell uid gets anonymized `AudioPlaybackConfiguration` (no pid/uid / proxy) on Nothing OS | M0 spike |
| Some players ignore `PlayerProxy.setVolume` (AAudio MMAP, OpenSL, some games) | M0 test matrix: Spotify, YouTube, Chrome, Instagram, WhatsApp, one game, PPSSPP |
| System resets player volume (calls, possibly other cases) | M0: level survives a phone call; re-apply on mode change |
| Hidden-API restrictions in the user-service process | Add `HiddenApiBypass` only if needed |
| Shizuku not running after reboot | "Start Shizuku" pill state |
| Android 13+ "restricted settings" for sideloaded accessibility services | Setup screen explains how to allow it |
| Service killed by battery optimisation | Setup asks for the exemption |

## 6. Milestones

- [ ] **M0 — Feasibility spike (go/no-go).** Shizuku user service lists open players with pid/package; one slider
      calls `setVolume`. Run the test matrix on the phone and check the level survives: pause/resume, next track,
      app restart, a phone call, Shizuku restart. **No-go:** if shell can't see pid/uid or the player proxy, or
      `setVolume` doesn't stick on most of the matrix, stop and re-plan.
- [x] **M1 — Skeleton.** Gradle, Shizuku, AIDL, unit-test setup.
- [x] **M2 — Backend + memory.** Playback callback, setVolume, store, re-apply on new players / mode change /
      reconnect.
- [x] **M3 — Overlay.** Accessibility service, pill beside the stock panel, timeout, lock screen, per-orientation
      offset setting.
- [x] **M4 — App card.** Sliders, icons, mute, ordering, paused state, responsive sizing, animation, haptics.
- [x] **M5 — Setup & polish.** Setup checklist, TalkBack, battery check, CI debug APK, release APK.

M1–M5 are written and unit-tested where possible; everything device-side still needs the M0 run and the
manual checks in section 7.

## 7. Testing

Tests are written before the implementation (see CLAUDE.md).

- Unit: controller with a fake backend (only adjusted apps touched, mute = 0, re-apply on new player / mode change
  / reconnect, ordering playing → recent → idle, uninstalled package skipped).
- Manual on device: the full checklist is in [TESTING.md](TESTING.md) (M0 test matrix first, then memory,
  untouched key behaviour, pill, card, TalkBack).
