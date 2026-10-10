# RA02_BRINGUP_NODE2

Bring-up kit dedicated to **Ra-02 #2** — the second Ai-Thinker Ra-02 (SX1278 @
433 MHz) module, wired to the second 30-pin ESP32 DevKit as **Node 2** of the
ResQPlug mesh.

Companion to [`RA02_BRINGUP/`](../RA02_BRINGUP/), which is dedicated to
**Ra-02 #1** (Node 1, the proven module on COM3).

**Start here → [`TEST_LIST.md`](TEST_LIST.md)**

---

> ## ✅ END-TO-END PROVEN — 2026-10-10
>
> | Stage | Ra-02 #1 (Node 1, COM4) | **Ra-02 #2 (Node 2, COM3)** |
> |---|---|---|
> | Test E pin → pad | ✅ `345 527 533 503 502 505` | ✅ `447 755 742×4`, GND `000` |
> | T3 bare SPI (`0x12`) | ✅ `0x12`, 5/5, `PD=float=PU` | ✅ `0x12`, 5/5, 7/7 rows |
> | T6 `LoRa.begin` | ✅ `Result : true` | ✅ `ok.` |
> | T7 beacon TX | ✅ 5/5 SENT | ✅ SENT, 0 timeouts |
> | **T8 two-node link** | ✅ **PASS** | ✅ **PASS** |
> | **U1 production firmware** | ✅ `ONLINE @ 433.00 MHz` | ✅ **receiving `[BCN:POD-C8E720…]`** |
> | **U2 phone → radio** | ✅ sent | ✅ **received** |
>
> ```
> T3  [PASS] 0x12 - CHIP IS ALIVE AND RESPONDING
> T8  [PASS] a packet arrived from another node
> U1  [PASS] ONLINE @ 433.00 MHz (+17 dBm PA)
>      [RX:RSSI:-67|SNR:9.8|DATA:[BCN:POD-C8E720|...|CITIZEN|1]]
> U2  [PASS] phone -> BT -> Node 1 -> 433 MHz -> Node 2
>      [RX:RSSI:-69|SNR:9.8|DATA:[MSG:...|hello node 2 I am node 1]]
>      RSSI -66 / -69 dBm   SNR +9.8 dB   mesh relay fired live
> ```
>
> **The whole chain is proven on real hardware.** A message typed into the
> APK left a phone, crossed Bluetooth to Node 1, went out a 433 MHz radio, and
> arrived at Node 2 — where the production firmware forwarded it in exactly the
> `[RX:RSSI:…|DATA:…]` format the app parses.
>
> The three remaining gates from the original plan are all closed:
>
> - ✅ bare-module bring-up (Test E → T3 → T6 → T7)
> - ✅ two-node RF link (T8, both directions, 6/6 packets, 0 drops)
> - ✅ production firmware end-to-end (U1 beacons, U2 phone message)
>
> ⚠ **Only Bluetooth is a real transport.** The APK's USB-OTG branch
> (`ResQPlugHardwareBridge.kt:148-150`) returns success without sending and
> never reads anything back. See [`TEST_LIST.md`](TEST_LIST.md) U2 remarks.

---

## Why a second kit

The original kit exists to *diagnose a fault*. It asks "why is this module
dead?" and gives you a ladder to climb down. That ladder is now answered: the
header pins had never been soldered.

Ra-02 #2 is a different job. It isn't broken — it's **unproven**. This kit is
built around acceptance, not diagnosis:

| | Kit #1 | Kit #2 |
|---|---|---|
| Question | *Why doesn't it work?* | *Does it work, and can it talk to #1?* |
| Ladder | T1 → T7, stop at first FAIL | T3 → T6 → T7 → **T8** |
| T1, T2, T4, T5 | main path | fallback only — run them **if** T3 fails |
| New test | — | **T8 two-node link** |
| Record | `RA02_BRINGUP/TEST_LIST.md` | `TEST_LIST.md` here |

T1, T2, T4 and T5 are still copied in and ready. They simply aren't on the
happy path any more.

---

## Files

| File | What it is |
|---|---|
| [`TEST_LIST.md`](TEST_LIST.md) | The checklist. Steps, expected readings, PASS/FAIL boxes, blank Remarks. |
| [`PLAN.md`](PLAN.md) | Ordered plan with gates. |
| [`FINDINGS.md`](FINDINGS.md) | Evidence for Ra-02 #2, in the same format as kit #1. |
| [`ra02_pins.h`](ra02_pins.h) | Canonical pin definitions — source of truth. |
| `t01`…`t07/` | The seven original sketches, copied unchanged. |
| **`t08_two_node_link/`** | **New.** The two-node link test — the first test in either kit that covers *receiving*. |

> **Sync note:** the sketches and `ra02_pins.h` are copies of kit #1's. If a
> pin ever changes, change it in **both** kits. Each kit's `ra02_pins.h` is
> canonical for its own sketches; the block marked `// SYNC: ra02_pins.h` inside
> each `.ino` is the copy that must match.

---

## Naming

This project has used two labelling schemes and they do **not** line up:

| This kit says | Older docs say | What it is |
|---|---|---|
| **Node 1 / Ra-02 #1** | "module 2" in `FINDINGS.md` §1d | The proven module, on COM3, base station |
| **Node 2 / Ra-02 #2** | "module 1" in `TEST_LIST.md` | This kit's module, the mesh peer |

From here on the docs use **Node 1 / Node 2**, because that ties the label to
the ESP32 it is wired to rather than to the order the modules were examined in.

---

## Order of operations

```
PART 0  already done
  Test E  pin -> pad          ✅ 447 755 742 742 742 742, GND 000

PART 1  acceptance (this is the path)
  T3  bare SPI  -> Reg 0x42 = 0x12          ← THE GATE
  T6  LoRa.begin(433E6) -> true
  T7  beacon transmit -> 5/5 SENT           ⚠ ANTENNA ON

PART 2  integration — what kit #1 never ran
  T8  two-node link, run on BOTH boards
      -> each board prints RX from the other
      -> VERDICT T8: PASS

PART 3  end-to-end with the production firmware
  unified_node0.2 on Node 2
      -> boot banner: Reg 0x42 = 0x12, ONLINE @ 433.00 MHz
      -> receives Node 1's [BCN:...] beacons every ~25 s
      -> receives a [MSG:...] sent from the phone app
```

**Stop at the first FAIL.** If T3 doesn't read `0x12`, drop into the fallback
ladder (T1 → T2 → T4 → T5) exactly as kit #1 describes in
[`../RA02_BRINGUP/TEST_LIST.md`](../RA02_BRINGUP/TEST_LIST.md).

---

## Setup

### Libraries

**T6**, **T7** and **T8** need the LoRa library. T1–T5 and the meter tests do not.

1. Arduino IDE → *Sketch → Include Library → Manage Libraries*
2. Search **`LoRa`**
3. Install **LoRa by Sandeep Mistry** (0.8.0 is what kit #1 was verified on)

### Board & port

1. *Tools → Board → ESP32 Dev Module*
2. *Tools → Port* → Node 2's port (Node 1 is COM3; Node 2 appears as another COM port when plugged in)
3. Serial Monitor: **115200 baud**

### DT-830B dial settings

| Measuring | Dial | Probes |
|---|---|---|
| Voltage | **DCV 20** | black → `COM`, red → top `VΩmA` |
| Continuity | **diode/beep** `►\|` | one probe per wire end, **board unplugged** |
| Resistance | **2000 Ω** / **20 kΩ** | board unplugged |

In diode mode the red probe is **positive**. Test E puts red on the U.FL
antenna shell (ground) and black on each header pin.

---

## Wiring reference

```
        LEFT HEADER              RIGHT HEADER      antenna at top
      ┌────────────┐          ┌────────────┐
 row1 │ GND        │          │ GND        │ (skip)
 row2 │ GND (skip) │          │ NSS  ──────┼──> D17   ⚠ NOT D5
 row3 │ 3.3V ──────┼──> 3V3   │ MOSI ──────┼──> D23
 row4 │ RST  ──────┼──> D14   │ MISO ──────┼──> D19
 row5 │ DIO0 ──────┼──> D26   │ SCK  ──────┼──> D18
 row6 │ DIO1       │          │ DIO5       │
 row7 │ DIO2       │          │ DIO4       │
 row8 │ DIO3       │          │ GND        │
      └────────────┘          └────────────┘
```

Node 1 and Node 2 use the **identical** pin map. Nothing in the firmware is
configured per node except the Bluetooth name and the T8 `NODE_ID`.

---

## Safety

- ⚠ **3.3 V only.** `VIN` or `5V` on the Ra-02 destroys the SX1278 permanently.
- ⚠ **Antenna before transmitting.** T7, T8 and any beacon transmit with no
  antenna reflect power into the power amplifier and can burn it out.
- ⚠ **Unplug USB** before continuity or resistance measurements.
- ⚠ GPIO 5, 12, 15, 0, 2 are ESP32 **boot strapping pins** — using them for NSS
  can stop the board booting.

---

## How to run a test

1. Open the sketch's `.ino` from its own folder (Arduino requires
   folder-name = file-name).
2. For **T8 only**: set `NODE_ID` to a unique string per board.
3. Upload. Open Serial Monitor at **115200**.
4. Do the meter steps listed for that test in `TEST_LIST.md`.
5. Read the `VERDICT` block the sketch prints. Tick the boxes and write your
   reading under **REMARKS**.
