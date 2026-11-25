# On-device testing checklist

Everything here needs the phone; the unit tests (`./gradlew testDebugUnitTest`) already cover the controller,
store, offsets and the pill/card decision. Tick items as you go and note anything odd next to them.

Do **section 2 first**: it is the M0 go/no-go. If it fails, stop there — the rest depends on it.

## 0. Prepare

- [ ] Shizuku app is v13 or newer and running (wireless debugging).
- [ ] Debug APK installed: from the latest CI run (`nvlide-debug` artifact) or `./gradlew assembleDebug`.
- [ ] Logs open on a computer, if you have one:

  ```bash
  adb logcat -s nvlide.audio nvlide.shizuku AndroidRuntime
  ```

## 1. Setup screen

- [ ] Open nvlide: four steps listed (Shizuku running, Shizuku permission, accessibility service, battery).
- [ ] Shizuku stopped → step 1 shows "Open Shizuku" and opens it; start it, come back → ✓.
- [ ] "Allow" shows the Shizuku permission dialog; allow → ✓.
- [ ] "Open settings" opens Accessibility; turn on nvlide → ✓ when you come back.
  - [ ] If the switch was greyed out: App info → ⋮ → Allow restricted settings worked.
- [ ] "Turn off" battery optimisation shows the system dialog; allow → ✓.
- [ ] With all four ✓, the "Apps with audio open" card appears (empty until something plays).

## 2. M0 — backend go/no-go

Play something, then look at the setup screen's live card and the log.

- [ ] Log shows `players: [(com.example.app, 123), ...]` with real package names.
  **No-go if** it logs `cannot read player info` / `no player proxy`, or lists nothing while audio plays.
- [ ] The playing app shows up in the live card with its icon.
- [ ] Dragging its slider changes that app's loudness (and not other apps').

Test matrix — set the app to about 30 %, then for each column check the level is still 30 %:

| App | Slider works | Pause → resume | Next track / video | Close + reopen app | Phone call ends | Shizuku restart |
| --- | --- | --- | --- | --- | --- | --- |
| Spotify | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| YouTube | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| Chrome (web video) | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| Instagram | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| WhatsApp (voice note) | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| A game: ________ | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| PPSSPP | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |

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

## 5. Pill

- [ ] Something playing + volume key → pill `♫ N` appears beside the stock panel; N = apps playing now.
- [ ] Nothing has audio open → no pill.
- [ ] Pill hides about 3 s after the last key press.
- [ ] Pill also appears on the lock screen.
- [ ] Stop Shizuku → volume key shows "Start Shizuku"; tapping it opens Shizuku.
- [ ] Stop Shizuku **while the pill is shown** → it switches to "Start Shizuku".
- [ ] Revoke nvlide's Shizuku permission → pill says "Set up nvlide" and opens the setup screen.
- [ ] Position: move the offset sliders in the setup screen → the pill shows and follows; set it to sit just
  beside the stock panel. Note the values: portrait ______ / landscape ______.

## 6. App card

- [ ] Tap the pill → card slides out (about 0.2 s) with one slider per app.
- [ ] Order: playing apps first, then ones stopped in the last 10 s, then idle/paused.
- [ ] Paused/idle apps' sliders are dimmed.
- [ ] Dragging a slider changes the app's volume live; a haptic tick every 10 %.
- [ ] At 0 % a small dot shows at the bottom.
- [ ] Tap an app icon → muted (red fill, dimmed icon); tap again → back to the same level.
- [ ] Card stays while you touch it, closes about 3 s after the last touch, even if the stock panel is gone.
- [ ] Tap outside the card → it closes.
- [ ] Close the only app with audio while the card is open → the card closes.
- [ ] Sliders work on the lock screen without unlocking.
- [ ] Many apps (5+) → card scrolls sideways, never runs off screen.
- [ ] Rotate while the card is open → it stays on screen with shorter sliders in landscape.
- [ ] Does the card style match the stock panel closely enough? Notes: ______

## 7. TalkBack

- [ ] Pill reads "N apps playing. Open app volumes".
- [ ] Slider reads "<app> volume, N percent" (and "Muted" when muted).
- [ ] Swipe up/down on a slider changes it by 10 %.
- [ ] Icon reads "Mute <app>" / "Unmute <app>" and toggles mute.

## 8. Living with it

- [ ] Next day (battery optimisation off): pill still appears and levels still apply, with no setup again.
- [ ] No noticeable battery drain from nvlide (Settings → Battery).
- [ ] Screenshots: note whether the pill/card shows up in them (known limit).

## Results

Anything that failed, with the app, what you did and what happened (plus log lines if you have them):

-
