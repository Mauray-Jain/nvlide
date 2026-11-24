# nvlide — plan

Per-app volume sliders for Nothing phones. The stock Nothing OS volume panel stays as it is; nvlide adds a
small pill beside it that expands into **one slider per app**, drawn in the same style. Each app keeps its
level after it is closed. No root: privileged calls go through [Shizuku](https://shizuku.rikka.app/).

Status: **planning only, no code yet.**

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
| `VolumeKeyService` | Sees volume keys (never consumes them), owns the overlay, timeout, orientation changes. |
| `ui/` | Views: pill, app card, slider. |
| `AppVolumeController` | Joins open players + saved levels, orders apps, applies levels. |
| `AppVolumeStore` | Package → level/mute, in SharedPreferences. |
| `backend/` | Shizuku binding + the privileged service + AIDL. All hidden-API reflection lives only here. |
| `MainActivity` | Setup checklist (Shizuku, permission, accessibility, battery optimisation) and settings (offsets). |

### How the volume is applied

AOSP keeps the volume set through `PlayerProxy.setVolume` in a separate multiplier inside each player
(`PlayerBase.mVolMultiplier`), multiplied with the app's own volume. The app's own `setVolume`, fades, ducking,
pan and start/pause/stop don't touch it, so **no polling is needed**. It is lost only in the cases below, and
each one has an event:

1. Shell process gets playback config changes → pushes `(pid, uid, piid, state)` list to the app.
2. Controller maps pid → package, groups players, updates the card.
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
- [ ] **M1 — Skeleton.** Gradle, Shizuku, AIDL, unit-test setup.
- [ ] **M2 — Backend + memory.** Playback callback, setVolume, store, re-apply on new players / mode change /
      reconnect.
- [ ] **M3 — Overlay.** Accessibility service, pill beside the stock panel, timeout, lock screen, per-orientation
      offset setting.
- [ ] **M4 — App card.** Sliders, icons, mute, ordering, paused state, responsive sizing, animation, haptics.
- [ ] **M5 — Setup & polish.** Setup checklist, TalkBack, battery check, CI debug APK, release APK.

## 7. Testing

Tests are written before the implementation (see CLAUDE.md).

- Unit: controller with a fake backend (only adjusted apps touched, mute = 0, re-apply on new player / mode change
  / reconnect, ordering playing → recent → idle, uninstalled package skipped).
- Manual on device: test matrix, reboot + Shizuku restart, force-stop then replay keeps level, rotate while the
  card is open.
