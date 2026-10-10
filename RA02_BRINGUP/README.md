# RA02_BRINGUP

Self-contained bring-up kit for the **Ai-Thinker Ra-02 (SX1278 @ 433 MHz)**
on a 30-pin ESP32 DevKit. Seven independent sketches, a full test checklist,
and a plan with explicit stop conditions.

**Start here → [`TEST_LIST.md`](TEST_LIST.md)**

> **Second module?** Ra-02 #2 has its own kit:
> [`RA02_BRINGUP_NODE2/`](../RA02_BRINGUP_NODE2/README.md). Same format, same
> pass criteria, plus a new **T8 two-node link** test that neither kit had.

---

> ## ✅ ALL TESTS PASS — 2026-10-07 · link proven 2026-10-10
>
> **Root cause: the header pins had never been soldered.** They were pushed
> into the module's holes and held by friction, so no signal ever crossed from
> a pin to the board. All 16 are now soldered, and the six breadboard wires
> were reseated one-conductor-per-hole.
>
> | Stage | Result |
> |---|---|
> | ESP32 → header pin | ✅ all 8 proven (T1 8/8) |
> | header pin → module pad | ✅ Test E 6/6 (was 0/6) |
> | module pad → silicon | ✅ Test D (`421`/`709`/`697`×4) |
> | silicon answering on SPI | ✅ **T3 `0x12`, 5/5 identical, `PD=float=PU`** |
> | **`LoRa.begin(433E6)`** | ✅ **`Result : true` — T6 PASS** |
> | **RF transmit** | ✅ **T7 PASS — 5/5 beacons SENT** |
> | **production firmware** | ✅ **`unified_node0.2` boots — `ONLINE @ 433.00 MHz`** |
> | **RF receive from the other node** | ✅ **T8 PASS 2026-10-10 — 3/3 packets, 0 drops** |
>
> ```
> T3  [PASS] 0x12 - CHIP IS ALIVE AND RESPONDING
> T6  [PASS] SUCCESS - Ra-02 online at 433.00 MHz
> T7  [PASS] all beacons transmitted
> T8  [PASS] a packet arrived from another node
>      RA02-UNIT1 <- RA02-UNIT2   RSSI -66 dBm   SNR +9.5 dB
> ```
>
> **T8 is the first proven packet-to-packet link in the project.** Ra-02 #2
> (Node 2) was brought up in
> [`RA02_BRINGUP_NODE2/`](../RA02_BRINGUP_NODE2/README.md) on the same day and
> passes T3, T6, T7 and T8 alongside this module.
>
> ```
> Node 1 = COM4  MAC 20:e7:c8:a7:a0:c0  BT ResQPlug-POD-C8E720
> Node 2 = COM3  MAC 6c:c8:40:05:7b:20
> ```
>
> Eliminated: dead silicon · wrong pinout · wrong wire positions · orientation ·
> module GND · jumper wires · NSS delivery · **unsoldered pins** ·
> **marginal breadboard joints**.
>
> **Phase 3 complete:** `GPIO 5 → 17` reconciled across 3 markdown docs and
> 4 firmware sketches — all 4 compile. See [`TEST_LIST.md`](TEST_LIST.md) §3.
>
> Full reasoning: [`FINDINGS.md`](FINDINGS.md) §1d–§1e ·
> corrected log: [`LORA_RA02_ESP32_TROUBLESHOOTING_LOG.md`](../LORA_RA02_ESP32_TROUBLESHOOTING_LOG.md) §10

---

> ## ⚡ TWO-NODE UPDATE — 2026-10-10
>
> This module (**Node 1**, `ResQPlug-POD-C8E720`) now runs the **production
> firmware** `firmware/resqplug_unified_node0.2/` and passes on real hardware:
>
> ```
> [LORA]: Probe 1 (Standard CS) Reg 0x42 = 0x12
> [LORA]: 🎉 SX1278 Radio Initialized Successfully at 433.00 MHz (+17 dBm)!
>    [ DONGLE ID   ]: POD-C8E720
>    [ SILICON MAC ]: C0:A0:A7:C8:E7:20
>    [ BLUETOOTH   ]: ResQPlug-POD-C8E720 (SPP READY)
>    [ LORA RADIO  ]: ONLINE @ 433.00 MHz (+17 dBm PA)
> ```
>
> First time the production firmware has run on hardware. Probe 1 succeeds on
> the first attempt — no software-CS fallback needed — so NSS on GPIO 17 is
> confirmed correct for the library path too, not just the bit-bang test.
>
> Four firmware fixes shipped with it: a real offline TX queue (the old
> `[ACK:TX_QUEUED_SIMULATED]` discarded messages), `pinMode(4, OUTPUT)` for the
> external LED, a non-blocking mesh-relay backoff, and `[RELAY_SKIP]` logging.
>
> **Node 2** — the second Ra-02 module — is being brought up in
> [`../RA02_BRINGUP_NODE2/`](../RA02_BRINGUP_NODE2/README.md). Its pins are
> soldered and Test E passes 6/6. The remaining step is the link test, which is
> new ground: **this module has never been tested for receiving either.**

---

## Files

| File | What it is |
|---|---|
| [`TEST_LIST.md`](TEST_LIST.md) | The checklist. Steps, expected meter readings, PASS/FAIL boxes, blank Remarks. |
| [`PLAN.md`](PLAN.md) | Ordered plan with gates and the decision table. |
| [`FINDINGS.md`](FINDINGS.md) | Evidence so far: meter readings, doc contradictions, the alive/dead argument. |
| [`ra02_pins.h`](ra02_pins.h) | Canonical pin definitions — source of truth. |
| `t01`…`t07/` | Seven independent Arduino sketches. |
| [`../RA02_BRINGUP_NODE2/`](../RA02_BRINGUP_NODE2/README.md) | Sibling kit for Ra-02 #2, plus the new **T8 two-node link** test. |

---

## Order of operations

```
PHASE 0  meter, no sketch
  M0  power at LEFT [3]   ✅ done — 3.20 V
  M1  RST  at LEFT [4]    ← the open question. Do this first.

PHASE 1  sketches, stop at first FAIL
  T1  pin states        T5  NSS auto-discovery
  T2  reset release         (run if the NSS pin is still uncertain)
  T3  bit-bang SPI      T6  LoRa.begin()
  T4  hardware SPI      T7  beacon transmit  ⚠ ANTENNA ON

PHASE 2  record results in TEST_LIST.md
PHASE 3  reconcile docs + firmware   ⛔ blocked until T5 names the pin
```

---

## Setup

### Libraries

Only **T6** and **T7** need a library:

1. Arduino IDE → *Sketch → Include Library → Manage Libraries*
2. Search **`LoRa`**
3. Install **LoRa by Sandeep Mistry**

T1–T5 use nothing but the built-in core — no installs.

### Board & port

1. *Tools → Board → ESP32 Dev Module* (or your exact DevKit variant)
2. *Tools → Port* → select your COM port
3. Serial Monitor: **115200 baud**

### DT-830B dial settings

| Measuring | Dial | Probes |
|---|---|---|
| Voltage | **DCV 20** | black → `COM`, red → top `VΩmA` |
| Continuity | **diode/beep** `►\|` | one probe per wire end, **board unplugged** |
| Resistance | **2000 Ω** / **20 kΩ** | board unplugged |

Red probe never goes in the `10ADC` jack. Expect roughly ±0.1 V error on the
20 V range — **3.20–3.35 V is a healthy 3.3 V rail.**

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

---

## Safety

- ⚠ **3.3 V only.** `VIN` or `5V` on the Ra-02 destroys the SX1278 permanently.
- ⚠ **Antenna before transmitting.** T7 with no antenna reflects power into the
  power amplifier and can burn it out. Seat the IPEX 433 MHz antenna first.
- ⚠ **Unplug USB** before continuity or resistance measurements.
- ⚠ GPIO 5, 12, 15, 0, 2 are ESP32 **boot strapping pins** — using them for NSS
  can stop the board booting. T5 flags it if one of them is your only answer.

---

## How to run a test

1. Open the sketch's `.ino` from its own folder (Arduino requires
   folder-name = file-name).
2. Set `PIN_NSS` if T5 told you it isn't 17.
3. Upload. Open Serial Monitor at **115200**.
4. Do the meter steps listed for that test in `TEST_LIST.md`.
5. Read the `VERDICT` block the sketch prints — it maps your result to the
   next physical action. Tick the boxes and write your reading under
   **REMARKS**.

**Stop at the first FAIL.** Later tests build on earlier ones and will produce
misleading output if an upstream fault is still present.
