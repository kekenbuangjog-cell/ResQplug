# APP_RESTRUCTURE — moving ResQPlug to Model-View-Presenter

Plan and task record for rearranging the Android app so it follows the MVP
architecture already written down in
[`../EXTERNAL_LED_FIREBASE_MVP_ACTIVITY_GUIDE.md`](../EXTERNAL_LED_FIREBASE_MVP_ACTIVITY_GUIDE.md).

---

## Read these in order

| File | What it is | When you need it |
|---|---|---|
| [`PLAN.md`](PLAN.md) | **The decisions** — why MVP, why these folders, why Bluetooth, what we deleted | Before touching any code |
| [`TASKS.md`](TASKS.md) | **The work** — ordered phases, one checkbox per task, each with a verify step | Every session |
| [`MIGRATION_MAP.md`](MIGRATION_MAP.md) | **The lookup table** — every file's old path → new path, with line counts | When you can't find something |

---

## Why this is happening

The blueprint is explicit (`EXTERNAL_LED_FIREBASE_MVP_ACTIVITY_GUIDE.md:95`):

> *"The deliverable **strictly forbids** placing business and Firebase logic
> inside Activities or Fragments."*

The app does not currently obey that. Two files hold almost all the logic:

| File | Lines | Problem |
|---|---|---|
| `MainActivity.kt` | 566 | UI + USB detection + Bluetooth permissions + Bluetooth connect + Firebase writes, all inline |
| `DashboardActivity.kt` | 583 | UI + USB + heartbeat loop + **five** separate Firestore methods + tab gating, all inline |
| `SimulationEngine.kt` | 686 | God object — packet parsing, mesh nodes, messages, SOS, roles, Bluetooth, Firebase |

`activity7/` — the LED/Firebase course assignment — already obeys the blueprint
perfectly. It is the proof the pattern works here, and it is being **deleted**
once its pattern has been borrowed.

---

## The decisions, one line each

| Question | Answer |
|---|---|
| What shape? | Layer-based MVP — `model/` `view/` `presenter/`, sub-folders inside |
| How many top-level packages? | **Exactly three** |
| `activity7/`? | **Delete** — pattern borrowed, package removed |
| Transport? | **Bluetooth primary**, USB-OTG kept as a documented stub |
| Dashboard? | **Single Activity + 4 fragments** — which, it turns out, already exists |
| `versionCode`? | Bump to `2` at the end so the new build is distinguishable |

Full reasoning in [`PLAN.md`](PLAN.md).

---

## Ground rules

1. **One phase at a time.** Each phase must compile *and* run before the next begins.
2. **Commit after each phase**, not at the end. If something breaks you can bisect.
3. **Phase 7 is the real gate** — re-run the Bluetooth end-to-end test from
   2026-10-10. We have proven hardware; most refactors never get a regression
   test this strong. Use it.
4. **No behaviour changes inside a move phase.** Phases 1–3 are pure renames and
   deletions. If a phase says "no behaviour change", do not "improve" anything
   while you're in it.

---

## Status

| Phase | Name | State |
|---|---|---|
| 0 | Baseline + scaffolding | ⬜ not started |
| 1 | Mechanical moves | ⬜ not started |
| 2 | Transport + OTG honesty fix | ⬜ not started |
| 3 | Delete `activity7/` | ⬜ not started |
| 4 | Extract repositories | ⬜ not started |
| 5 | Extract presenters | ⬜ not started |
| 6 | Thin the views | ⬜ not started |
| 7 | Verify + hardware regression | ⬜ not started |

Update this table as you go. [`TASKS.md`](TASKS.md) holds the detail.

---

*Created 2026-10-10 · ResQPlug Capstone 2026–2027 · University of Cebu Banilad*
