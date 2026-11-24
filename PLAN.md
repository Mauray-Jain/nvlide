# nvlide — plan

Per-app volume sliders for Nothing phones. The stock Nothing OS volume panel stays as it is; nvlide adds a
small pill beside it that expands into **one slider per app**, drawn in the same style. Each app keeps its
level after it is closed. No root: privileged calls go through [Shizuku](https://shizuku.rikka.app/).

Status: **planning only, no code yet.**

---

## 1. Goals

1. On a volume key press, a small nvlide pill appears next to the stock panel (only if some app has audio).
2. Tapping the pill slides out a card with one slider per app that has an open audio player.
3. Each app's level is saved by package name and re-applied whenever that app plays again, even after the
   app or the phone restarts.
4. Works without root, using Shizuku (wireless debugging on Android 11+).

Out of scope: replacing the system panel, consuming or remapping volume keys, boosting above 100 %,
per-app EQ, controlling calls/alarms per app, root backend.

## 2. Decisions (from design grilling)

| # | Decision |
| --- | --- |
| Panel | **Beside, not replace.** nvlide never consumes volume keys; the stock panel and all system key behaviour (calls, ringing, alarms, camera shutter, cast, Bluetooth, screenshot chord) stay untouched. |
| Expand | Key press → small pill (`♫ 3` = apps with audio) next to the stock panel. Tap → app card slides out. No pill when no app has an open player. |
| Which apps | Apps with **at least one unreleased audio player** (playing *or* paused). Playing apps first. An app drops out when its last player is released (closed, swiped from recents, killed). No timer. Same model as the Windows mixer and Shizuku apps like VolumeManager. |
| Memory | Level + mute saved per package, forever. Re-applied to every new player of that package. |
| Keys | Stock behaviour only. (A "keys control foreground app" mode is impossible without consuming keys, so dropped.) |
| Timeout | Card stays 3 s after the last touch, even if the stock panel already hid; tap outside closes it. |
| Position | Pill/card offset from the stock panel is adjustable in settings (stock panel size can't be read). |
| Known limits | Screenshots may include the pill/card. Apps whose players ignore the proxy volume won't respond. |

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

```
app process                                          Shizuku user service (shell uid)
┌─────────────────────────────────────────┐          ┌────────────────────────────────────┐
│ VolumeKeyService (AccessibilityService) │          │ PrivilegedAudioService             │
│   onKeyEvent → show pill, return false  │          │   IAudioService via reflection     │
│   TYPE_ACCESSIBILITY_OVERLAY window     │   AIDL   │   • playback callback → push list  │
│ AppVolumeController                     │◄────────►│   • PlayerProxy.setVolume(f)       │
│ AppVolumeStore (DataStore)              │          └────────────────────────────────────┘
└─────────────────────────────────────────┘
```

Start with the fewest pieces that work; split only when a file gets hard to read.

| Piece | Responsibility |
| --- | --- |
| `VolumeKeyService` | Sees volume keys (never consumes them), owns the overlay, timeout. |
| `ui/` | Compose: pill, app card, slider. |
| `AppVolumeController` | Joins open players + saved levels, applies levels. |
| `AppVolumeStore` | Package → level/mute. |
| `backend/` | Shizuku binding + the privileged service + AIDL. All hidden-API reflection lives only here. |
| `MainActivity` | Setup checklist: Shizuku, permission, accessibility, battery optimisation. |

### Per-app volume flow

1. Shell process gets playback config changes → pushes `(uid, piid, state)` list to the app.
2. Controller maps uid → package, groups players, updates the card.
3. For packages with a saved level ≠ 100 % or muted → `setVolume(uid, level)` on every player.
4. A new player (next track, app restarted, after reboot) fires step 1 again → level re-applied.

## 5. Risks

| Risk | Check |
| --- | --- |
| Shell uid gets anonymized `AudioPlaybackConfiguration` (no uid / proxy) on Nothing OS | M0 spike |
| Some players ignore `PlayerProxy.setVolume` (AAudio MMAP, some games) | M0 test matrix: Spotify, YouTube, Chrome, Instagram, WhatsApp, one game, PPSSPP |
| Hidden-API restrictions in the user-service process | Add `HiddenApiBypass` only if needed |
| Shizuku not running after reboot | Pill shows a "start Shizuku" state; tap opens Shizuku |
| Android 13+ "restricted settings" for sideloaded accessibility services | Setup screen explains how to allow it |
| Service killed by battery optimisation | Setup asks for the exemption |

## 6. Milestones

- [ ] **M0 — Feasibility spike (go/no-go).** Shizuku user service lists open players with uid/package; one slider
      calls `setVolume`. Run the test matrix on the phone.
- [ ] **M1 — Skeleton.** Gradle, Compose, Shizuku, AIDL, CI debug APK.
- [ ] **M2 — Backend + memory.** Playback callback, setVolume, store, re-apply on new players.
- [ ] **M3 — Overlay.** Accessibility service, pill beside stock panel, timeout, offset setting.
- [ ] **M4 — App card.** Sliders, icons, mute, paused state, animation, haptics.
- [ ] **M5 — Setup & polish.** Setup checklist, TalkBack, battery check, release APK.

## 7. Testing

- Unit: controller with a fake backend (only adjusted apps touched, mute = 0, re-apply on new player,
  uninstalled package skipped).
- Manual on device: test matrix, reboot + Shizuku restart, force-stop then replay keeps level.
