# nvlide — plan

A replacement volume panel for Nothing phones, in the Nothing OS style. It adds **per-app volume
sliders** behind an expand gesture, and an app keeps its level after it is closed. No root: privileged
calls go through [Shizuku](https://shizuku.rikka.app/).

Status: **planning only, no code yet.**

---

## 1. Goals

1. Volume key opens an nvlide panel that looks like the stock Nothing OS one (collapsed pill → expanded streams).
2. Expanding further shows one slider per app that is currently playing audio, with the app's icon.
3. Each app's level is saved by package name and re-applied whenever that app plays again, even after a
   restart of the app or the phone.
4. Works without root, using Shizuku (wireless debugging on Android 11+).

Out of scope: boosting above 100 %, per-app EQ, controlling calls/alarms per app, root backend (maybe later).

## 2. Target UI (from Nothing OS screenshots)

**State A — collapsed** (what a volume key press shows)

```
 ( 🔊 )            ← round ringer-mode button, cycles sound/vibrate/silent
 ┌───┐
 │ · │             ← dotted track = empty part
 │ · │
 │ █ │             ← solid off-white fill = current level
 │ ● │             ← dot handle when at 0
 │ ♪ │             ← stream icon (media)
 │ › │             ← expand
 └───┘
```

**State B — streams** (tap `›`): ringer row (mute / vibrate / sound) + four sliders: media, call, ring, alarm.
Footer button `APPS` (where stock shows `SEE MORE`).

**State C — apps** (tap `APPS`, or swipe the panel sideways): an extra card slides out next to State B with
one slider per playing app; the app's monochrome icon sits under each slider where the stream icon would be.
Horizontal scroll if more than four apps. A small `⚙` opens the system sound settings.

Visual tokens:

| Token | Value |
| --- | --- |
| Panel surface | `#1C1C1E`, 28 dp corner radius, no elevation shadow |
| Slider track | 40 dp wide pill, `#2A2A2C` |
| Filled level | `#F2F2F2`, same pill radius |
| Empty part | column of 2 dp dots `#4A4A4C`, 10 dp pitch (the "dotted" look) |
| Zero handle | 12 dp off-white dot at the bottom |
| Muted app | fill turns Nothing red `#D71921`, icon dimmed |
| Icons | Material Symbols Rounded / monochrome app icons, `#BDBDBD` |
| Text | `NDot 57` for numbers/labels, falls back to system font |
| Motion | 220 ms emphasized-decelerate for expand; panel fades after 3 s idle |
| Haptics | `CLOCK_TICK` every 10 %, `CONFIRM` on mute toggle |

Position: left edge, vertically centered as on stock (setting for right edge).

## 3. Architecture

```
app process (normal uid)                                Shizuku user service (shell uid)
┌──────────────────────────────────────────┐           ┌──────────────────────────────────────┐
│ VolumeKeyService  (AccessibilityService)  │           │ PrivilegedAudioService               │
│   onKeyEvent → consume vol keys           │           │   IAudioService (hidden, reflection) │
│   overlay = TYPE_ACCESSIBILITY_OVERLAY    │           │   • getActivePlaybackConfigurations  │
│        │                                  │   AIDL    │   • registerPlaybackCallback  ──────┐│
│ PanelController (Compose UI state)        │◄─────────►│   • PlayerProxy.setVolume(f)        ││
│        │                                  │           │   pushes onPlayersChanged(list) ◄───┘│
│ AppVolumeController                       │           └──────────────────────────────────────┘
│   ├ StreamVolume  (public AudioManager)   │
│   ├ AppVolumeBackend (interface)          │
│   │    └ ShizukuBackend                   │
│   └ AppVolumeRepository (Room)            │
└──────────────────────────────────────────┘
```

### Modules / packages

| Path | Responsibility |
| --- | --- |
| `service/VolumeKeyService.kt` | Accessibility service: filters volume keys, owns the overlay window, auto-hide timer. |
| `ui/panel/` | Compose: `CollapsedPanel`, `StreamsPanel`, `AppsPanel`, `PillSlider`, `RingerToggle`. |
| `ui/theme/NothingTokens.kt` | Colors, shapes, type, motion from the table above. |
| `audio/StreamVolume.kt` | Media/call/ring/alarm via `AudioManager` (public API), ringer mode. |
| `audio/AppVolumeController.kt` | Joins active players + saved levels; decides what to apply. |
| `backend/AppVolumeBackend.kt` | Interface: `players(): Flow<List<Player>>`, `setVolume(uid, v)`. |
| `backend/ShizukuBackend.kt` | Binds the Shizuku user service, reconnects, exposes state (`NotRunning / NoPermission / Ready`). |
| `backend/PrivilegedAudioService.kt` | Runs as shell; all hidden-API reflection lives here and only here. |
| `aidl/IPrivilegedAudio.aidl`, `IPlayersListener.aidl` | Binder contract between the two processes. |
| `data/AppVolumeRepository.kt` | Room table `app_volume(pkg PK, level Int, muted Bool, lastSeen Long)`. |
| `onboarding/` | Setup screen: Shizuku status, permission, accessibility toggle, "allow restricted settings" help. |

### Key decisions (differences from ntvslide)

| Topic | ntvslide | nvlide |
| --- | --- | --- |
| Panel | Extra dot-matrix panel *beside* the system panel; keys pass through | *Replaces* the system panel (keys consumed, volume changed via `adjustStreamVolume(…, 0)` so no system UI) so app sliders live inside the same expandable panel |
| Re-applying levels | Polls every 2 s | Event-driven: `AudioPlaybackCallback` registered in the shell process, pushed over AIDL; slow 10 s poll as fallback |
| Memory | SharedPreferences, last 8 apps | Room, unlimited, keyed by package; `lastSeen` for ordering |
| UI | Custom `View`s | Jetpack Compose inside the overlay (`ComposeView` + manual `Lifecycle/SavedState` owners) |
| uid → package | `getPackagesForUid().first()` | Same, but shared-uid apps grouped and labelled by the first launchable package |

Kept from ntvslide: Shizuku *user service* pattern, AIDL boundary, `AppVolumeBackend` interface, "only touch
apps the user adjusted", master × app = heard loudness, TalkBack range actions on sliders, a web preview for
quick UI iteration.

### Per-app volume flow

1. Shell process gets `onPlaybackConfigChanged(configs)` → maps to `(uid, piid, state)` → pushes to app.
2. `AppVolumeController` maps uid → package, looks up repository.
3. If the package has a saved level ≠ 100 % (or is muted), call `setVolume(uid, level)` → shell process calls
   `getPlayerProxy().setVolume(level)` on every player of that uid.
4. New player for the same app (next track, app restarted, after a phone reboot) → step 1 fires again → level re-applied.
   This is what "remembers after closing" means in practice.
5. System ducking/fades can overwrite the proxy volume; re-apply on every config change for adjusted apps.

### Key handling rules

- Screen off, in a call, or `AudioManager.mode != NORMAL` → don't consume; let the system handle it.
- Otherwise consume, adjust the stream the system would (media if anything is playing, else ring), show panel.
- Long press repeats are rate-limited to one step per 80 ms.
- If the accessibility service crashes, the system panel naturally comes back (keys aren't consumed).

## 4. Risks & how we check them

| Risk | Check |
| --- | --- |
| Shell uid can't see full `AudioPlaybackConfiguration` (uid / player proxy anonymized) on Nothing OS 3/4 | Phase 0 spike, logs on device |
| `PlayerProxy.setVolume` ignored by some players (AAudio MMAP, Oboe games, ExoPlayer resetting volume) | Test matrix in Phase 0: Spotify, YouTube, YT Music, Chrome, Instagram, WhatsApp voice note, one game, PPSSPP |
| Hidden-API blocklist in the user-service process | Use `org.lsposed.hiddenapibypass` if reflection fails |
| Shizuku stops after reboot | Detect, show a one-tap notification "Start Shizuku"; panel still works for streams without it |
| Android 13+ "restricted settings" blocks enabling accessibility for sideloaded apps | Onboarding explains App info → ⋮ → Allow restricted settings |
| Nothing OS overlay above our window (e.g. Glyph/notifications) | Accessibility overlay is above normal app overlays; verify |

## 5. Milestones

- [ ] **M0 — Feasibility spike (go/no-go).** Minimal app: Shizuku user service lists active players with
      uid/package, one slider calls `setVolume`. Run the test matrix on the phone. If uid/proxy is anonymized for
      shell, stop and re-plan (root backend).
- [ ] **M1 — Project skeleton.** Gradle (AGP 8.x, Kotlin 2.x, compileSdk 35, minSdk 31), Compose, Shizuku API
      13.1.5, Room, AIDL, CI build on GitHub Actions producing a debug APK.
- [ ] **M2 — Backend.** `PrivilegedAudioService` with playback callback, `ShizukuBackend` with reconnect + status flow.
- [ ] **M3 — Memory.** Room repository, `AppVolumeController`, re-apply on player events, unit tests with a fake backend.
- [ ] **M4 — Panel replacement.** Accessibility service, key rules, overlay window, collapsed + streams states with
      public `AudioManager`.
- [ ] **M5 — Apps panel.** State C, per-app sliders, mute-on-tap, icons, horizontal scroll, animations, haptics.
- [ ] **M6 — Onboarding & settings.** Setup checklist, panel side, timeout, "keys control foreground app" option,
      excluded apps, reset all.
- [ ] **M7 — Polish & release.** TalkBack pass, dark-only theme check, battery check (no wakelocks, no polling when
      screen off), signed release APK on GitHub Releases.

Optional later: web preview (`web/`) for fast UI iteration, QS tile to open the apps panel, root backend.

## 6. Testing

- Unit: `AppVolumeController` with `FakeBackend` (apply only adjusted apps, mute = 0, re-apply on new player,
  uninstalled package dropped).
- Instrumented: Room migrations, Compose slider semantics.
- Manual on device: test matrix above, reboot + Shizuku restart, app force-stop then replay keeps level.
