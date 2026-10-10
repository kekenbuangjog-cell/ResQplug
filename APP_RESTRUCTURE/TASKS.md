# TASKS — the working checklist

Work top to bottom. **Tick a task only when its verify line passes.**
Commit after each phase, not at the end.

```
Phase  0  baseline + scaffolding
Phase  1  mechanical moves          (no behaviour change)
Phase  2  transport + OTG honesty   (one small behaviour change, deliberate)
Phase  3  delete activity7/
Phase  4  extract repositories
Phase  5  extract presenters
Phase  6  thin the views
Phase  7  verify + hardware regression
```

---

# Phase 0 — Baseline and scaffolding

Nothing here changes app code.

- [ ] **0.1** Record a clean baseline build
      - Run `gradlew assembleDebug`
      - **Verify:** build succeeds; note the APK path and size
- [ ] **0.2** Confirm the app still installs and the Bluetooth link still works
      - Install, pair to `ResQPlug-POD-C8E720`, send a test message
      - **Verify:** `[RX:…DATA:[MSG:…]]` appears on Node 2's serial (COM3)
- [ ] **0.3** Capture `git status` and `git log --oneline -3` into this folder
      - **Verify:** a known-good starting commit is written down
- [ ] **0.4** Create `APP_RESTRUCTURE/` (this folder) — **done 2026-10-10**
- [ ] **0.5** Add `APP_RESTRUCTURE/` to the repo and commit
      - **Verify:** `git status` clean

---

# Phase 1 — Mechanical moves

**Rule for this entire phase: no behaviour change.** Pure package moves and
renames only. If you find yourself editing a line's logic, stop — that belongs
in a later phase.

- [ ] **1.1** Create the empty package folders
      - `model/data/`, `model/engine/`, `model/repository/`, `model/transport/`
      - `presenter/`
      - `view/fragments/`, `view/adapters/`, `view/widgets/`
      - **Verify:** folders exist, project still builds
- [ ] **1.2** Move data classes → `model/data/`
      - `simulation/MeshNode.kt`, `ChatMessage.kt`, `SosIncident.kt`, `UserRole.kt`
      - **Verify:** compiles
- [ ] **1.3** Move adapters → `view/adapters/`
      - `ui/ActiveContactAdapter.kt`, `ConversationAdapter.kt`,
        `MessageAdapter.kt`, `SosIncidentAdapter.kt`
      - **Verify:** compiles
- [ ] **1.4** Move `ui/StarfieldView.kt` → `view/widgets/`
      - **Verify:** compiles; starfield still renders on both screens
- [ ] **1.5** Move fragments → `view/fragments/`
      - `ui/fragments/DashboardFragment.kt`, `MessagesFragment.kt`,
        `SettingsFragment.kt`, `SosHubFragment.kt`
      - **Verify:** compiles; all four tabs still open
- [ ] **1.6** Move `MainActivity.kt` and `DashboardActivity.kt` → `view/`
      - Update `AndroidManifest.xml` activity names
      - **Verify:** compiles; app launches; **manifest points at new paths**
- [ ] **1.7** Delete the now-empty `ui/` package
      - **Verify:** `ui/` gone; compiles
- [ ] **1.8** Commit Phase 1
      - Message shape: `refactor(app): move data, adapters, fragments and views into MVP packages`

> **Stop and run the Bluetooth test** if anything felt uncertain. Cheap now,
> expensive later.

---

# Phase 2 — Transport, and one honest fix

- [ ] **2.1** Move `hardware/ResQPlugHardwareBridge.kt` → `model/transport/HardwareBridge.kt`
      - Rename class `ResQPlugHardwareBridge` → `HardwareBridge`
      - **Verify:** compiles
- [ ] **2.2** Move `bluetooth/BluetoothRadioHelper.kt` → `model/transport/BluetoothTransport.kt`
      - Rename class accordingly
      - **Verify:** compiles; Bluetooth still connects
- [ ] **2.3** Move `usb/UsbDeviceHelper.kt` and `UsbConnectionReceiver.kt` → `model/transport/`
      - **Verify:** compiles
- [ ] **2.4** Delete the now-empty `hardware/`, `bluetooth/`, `usb/` packages
      - **Verify:** compiles
- [ ] **2.5** **Fix the OTG lie** in `HardwareBridge.sendCommand()`
      - Change the `Transport.USB_OTG` branch from returning `true`
        to returning `false`, with a log line
      - Suggested: `Log.w(TAG, "USB_OTG send not implemented — returning false")`
      - **Verify:** compiles; over Bluetooth nothing changes; **no code path
        claims success for OTG any more**
- [ ] **2.6** Also fix `HardwareBridge.isConnected()` — `USB_OTG -> true` is
      the same lie. Return `false` until a real OTG session exists
      - **Verify:** compiles
- [ ] **2.7** Commit Phase 2
      - Message: `refactor(app): move transport into model/transport; stop OTG stub reporting false success`

> **Note:** 2.5 and 2.6 are the only intentional behaviour changes in Phases 1–3.
> Everything else is a rename. Call this out in the commit body.

---

# Phase 3 — Delete `activity7/`

Verified 2026-10-10: `FirebaseAuth` appears nowhere outside this package, and
each of its five layouts is referenced by exactly one of its own files.

- [ ] **3.1** Delete the package `activity7/` (12 files)
- [ ] **3.2** Delete its layouts
      - `activity_led.xml`, `activity_a7_login.xml`, `activity_a7_connection.xml`,
        `item_a7_bluetooth_device.xml`, `dialog_clearance_success.xml`
- [ ] **3.3** Remove its three manifest entries
      - `.activity7.Activity7AuthActivity`, `.activity7.Activity7ConnectionActivity`,
        `.activity7.LedActivity`
- [ ] **3.4** Delete `R.string` entries used only by those layouts
      - **Verify:** `strings.xml` has no unused `a7_`/`led_` leftovers
- [ ] **3.5** Delete the LED-only Firebase path if now orphaned
      - `activities/esp32_led_control` writes — check none remain
      - **Verify:** `grep -r "esp32_led_control"` returns nothing
- [ ] **3.6** Compiles and launches; Bluetooth still connects
- [ ] **3.7** Commit Phase 3
      - Message: `refactor(app)!: remove activity7 coursework package (12 files, 1562 lines, 5 layouts, 3 activities)`

> The `!` marks a breaking removal. Note in the body that `FirebaseAuth` is
> consequently absent — it was never used by the product code.

---

# Phase 4 — Extract repositories

**Data access only. No business rules.** A repository is a thin wrapper around
Firestore with the same collection and field names as today — pure extraction,
no schema change.

- [ ] **4.1** Create `model/repository/DeviceRepository.kt`
      - Move the five Firestore methods from `DashboardActivity`:
        `markDeviceActiveInFirestore()` (:563), `markDeviceInactiveInFirestore()` (:590),
        `updateUserNameInFirestore()` (:606), `updateUserRoleInFirestore()` (:645),
        `startHeartbeatLoop()` writes (:288)
      - Move `registerDeviceToFirebase()` (:577) from `MainActivity`
      - **Keep exact** collection name `active_devices` and all field strings
      - **Verify:** compiles
- [ ] **4.2** Create `model/repository/MessageRepository.kt`
      - Message send/persist path currently inside `SimulationEngine`
      - **Verify:** compiles
- [ ] **4.3** Create `model/repository/SosRepository.kt`
      - SOS create/list/resolve currently inside `SimulationEngine`
      - **Verify:** compiles
- [ ] **4.4** Strip every `import com.google.firebase.*` from both Activities
      - **Verify:** `grep -rn "com.google.firebase" view/` returns **nothing**
      - **This is the blueprint rule, proven mechanically**
- [ ] **4.5** App still registers, heartbeats and goes active/inactive in Firestore
      - **Verify:** Firebase Console shows `active_devices` updates on connect/disconnect
- [ ] **4.6** Commit Phase 4
      - Message: `refactor(app): extract Device/Message/Sos repositories; remove all Firebase imports from views`

> **4.4 is the milestone of the whole restructure.** Screenshot it.

---

# Phase 5 — Extract presenters

Now the logic has somewhere to go, because the repositories exist.

- [ ] **5.1** Create `presenter/MainContract.kt` + `MainPresenter.kt`
      - Move: Bluetooth permission flow, device attach/detach handling,
        the connection sequence, transport selection
      - View keeps: widget references, `registerForActivityResult`, lifecycle
      - **Verify:** compiles; connect flow works over Bluetooth
- [ ] **5.2** Create `presenter/DashboardContract.kt` + `DashboardPresenter.kt`
      - Move: heartbeat loop, freshness ticker, tab gating by role,
        transport status decisions
      - **Verify:** compiles; dashboard still updates
- [ ] **5.3** Create `presenter/MessagesContract.kt` + `MessagesPresenter.kt`
      - Move: message send/route decisions from `MessagesFragment` + engine
      - **Verify:** compiles; sending works
- [ ] **5.4** Create `presenter/SosContract.kt` + `SosPresenter.kt`
      - Move: SOS raise/resolve, responder-only gating
      - **Verify:** compiles
- [ ] **5.5** Rename `simulation/SimulationEngine.kt` → `model/engine/MeshEngine.kt`
      - **Rename only in this task.** No restructuring of its 686 lines yet
      - **Verify:** compiles; `[RX:…]` packets still parse
- [ ] **5.6** Break `MeshEngine` into focused collaborators
      - Suggested split: `PacketParser`, `SeenCache`, `TxQueue`, `MeshRelay`
      - **Do this last** — it is the riskiest single step in the project
      - **Verify:** compiles; **full Bluetooth end-to-end test passes**
- [ ] **5.7** Commit Phase 5
      - Message: `refactor(app): introduce MVP presenters; split MeshEngine into parser/cache/queue/relay`

> **5.6 needs its own commit** and its own run of the hardware test. Do not
> bundle it with 5.1–5.5.

---

# Phase 6 — Thin the views

Mostly falls out of Phases 4–5.

- [ ] **6.1** `MainActivity` should contain only: widget refs, lifecycle,
      `registerForActivityResult`, and calls into `MainPresenter`
      - **Verify:** compiles; under ~150 lines
- [ ] **6.2** `DashboardActivity` should contain only: fragment host,
      bottom-nav wiring, and calls into `DashboardPresenter`
      - **Verify:** compiles; under ~200 lines
- [ ] **6.3** No Activity or Fragment imports `com.google.firebase.*`
      - **Verify:** grep returns nothing across `view/`
- [ ] **6.4** No Activity or Fragment calls `Firestore` directly
      - **Verify:** grep returns nothing
- [ ] **6.5** Commit Phase 6

---

# Phase 7 — Verify and prove nothing broke

- [ ] **7.1** Full clean build
      - `gradlew clean assembleDebug`
      - **Verify:** succeeds
- [ ] **7.2** Bump `versionCode` **1 → 2** and `versionName` **"1.0" → "2.0"**
      - `app/build.gradle.kts:15-16`
      - **Verify:** the new build is distinguishable from the old one on the phone
- [ ] **7.3** Install on the phone
      - **Verify:** app launches, logs in, reaches the dashboard
- [ ] **7.4** **Hardware regression — the important one**
      - Pair to `ResQPlug-POD-C8E720`, send a message
      - Capture Node 2 on COM3 for ~45 s
      - **Verify:** `[RX:RSSI:…|DATA:[MSG:…]]` arrives
      - This is the exact test proven on 2026-10-10. It must still pass.
- [ ] **7.5** Re-run T8 with `t08_two_node_link` if the firmware was left on `unified_node0.2`
      - **Verify:** beacons still cross between Node 1 and Node 2
- [ ] **7.6** Check all four tabs
      - **Verify:** Dashboard, SOS Hub, Messages, Settings all open and update
- [ ] **7.7** Check connect/disconnect against Firebase Console
      - **Verify:** `active_devices` marks active on open, inactive on close
- [ ] **7.8** Update the status table in [`README.md`](README.md) to all ✅
- [ ] **7.9** Commit and push
      - Message: `refactor(app)!: complete MVP restructure; version 2.0`

---

# Known outstanding — deliberately not done here

| Item | Where | Estimate |
|---|---|---|
| Implement USB-OTG for real | `model/transport/` — needs a serial library, a real write, a reader loop | ~150 lines + 1 dependency |
| Firebase Authentication | new `model/repository/AuthRepository.kt` + presenter | optional, never existed |
| Revise `ResQPlug.md` | note that Bluetooth is the validated transport, OTG the design target | 1 paragraph |
| Bundle `press_start_2p.ttf` | `RESQPLUG_DESIGN_RULES.md:90` TODO | 1 file |

---

*Created 2026-10-10 · ResQPlug Capstone 2026–2027*
