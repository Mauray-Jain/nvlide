# On-device testing checklist

Everything here needs the phone; the unit tests (`./gradlew testDebugUnitTest`) already cover the controller,
store, the content rule and the card placement. Tick items as you go and note anything odd next to them.

Do **section 2 first**: it is the M0 go/no-go. If it fails, stop there — the rest depends on it.

## 0. Prepare

- [x] Shizuku app is v13 or newer and running (wireless debugging, or over USB:
  `adb shell <shizuku apk dir>/lib/arm64/libshizuku.so`; that one stops on reboot).
- [x] Debug APK installed: from the latest CI run (`nvlide-debug` artifact) or `./gradlew assembleDebug`
  (needs JDK 17–24: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk`).
- [ ] Logs open on a computer, if you have one:

  ```bash
  adb logcat -s nvlide.audio nvlide.shizuku AndroidRuntime
  ```

## 1. Setup screen

- [x] Open nvlide: four steps listed (Shizuku running, Shizuku permission, accessibility service, battery).
- [x] Shizuku not installed → "Open Shizuku" opens the download page; started → ✓.
- [x] "Allow" shows the Shizuku permission dialog; allow → ✓.
- [x] "Open settings" opens Accessibility; turn on nvlide → ✓ when you come back. Android warns that it can see
  the screen: nvlide only reads the volume panel window.
  - [ ] If the switch was greyed out: App info → ⋮ → Allow restricted settings worked.
- [x] "Turn off" battery optimisation shows the system dialog; allow → ✓.
- [x] With all four ✓, the "Apps with audio open" card appears (empty until something plays).

## 2. M0 — backend go/no-go

Play something, then look at the setup screen's live card and the log.

- [x] Log shows `players: [com.example.app#123 ▶, ...]` with real package names (▶ = playing).
  **No-go if** it logs `cannot read player info` / `no player proxy`, or lists nothing while audio plays.
- [x] The playing app shows up in the live card with its icon.
- [x] Dragging its slider changes that app's loudness (and not other apps').

Test matrix — set the app to about 30 %, then for each column check the level is still 30 %:

| App | Slider works | Pause → resume | Next track / video | Close + reopen app | Phone call ends | Shizuku restart |
| --- | --- | --- | --- | --- | --- | --- |
| Spotify | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| YouTube | [x] | [ ] | [x] | [ ] | [ ] | [ ] |
| Chrome (web video) | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| Instagram | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| WhatsApp (voice note) | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| A game: ________ | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| PPSSPP | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| melonDS | no: exclusive MMAP AAudio, see Results | | | | | |

- [ ] **Go/no-go:** the slider works and the level sticks for most of the matrix. Write down which apps ignore it
  (expected for some games / AAudio apps; that's a known limit).

## 3. Memory

- [ ] Force-stop an adjusted app (App info → Force stop), play again → level is back without touching anything.
- [ ] Mute an app, play another track → still muted.
- [ ] Reboot, start Shizuku, play the adjusted app → level re-applied.
- [ ] An app you never touched keeps its normal volume (no change at all).
- [ ] Moving an adjusted app back to 100 % makes it full volume again.

## 4. Volume keys and the stock panel (must stay untouched)

- [ ] Volume up/down still show the stock Nothing panel and change the volume exactly as before.
- [ ] Volume keys during a call change call volume.
- [ ] Volume key silences an incoming call's ringing.
- [ ] Camera: volume key still takes a photo.
- [ ] Power + volume down still takes a screenshot.
- [ ] Bluetooth headphones / casting: volume keys still control them.

## 5. Showing and hiding

- [x] Stock panel collapsed → nvlide shows nothing.
- [x] Stock panel expanded (›) → the slider row looks the same, and swiping it sideways reaches the apps.
- [x] A sideways swipe that starts on a slider scrolls; it doesn't change that volume.
- [ ] Media / Ring / Notification / Alarm in nvlide's copy change the real volumes, and follow the volume keys.
- [x] nvlide goes as the stock panel starts sliding out, not after it.
- [ ] Only system sounds open (System UI, Nothing X) and nothing adjusted → stock panel untouched.
- [ ] Also works on the lock screen.
- [ ] Stop Shizuku → expanded panel shows "Start Shizuku"; tapping it opens Shizuku.
- [ ] Stop Shizuku **while the row is shown** → it switches to "Start Shizuku".
- [ ] Revoke nvlide's Shizuku permission → "Set up nvlide", which opens the setup screen.
- [ ] Landscape: the row still lines up with the stock one.

## 6. App card

- [x] Stream icons are the stock ones (same shape, size, position, colour); struck through at 0 %.
- [ ] App icon struck through at 0 % or muted.
- [x] Low-latency app (melonDS, Audio latency Low) → slider greyed out, can't be dragged, no strike; a tap shows
  "… uses low-latency audio, which nvlide can't change. Use its own volume setting." inside the panel.
- [x] A paused app that was adjusted before still shows after nvlide restarts.
- [ ] Next Short / next track → the app's column stays put (no blink, no greying).
- [ ] Order: playing apps first, then ones stopped in the last 10 s, then idle/paused.
- [ ] Dragging a slider changes the app's volume live; a haptic tick every 10 %.
- [x] While touched, the level bar widens like the stock one and shrinks back on release.
- [x] Stream bars and the 0 % dot in nvlide's copy match the stock expanded row (±1 px); no jump when expanding.
- [ ] Tap an app icon → muted (red fill, dimmed icon); tap again → back to the same level.
- [x] Holding a slider keeps the stock panel (and nvlide) open; both close together a few seconds after letting go.
- [ ] Close the only app with audio while the card is open → the card closes.
- [ ] Sliders work on the lock screen without unlocking.
- [ ] Does the card style match the stock panel closely enough? Notes: ______

## 7. TalkBack

- [ ] Slider reads "<app> volume, N percent" (and "Muted" when muted).
- [ ] Swipe up/down on a slider changes it by 10 %.
- [ ] Icon reads "Mute <app>" / "Unmute <app>" and toggles mute.

## 8. Living with it

- [ ] Next day (battery optimisation off): the app sliders still appear and levels still apply, with no setup again.
- [ ] No noticeable battery drain from nvlide (Settings → Battery).
  2026-11-26 measurements (Nothing Phone A059): batterystats 2.16 mAh for nvlide's uid over an evening of heavy
  testing, no wakelocks of its own; idle CPU 3 ticks/min for the app (was 25 before ignoring System UI content
  changes while the panel is down), 0 for the Shizuku service. Memory (PSS): app 22 MB with the setup screen closed
  (127 MB while it is open), Shizuku service 53–83 MB (not zygote-forked, so framework code isn't shared).
  During YouTube playback, per minute: app 0.03 s CPU, Shizuku service 0.04 s for 11 player updates (YouTube itself:
  22.9 s). One panel use (open, expand, swipe, close): 0.53 s CPU after opening System UI's resources once per
  process and coalescing panel scans to one per frame (was 0.9 s); the first use after nvlide starts costs ~1.8 s.
- [ ] Screenshots: note whether nvlide's row shows up in them (known limit).

## Results

Anything that failed, with the app, what you did and what happened (plus log lines if you have them):

- 2026-11-26, Nothing Phone (A059), Android 17, Shizuku 13.6.0: volume keys sent with `adb shell input keyevent`
  open the stock panel, so nvlide shows for them too (it follows the panel, not the keys).
- Fixed on the device: setup screen hidden under the title bar (edge-to-edge); short loud burst on every new
  YouTube Short / replay (levels now set in the shell service); pill/card placement and look (now taken from the
  stock panel's live bounds); System UI / Nothing X listed as apps.
- melonDS: listed and playing, level 0 % has no effect. It plays through an exclusive MMAP AAudio stream (straight
  to the HAL, bypassing the mixer). `adb shell setprop aaudio.mmap_exclusive_policy 1` (to force shared mode) is
  refused for the shell uid, so Shizuku can't change it either. `appops set me.magnum.melonds PLAY_AUDIO ignore`
  doesn't silence it either (tested, then reset to default). Known limit; nvlide now greys the slider out and explains.
