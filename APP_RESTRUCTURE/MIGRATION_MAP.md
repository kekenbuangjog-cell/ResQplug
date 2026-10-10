# MIGRATION_MAP — every file's old path → new path

Snapshot taken 2026-10-10, before any code was moved.

```
Baseline:  32 Kotlin files · 5,359 lines
           app versionCode 1 · versionName "1.0"
           commit b19928c
```

Use this as the lookup table when you can't find something. Update the
**Status** column as phases complete.

---

## Summary by destination

| Destination | Files | Lines | Phase |
|---|---:|---:|---|
| **DELETED** (`activity7/`) | 12 | 1,562 | 3 |
| `model/data/` | 4 | 62 | 1 |
| `model/engine/` | 1 | 686 | 5 |
| `model/repository/` | 3 *(new)* | *(extracted)* | 4 |
| `model/transport/` | 4 | 528 | 2 |
| `presenter/` | 8 *(new)* | *(extracted)* | 5 |
| `view/` *(thinned, not moved)* | 2 | 1,149 → ~350 | 6 |
| `view/fragments/` | 4 | 700 | 1 |
| `view/adapters/` | 4 | 432 | 1 |
| `view/widgets/` | 1 | 240 | 1 |
| **Total** | **32** | **5,359** | |

---

## DELETED — `activity7/` (Phase 3) · 12 files · 1,562 lines

The LED/Firebase coursework package. Pattern borrowed, package removed.
`FirebaseAuth` appears nowhere else — verified 2026-10-10.

| Old path | Lines | Status |
|---|---:|---|
| `activity7/Activity7AuthActivity.kt` | 148 | ⬜ delete |
| `activity7/Activity7ConnectionActivity.kt` | 225 | ⬜ delete |
| `activity7/AuthContract.kt` | 19 | ⬜ delete |
| `activity7/AuthPresenter.kt` | 110 | ⬜ delete |
| `activity7/ConnectionContract.kt` | 32 | ⬜ delete |
| `activity7/ConnectionPresenter.kt` | 98 | ⬜ delete |
| `activity7/DeviceScanAdapter.kt` | 39 | ⬜ delete |
| `activity7/HardwareSessionManager.kt` | 137 | ⬜ delete |
| `activity7/LedActivity.kt` | 227 | ⬜ delete |
| `activity7/LedContract.kt` | 27 | ⬜ delete |
| `activity7/LedPresenter.kt` | 189 | ⬜ delete |
| `activity7/LedRepository.kt` | 311 | ⬜ delete |

### Layouts deleted with it (5)

| Old path | Referenced by | Status |
|---|---|---|
| `res/layout/activity_led.xml` | `LedActivity` only | ⬜ delete |
| `res/layout/activity_a7_login.xml` | `Activity7AuthActivity` only | ⬜ delete |
| `res/layout/activity_a7_connection.xml` | `Activity7ConnectionActivity` only | ⬜ delete |
| `res/layout/item_a7_bluetooth_device.xml` | `DeviceScanAdapter` only | ⬜ delete |
| `res/layout/dialog_clearance_success.xml` | activity7 only | ⬜ delete |

### Manifest entries removed (3)

`.activity7.Activity7AuthActivity` · `.activity7.Activity7ConnectionActivity` ·
`.activity7.LedActivity`

---

## `model/data/` (Phase 1) · 4 files · 62 lines

| Old path | New path | Lines | Status |
|---|---|---:|---|
| `simulation/MeshNode.kt` | `model/data/MeshNode.kt` | 13 | ⬜ |
| `simulation/ChatMessage.kt` | `model/data/ChatMessage.kt` | 17 | ⬜ |
| `simulation/SosIncident.kt` | `model/data/SosIncident.kt` | 15 | ⬜ |
| `simulation/UserRole.kt` | `model/data/UserRole.kt` | 17 | ⬜ |

---

## `model/engine/` (Phase 5) · 1 file · 686 lines

| Old path | New path | Lines | Status |
|---|---|---:|---|
| `simulation/SimulationEngine.kt` | `model/engine/MeshEngine.kt` | 686 | ⬜ rename in 5.5, split in 5.6 |

> **Rename only in 5.5.** Splitting into `PacketParser` / `SeenCache` /
> `TxQueue` / `MeshRelay` is task **5.6** and needs its own commit and its own
> hardware regression run. It is the riskiest single step in the project.

---

## `model/repository/` (Phase 4) · 3 new files

All new. Sources are **extracted**, not moved — the original Activities lose
these methods.

| New path | Extracted from | Status |
|---|---|---|
| `model/repository/DeviceRepository.kt` | `DashboardActivity` `:563` `:590` `:606` `:645` `:288` · `MainActivity` `:577` | ⬜ |
| `model/repository/MessageRepository.kt` | `SimulationEngine` send/persist path | ⬜ |
| `model/repository/SosRepository.kt` | `SimulationEngine` SOS create/list/resolve | ⬜ |

> **Keep the Firestore collection name `active_devices` and every field string
> byte-identical.** This is pure extraction — no schema change.

---

## `model/transport/` (Phase 2) · 4 files · 528 lines

| Old path | New path | Lines | Status |
|---|---|---:|---|
| `hardware/ResQPlugHardwareBridge.kt` | `model/transport/HardwareBridge.kt` | 200 | ⬜ rename class too |
| `bluetooth/BluetoothRadioHelper.kt` | `model/transport/BluetoothTransport.kt` | 109 | ⬜ rename class too |
| `usb/UsbDeviceHelper.kt` | `model/transport/UsbDeviceHelper.kt` | 182 | ⬜ |
| `usb/UsbConnectionReceiver.kt` | `model/transport/UsbConnectionReceiver.kt` | 37 | ⬜ |

### Plus one deliberate behaviour change (Phase 2.5–2.6)

`HardwareBridge.kt` — the OTG stub must stop reporting success:

| Location | Today | After |
|---|---|---|
| `sendCommand()` `Transport.USB_OTG` branch (`:148-150`) | returns `true` | returns `false` + `Log.w` |
| `isConnected()` `Transport.USB_OTG` branch (`:169`) | returns `true` | returns `false` |

---

## `presenter/` (Phase 5) · 8 new files

All new. One Contract + one Presenter per screen.

| New path | Responsibilities taken from | Status |
|---|---|---|
| `presenter/MainContract.kt` | — | ⬜ |
| `presenter/MainPresenter.kt` | `MainActivity` — BT permissions, attach/detach, connection sequence, transport selection | ⬜ |
| `presenter/DashboardContract.kt` | — | ⬜ |
| `presenter/DashboardPresenter.kt` | `DashboardActivity` — heartbeat, freshness ticker, role gating, transport status | ⬜ |
| `presenter/MessagesContract.kt` | — | ⬜ |
| `presenter/MessagesPresenter.kt` | `MessagesFragment` + engine — send/route | ⬜ |
| `presenter/SosContract.kt` | — | ⬜ |
| `presenter/SosPresenter.kt` | `SosHubFragment` + engine — raise/resolve, responder gating | ⬜ |

---

## `view/` (Phase 1 move, Phase 6 thinning) · 2 files

| Old path | New path | Lines now | Target | Status |
|---|---|---:|---|---|
| `MainActivity.kt` | `view/MainActivity.kt` | 566 | ~150 | ⬜ |
| `DashboardActivity.kt` | `view/DashboardActivity.kt` | 583 | ~200 | ⬜ |

Both must end with **zero** `import com.google.firebase.*`.

---

## `view/fragments/` (Phase 1) · 4 files · 700 lines

| Old path | New path | Lines | Status |
|---|---|---:|---|
| `ui/fragments/DashboardFragment.kt` | `view/fragments/DashboardFragment.kt` | 162 | ⬜ |
| `ui/fragments/MessagesFragment.kt` | `view/fragments/MessagesFragment.kt` | 302 | ⬜ |
| `ui/fragments/SettingsFragment.kt` | `view/fragments/SettingsFragment.kt` | 170 | ⬜ |
| `ui/fragments/SosHubFragment.kt` | `view/fragments/SosHubFragment.kt` | 66 | ⬜ |

---

## `view/adapters/` (Phase 1) · 4 files · 432 lines

| Old path | New path | Lines | Status |
|---|---|---:|---|
| `ui/ActiveContactAdapter.kt` | `view/adapters/ActiveContactAdapter.kt` | 67 | ⬜ |
| `ui/ConversationAdapter.kt` | `view/adapters/ConversationAdapter.kt` | 130 | ⬜ |
| `ui/MessageAdapter.kt` | `view/adapters/MessageAdapter.kt` | 125 | ⬜ |
| `ui/SosIncidentAdapter.kt` | `view/adapters/SosIncidentAdapter.kt` | 110 | ⬜ |

---

## `view/widgets/` (Phase 1) · 1 file · 240 lines

| Old path | New path | Lines | Status |
|---|---|---:|---|
| `ui/StarfieldView.kt` | `view/widgets/StarfieldView.kt` | 240 | ⬜ |

---

## Packages that disappear entirely

| Package | Files | Goes to |
|---|---:|---|
| `activity7/` | 12 | **deleted** |
| `simulation/` | 5 | `model/data/` (4) + `model/engine/` (1) |
| `hardware/` | 1 | `model/transport/` |
| `bluetooth/` | 1 | `model/transport/` |
| `usb/` | 2 | `model/transport/` |
| `ui/` | 9 | `view/fragments/` (4) + `view/adapters/` (4) + `view/widgets/` (1) |

After Phase 6 the tree holds exactly **three** top-level packages:
`model/`, `presenter/`, `view/`.

---

*Snapshot 2026-10-10 · commit b19928c · ResQPlug Capstone 2026–2027*
