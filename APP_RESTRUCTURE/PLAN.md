# PLAN — architecture decisions for the ResQPlug app

Every decision below was taken on 2026-10-10, immediately after the two-node
link and the phone end-to-end test were proven on real hardware.

---

## 1. Why Model-View-Presenter

Not a preference — a requirement. `EXTERNAL_LED_FIREBASE_MVP_ACTIVITY_GUIDE.md:95`:

> *"The deliverable **strictly forbids** placing business and Firebase logic
> inside Activities or Fragments."*

And `:128`:

> *"Pure UI—zero Firebase references."*

The app currently breaks both rules in `MainActivity` and `DashboardActivity`.
That is the single thing this restructure exists to fix.

### The pattern, as the blueprint defines it

```
       [ USER ]
          │
          ▼
   ┌──────────────┐   calls     ┌───────────────────┐
   │     VIEW     │ ──────────► │     PRESENTER     │
   │ (Activity /  │             │                   │
   │  Fragment)   │ ◄────────── │                   │
   └──────────────┘  updates UI └─────────┬─────────┘
                                          │ reads/writes
                                          ▼
                                ┌───────────────────┐
                                │  MODEL/REPOSITORY │
                                └─────────┬─────────┘
                                          ▼
                                   [ FIREBASE / HW ]
```

| Layer | Owns | Must never contain |
|---|---|---|
| **View** | widgets, lifecycle, rendering | Firebase, business rules, LoRa logic |
| **Presenter** | coordination, validation, state decisions | Android widget references, Firebase calls |
| **Model / Repository** | data access, transport, persistence | UI references |

`activity7/` already implements this correctly. It is the reference.

---

## 2. Why exactly three top-level packages

The shape asked for:

```
model/        data classes + repositories (business logic)
view/         Activities + View interfaces
presenter/    Presenter interfaces + implementations
```

Three is not arbitrary — it is the smallest number that maps cleanly onto the
three layers the blueprint names. Everything else is a **sub-folder inside one
of those three**, never a fourth top-level package.

The one judgement call was where the Bluetooth / USB / hardware-bridge code
goes. It is not business logic and it is not UI — it is *how the model reaches
the outside world*. It belongs to the model layer:

```
model/transport/     ← the plumbing a Repository talks to
```

That keeps the count at three while giving transport a clear home.

---

## 3. Target structure

```
com.example.resqplug/
│
├── model/
│   ├── data/                    MeshNode · ChatMessage · SosIncident · UserRole
│   ├── engine/                  MeshEngine.kt
│   ├── repository/              DeviceRepository · MessageRepository · SosRepository
│   └── transport/               HardwareBridge · BluetoothTransport
│                                UsbDeviceHelper · UsbConnectionReceiver
│
├── presenter/
│   ├── MainContract.kt          MainPresenter.kt
│   ├── DashboardContract.kt     DashboardPresenter.kt
│   ├── MessagesContract.kt      MessagesPresenter.kt
│   └── SosContract.kt           SosPresenter.kt
│
└── view/
    ├── MainActivity.kt          thin shell
    ├── DashboardActivity.kt     thin shell + fragment host
    ├── fragments/               Dashboard · Messages · Settings · SosHub
    ├── adapters/                ActiveContact · Conversation · Message · SosIncident
    └── widgets/                 StarfieldView.kt
```

Full old→new mapping in [`MIGRATION_MAP.md`](MIGRATION_MAP.md).

### Naming

| Old | New | Why |
|---|---|---|
| `SimulationEngine` | `MeshEngine` | It has not been a simulation for a while — it drives real LoRa |
| `ResQPlugHardwareBridge` | `HardwareBridge` | Package is already `resqplug`; the prefix is redundant |
| `BluetoothRadioHelper` | `BluetoothTransport` | States its role in `model/transport/` |
| `activity7` | *(deleted)* | An activity number is not a domain name |

---

## 4. Decision: delete `activity7/`

**Chosen:** delete.

| Reason | Detail |
|---|---|
| It is coursework, not product | It controls an external LED over Firebase — a separate assignment, not a ResQPlug feature |
| Its name means nothing | `activity7` tells a future reader nothing |
| Its layouts are exclusive | `activity_led`, `activity_a7_login`, `activity_a7_connection`, `item_a7_bluetooth_device`, `dialog_clearance_success` — each referenced by exactly one activity7 file |
| Nothing else uses its auth | `FirebaseAuth` appears **nowhere** outside `activity7/` — verified 2026-10-10 |

**What is kept:** the pattern. Contract → Presenter → Repository → View is
applied to every remaining feature. `activity7/` is the worked example this
plan copies.

**Consequence:** the app will have **no Firebase Authentication**. It never
had any outside `activity7/`, so this is not a regression. If auth is wanted
later, add `AuthRepository` under `model/repository/` following the same shape.

---

## 5. Decision: Bluetooth primary, USB-OTG as a stub

**Chosen:** Bluetooth is the real transport. USB-OTG stays, but honestly labelled.

### Why this was forced

`ResQPlug.md:12` states the project's premise:

> *"…drawing minimal operating power straight from the mobile device. This
> eliminates the need for external battery packs, bulky enclosures, or
> **error-prone wireless pairing (such as Bluetooth)** during high-stress
> emergency operations."*

The hardware disagreed. On 2026-10-10 the full chain was proven over
**Bluetooth**, and the OTG path was found to be a stub that reports success
without sending:

```kotlin
// ResQPlugHardwareBridge.kt:148-150 — the entire OTG "send"
Transport.USB_OTG -> {
    true
}
```

And `attachUsbSession()` never starts a reader, so nothing is ever received.

### What we do about it

| | Action |
|---|---|
| **Firmware** | Already dual-transport — `Serial` *and* `SerialBT` both feed the same handler. No change needed. |
| **App — send** | Change the `USB_OTG` branch to return **`false`** and log. It must stop lying. |
| **App — receive** | Leave absent. Documented as unimplemented. |
| **Paper** | `ResQPlug.md` needs a revision note: OTG remains the design target; Bluetooth is the validated implementation. |

The important principle: **a transport that fails loudly is better than one that
fails silently.** Right now a user could send an emergency message over OTG,
see "sent", and reach nobody.

---

## 6. Decision: single Activity + 4 fragments

**Chosen:** one host Activity, four fragments.

This turned out to be **already true**. `DashboardActivity.selectTab()`
(:394-415) swaps `DashboardFragment` / `SosHubFragment` / `MessagesFragment` /
`SettingsFragment` into `R.id.fragmentContainerView`, and bottom-nav gating
(:417-443) is already in place.

So the work here is **not** restructuring navigation. It is:

- move the five `*InFirestore()` methods out into a repository,
- move heartbeat and freshness tickers out into a presenter,
- leave `DashboardActivity` as a thin shell that only routes.

---

## 7. Phase rationale

| Phase | Why this order |
|---|---|
| **0** Baseline | Know the build works *before* you change it, so you can tell what you broke |
| **1** Mechanical moves | Pure renames — cheapest possible way to prove the new package layout compiles |
| **2** Transport | Moves a leaf dependency before anything that depends on it moves |
| **3** Delete `activity7/` | Removes **1,562 lines across 12 files** plus 5 layouts before the refactor starts, shrinking the surface |
| **4** Repositories | Extracting **data access** first is safer than extracting **logic** — a repository is a thin wrapper with no decisions in it |
| **5** Presenters | Now the logic has somewhere to go, because the repositories exist |
| **6** Thin the views | Falls out of 4 and 5; little work left |
| **7** Verify + hardware test | **The gate.** Two working ESP32s exist — use them. |

The ordering principle throughout: **move data access before moving logic.**
Logic without a repository to call has nowhere to live, which is why most MVP
refactors fail when they start with Presenters.

---

## 8. Out of scope

Deliberately **not** part of this restructure:

| Item | Why not |
|---|---|
| Implementing USB-OTG for real | ~150 lines plus a serial library — a separate job, tracked in `TASKS.md` Phase 7 notes |
| Firebase Authentication | Never existed outside `activity7/`; adding it is a feature, not a refactor |
| `versionCode` bump to `2` | Worth doing at the end so the new build is distinguishable on the phone; listed in Phase 7 |
| Any change to `firmware/` | The firmware was proven today. Do not touch it. |
| Design / visual rules | `RESQPLUG_DESIGN_RULES.md` governs appearance and is unaffected by package layout |

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| Breaking the working Bluetooth path | Phase 7 re-runs the exact end-to-end test proven on 2026-10-10 |
| `SimulationEngine` is 686 lines and everything touches it | Phase 5 splits it **last**, after repositories exist, and only by extracting — never rewriting |
| Fragment ↔ Activity contracts are implicit | `*Contract.kt` interfaces make them explicit; this is the main structural gain |
| Losing the Firebase field names | Repositories keep the exact same collection and field strings — pure extraction, no schema change |
| Scope creep into "while I'm here" fixes | Rule 4 in the README: no behaviour changes inside a move phase |

---

*Created 2026-10-10 · ResQPlug Capstone 2026–2027*
