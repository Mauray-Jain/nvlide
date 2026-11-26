# nvlide — plan

Per-app volume sliders for Nothing phones. When the stock Nothing OS volume panel is expanded, its row of sliders
can be **swiped sideways to reach one slider per app**, drawn in the same style. Each app keeps its
level after it is closed. No root: privileged calls go through [Shizuku](https://shizuku.rikka.app/).

Status: **M0 passed its first check on the phone (Nothing Phone, Android 17, Shizuku 13.6)**: players are read with
package names, and the slider changes YouTube's loudness and sticks on new players. The overlay was redesigned on
the device to follow the stock panel window (see Decisions). The rest of the test matrix in TESTING.md is next.

---

## 1. Goals

1. When the stock volume panel is expanded (›), its slider row scrolls sideways: Media, Ring, Notification, Alarm,
   then one slider per app, 4 at a time. It goes when the panel goes.
3. Each app's level is saved by package name and re-applied whenever that app plays again, even after the
   app or the phone restarts.
4. Works without root, using Shizuku (wireless debugging on Android 11+).

Out of scope: replacing the system panel, consuming or remapping volume keys, boosting above 100 %,
per-app EQ, controlling calls/alarms per app, work profile apps, root backend.

## 2. Decisions

| Topic | Decision |
| --- | --- |
| Panel | **Beside, not replace.** nvlide never sees or consumes volume key events; the stock panel and all system key behaviour (calls, ringing, alarms, camera shutter, cast, Bluetooth, screenshot chord) stay untouched. |
| Follow the panel | The accessibility service watches window changes, and System UI's content changes, for the stock panel's body (`com.android.systemui:id/volume_dialog_background`) and shows nvlide **exactly while it is on screen**, placed from its live bounds. The stock panel slides in and out from the left edge; a body touching the edge counts as gone, so nvlide leaves as the slide-out starts (~30 ms) instead of after it (~120 ms later). No key filtering, no own timeout, no outside-tap handling. |
| When | **Only while the stock panel is expanded** (wider than one column). Nothing while it is collapsed. No pill (tried on the device and dropped: it got in the way and lagged the panel). |
| Inside the box | nvlide can't add views to System UI, so it covers **only the stock slider row** (`horizontal_sliders_container`) with an opaque window holding a sideways-scrolling copy: the stock stream rows first (same system volumes through the public `AudioManager` stream API, refreshed on `VOLUME_CHANGED_ACTION`), then the apps. The ringer buttons, SEE MORE and captions stay Nothing's. Rows are read from the stock sliders' labels (Media, Call, Ring, Notification, Alarm); any other row → nvlide stays out and the stock panel shows as is. A separate box under or beside the panel was tried on the device and dropped. |
| Touch | Vertical drag sets a level, sideways swipe scrolls the row, tap jumps to where it lands (like the stock slider). |
| Keep open | Any touch on nvlide restarts the stock panel's hide timer with `adjustSuggestedStreamVolume(ADJUST_SAME, USE_DEFAULT_STREAM_TYPE, FLAG_SHOW_UI)` (at most once a second): no volume changes, but the stock panel, and so nvlide, stays while a slider is dragged. |
| Which apps | Apps with an unreleased **media/game** player (`USAGE_MEDIA`, `USAGE_GAME`, `USAGE_UNKNOWN`; the service drops calls, ringtones, alarms, notifications and UI sounds) **that has played at least once, or that the user adjusted before** (so idle players kept open by system apps, e.g. System UI and Nothing X, don't show, but a paused adjusted app still does after nvlide restarts). Ordered: playing → stopped < 10 s ago → paused/idle. An app stays listed 10 s after its last player is released (YouTube swaps players between Shorts), then drops out. Paused apps are not dimmed (the stock panel doesn't dim either; it read as random flicker). Players are mapped to apps by **pid → running process** (handles shared uids, skips system processes); nvlide itself is skipped. |
| Memory | Level + mute saved per package, forever. The privileged service keeps the level of every adjusted package and sets it on a new player **in the callback that first reports it** (YouTube makes a new player for every Short and replay; going through the app first let it play loud for a moment). Dragging a slider applies immediately. |
| Keys | Stock behaviour only. |
| Lock screen | nvlide shows wherever the stock panel does; sliders work without unlocking. |
| Layout | Exactly the stock slider row's live bounds, 58 dp columns, so as many columns show at once as the stock panel shows (4). No settings. |
| Shizuku down | Expanded stock panel → the stream rows, then a round "Start Shizuku" button where the apps go; tap opens Shizuku. |
| Known limits | Screenshots may include nvlide's row. Players that ignore the proxy volume won't respond; this includes every app on an exclusive low-latency AAudio stream (many emulators and games, e.g. melonDS). A new player can still be loud for the few ms before Android reports it (most are reported only once started). nvlide depends on the stock panel's view id; another OEM skin or a Nothing OS update may rename it. |
| Package | `com.nvlide`. |
| Player → app | The privileged service maps pid → package (it is the only side that can list every running process) and sends `(package, piid, playing)` per player. Players with uid < 10000 or an unknown pid are dropped there. |
| Player list | Public `AudioManager.registerAudioPlaybackCallback` inside the shell-uid service (needs the Shizuku app v13+, which passes a `Context` to the user service); only the `@SystemApi` getters and `PlayerProxy.setVolume` use reflection. |
| HiddenApiBypass | Not added: the Shizuku user service runs under `app_process`, where hidden-API checks are off. Add it only if M0 shows reflection blocked. |
| Hint state | Shizuku stopped → "Start Shizuku" (opens Shizuku). Permission missing → "Set up nvlide" (opens the setup screen). Still connecting → nothing. The overlay follows Shizuku and player changes while shown: the card closes when the last app goes away, and turns into the hint if Shizuku stops. |
| Back to 100 % | Moving an adjusted app back to 100 % still sends `setVolume(1.0)`; new players of an app at 100 % unmuted get no call. |
| Idle order | Inside a group (playing / recent / idle) apps keep the order of the player list. |
| M0 spike | The setup screen shows the live app card while Shizuku is connected, so the test matrix can be run without the overlay. |
| Build | Gradle 8.14 needs JDK 17–24 (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk` on a machine whose default is newer). `targetSdk 35` is edge-to-edge, so the setup screen fits system windows itself. |
| Release | Minify off (reflection + Shizuku user service by class name). CI builds a debug APK on every push. A `v*` tag builds the release APK signed with the release key (`NVLIDE_KEYSTORE_BASE64`, `NVLIDE_KEYSTORE_PASSWORD`, `NVLIDE_KEY_ALIAS`, `NVLIDE_KEY_PASSWORD` secrets; the tag fails without them rather than ship unsigned), verifies the signature and publishes it as a GitHub Release (`nvlide-<version>.apk`). The key's only other copy is kept off-repo by the owner; every future release must use the same key. |

## 3. Target UI

```
 ( 🔇   📳   🔊 )                ← stock
 ┌─────────────────────┐
 │ ·    █    ·    █    │ ·    █    ·      ← swipe sideways
 │ █    █    ●    █    │ █    █    █
 │ ♪    ☎    🔔    ⏰    │ ▶    ◎    ✈      ← apps after the streams
 │      SEE MORE       │                  ← stock
 └─────────────────────┘
```

Visual tokens (measured from the stock panel on the phone, 420 dpi):

| Token | Value |
| --- | --- |
| Surface | `#303030`; card box 22 dp corner radius (like the expanded panel); hint button fully round |
| Column | 58 dp wide; track fills the row above a 42 dp icon strip, like the stock row |
| Filled level | 11.5 dp wide `#F1F1F1` capsule, bottom-aligned, height = width + level × (track − width): a round dot at 0, the full track at 100 % (measured on the stock row to ±1 px). Level = volume index ÷ max, like the stock row (alarm 11/16 → 69 %). |
| Empty part | dotted column, 4 dp dots `#474747`, 10 dp pitch |
| Icons | Stream rows use **System UI's own drawables** (loaded by name from its resources: `ic_volume_media`, `ic_ring_volume`, `ic_volume_ringer`, `ic_volume_alarm`, `ic_volume_voice_chat`), switching to their `_mute`/`_off` versions at 0 % like the stock row; tinted `#BFBFBF`; 23 dp. App icons greyscale, with a strike at 0 % or muted. |
| Muted app | fill → Nothing red `#D71921`, icon dimmed; tap icon toggles mute |
| Dragging | level bar widens from 11.5 dp to 36 dp (150 ms) and its rounded top rises by as much; shrinks back on release, like the stock slider |
| Haptics | tick every 10 % |

## 4. Architecture

Kotlin, plain Android Views, `minSdk 31`, `compileSdk`/`targetSdk 35`.

```
app process                                          Shizuku user service (shell uid)
┌─────────────────────────────────────────┐          ┌────────────────────────────────────┐
│ StockPanelService (AccessibilityService)│          │ PrivilegedAudioService             │
│   windows changed → stock panel bounds  │          │   • playback callback → push list  │
│   TYPE_ACCESSIBILITY_OVERLAY windows    │   AIDL   │   • package levels → set on new    │
│ AppVolumeController                     │◄────────►│     players at once                │
│ AppVolumeStore (SharedPreferences)      │          │   • PlayerProxy.setVolume(f)       │
│                                         │          └────────────────────────────────────┘
└─────────────────────────────────────────┘
```

Start with the fewest pieces that work; split only when a file gets hard to read.

| Piece | Responsibility |
| --- | --- |
| `NvlideApp` | Creates the one store, backend and controller; re-sends levels on audio mode change. |
| `StockPanelService` | Finds the stock panel's body on every window change and hands its bounds (or null) to `ui/Overlay`. |
| `StreamVolumes` | Stock row labels → streams; stream volume ↔ slider level, through the public `AudioManager` API. |
| `ui/` | `Overlay` (the window over the stock slider row, keeps the stock panel open while touched), `PanelLayout`, `PanelStripView` (the scrolling row), slider, hint button. |
| `AppVolumeController` | Joins open players + saved levels, picks and orders apps, sends levels by package. |
| `AppVolumeStore` | Package → level/mute, in SharedPreferences. |
| `backend/` | Shizuku binding + the privileged service + AIDL. All hidden-API reflection lives only here. |
| `MainActivity` | Setup checklist (Shizuku, permission, accessibility, battery optimisation) and the live app card. |

### How the volume is applied

AOSP keeps the volume set through `PlayerProxy.setVolume` in a separate multiplier inside each player
(`PlayerBase.mVolMultiplier`), multiplied with the app's own volume. The app's own `setVolume`, fades, ducking,
pan and start/pause/stop don't touch it, so **no polling is needed**. It is lost only in the cases below, and
each one has an event:

1. The app sends the shell service the level of every adjusted package (on connect, and on every change).
2. Shell process gets playback config changes → maps pid → package, sets the saved level on every **new** player of
   an adjusted package right there, then pushes the `(package, piid, playing)` list to the app for the card.
3. Moving a slider → the package's level goes to the service → `setVolume` on every player of that package.
   Back to 100 % unmuted → set once more, then new players of that package are left alone.
4. **New player instance** (next track, next Short, app restarted, after reboot) starts at 100 % → step 2.
5. **System resets player volume** (e.g. other apps muted during a phone call, reset to 100 % after) → re-send
   all adjusted packages when audio mode returns to normal (`AudioManager.addOnModeChangedListener`).
6. **Shizuku service reconnects** → re-send all adjusted packages.

## 5. Risks

| Risk | Check |
| --- | --- |
| Shell uid gets anonymized `AudioPlaybackConfiguration` (no pid/uid / proxy) on Nothing OS | M0 spike |
| Some players ignore `PlayerProxy.setVolume` (AAudio MMAP, OpenSL, some games) | **Confirmed for exclusive MMAP AAudio** (melonDS: `type:AAudio`, `AUDIO_OUTPUT_FLAG_MMAP_NOIRQ`, an "Exclusive MMAP Endpoint" in `dumpsys media.aaudio`): the stream goes straight to the HAL and the level has no effect. The only system switch, `aaudio.mmap_exclusive_policy`, can't be set by the shell uid (setprop is refused), and blocking the app's `PLAY_AUDIO` app-op doesn't silence an MMAP stream either, so there's no fix without root. Workarounds: the app's own volume, or turning off its low-latency mode (melonDS: Audio latency → Medium). nvlide detects these apps (AAudio players whose pid has a stream in `dumpsys media.aaudio`, read by the shell service) and greys their slider out, without the mute colour; the mark is saved per app and cleared only when the app is seen playing on the normal path (these apps close their stream in the background); a tap explains why inside the panel (toasts from the service are dropped while nvlide's notifications are off). Rest of the M0 matrix still to run. |
| System resets player volume (calls, possibly other cases) | M0: level survives a phone call; re-apply on mode change |
| Hidden-API restrictions in the user-service process | Add `HiddenApiBypass` only if needed |
| Shizuku not running after reboot | "Start Shizuku" button in the expanded panel |
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
- [x] **M3 — Overlay.** Accessibility service following the stock panel window; swipeable copy of the expanded
      stock slider row with the apps after the streams; keeps the stock panel open while touched.
- [x] **M4 — Sliders.** Stock look, icons, mute, ordering, drag look, drag vs swipe, haptics.
- [x] **M5 — Setup & polish.** Setup checklist, TalkBack, battery check, CI debug APK, release APK.

M1–M5 are written and unit-tested where possible; everything device-side still needs the M0 run and the
manual checks in section 7.

## 7. Testing

Tests are written before the implementation (see CLAUDE.md).

- Unit: controller with a fake backend (only adjusted apps sent, mute = 0, re-send on mode change / reconnect,
  only apps that played or were adjusted, kept 10 s after their player goes, ordering playing → recent → idle,
  uninstalled package skipped), store, overlay content, stock row detection, stream mapping and level conversion,
  drag vs swipe, bar height.
- Manual on device: the full checklist is in [TESTING.md](TESTING.md) (M0 test matrix first, then memory,
  untouched key behaviour, showing/hiding, card, TalkBack).
