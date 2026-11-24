# nvlide

Android app (Kotlin) adding per-app volume sliders beside the stock Nothing OS volume panel, using Shizuku
for the privileged audio calls. Design, decisions and milestones live in [PLAN.md](PLAN.md); keep it updated
when a decision changes.

## Ground rules

- nvlide never consumes volume key events. The stock panel and system key behaviour must stay untouched.
- All hidden-API / reflection code lives only in `backend/`. Nothing else touches hidden APIs.
- Only apps the user has adjusted ever get `setVolume` calls.

## Code style

- **Occam's razor.** Try the simplest thing that works first. No abstraction, layer, interface, config option
  or dependency until a concrete need exists right now, not a hypothetical one.
- No micro-optimisations without a measured problem.
- Keep the architecture clean: each file has one clear job, dependencies point one way
  (UI → controller → store/backend), no cycles, no god objects.
- Prefer deleting code over adding it. Small files and small functions; split when something is hard to read,
  not before.
- Kotlin idioms, standard Android/Jetpack libraries; match the existing style of the file you edit.
