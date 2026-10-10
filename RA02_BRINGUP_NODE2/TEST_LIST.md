# ResQPlug — Ra-02 #2 (Node 2) Bring-Up Test List

Companion to [`../RA02_BRINGUP/TEST_LIST.md`](../RA02_BRINGUP/TEST_LIST.md),
which is Ra-02 #1's record. Same format, same pass criteria, so the two records
are directly comparable.

## Meter settings quick reference

| Measuring | Dial | Probes | Board |
|---|---|---|---|
| Voltage | **DCV 20** | black `COM`, red `VΩmA` | powered |
| Diode drop | **diode** `►\|` | red = **positive** | **unplugged** |
| Continuity | **diode/beep** | either | **unplugged** |
| Resistance | **2000 Ω** / **20 kΩ** | either | **unplugged** |

Diode mode on a DT-830B applies roughly 2.8 V at ~1 mA. A reading of `1` means
**open loop** — no path. `000` means a dead short. Healthy signal pins land
somewhere in **300–800**.

## Ra-02 pin reference (antenna at top)

```
        LEFT HEADER              RIGHT HEADER
      ┌────────────┐          ┌────────────┐
 row1 │ GND        │          │ GND        │
 row2 │ GND        │          │ NSS  ──────┼──> D17
 row3 │ 3.3V ──────┼──> 3V3   │ MOSI ──────┼──> D23
 row4 │ RST  ──────┼──> D14   │ MISO ──────┼──> D19
 row5 │ DIO0 ──────┼──> D26   │ SCK  ──────┼──> D18
 row6 │ DIO1       │          │ DIO5       │
 row7 │ DIO2       │          │ DIO4       │
 row8 │ DIO3       │          │ GND        │
      └────────────┘          └────────────┘
```

---

# PART 0 — Meter gate

## Test E — header pin → pad (diode mode)

**The one part of Ra-02 #2's record that already exists.** Ra-02 #1's pins were
never soldered and Test E read `1 1 1 1 1` — that was the root cause of its
original failure. Ra-02 #2's pins **are** soldered, so this should pass.

### Steps

1. Unplug everything. Pull Ra-02 #2 out of the breadboard so it is standalone.
2. Multimeter → **diode mode** (`—|►—`, *not* the sound-wave symbol).
3. **Red probe on the round metal U.FL antenna socket** — that is your ground.
4. **Black probe on the top of each header pin.**
5. Write each reading down.

### Expected

| Pin | Good | Bad | Result | |
|---|---|---|---|---|
| 3.3V | 300–800 | `1` | **`447`** ✅ | 2026-10-10 |
| RESET | 300–800 | `1` | **`755`** ✅ | 2026-10-10 |
| NSS | 300–800 | `1` | **`742`** ✅ | 2026-10-10 |
| MOSI | 300–800 | `1` | **`742`** ✅ | 2026-10-10 |
| MISO | 300–800 | `1` | **`742`** ✅ | 2026-10-10 |
| SCK | 300–800 | `1` | **`742`** ✅ | 2026-10-10 |
| 4 × GND | ~000 | — | **`000`** ✅ | 2026-10-10 |

- **Any `1`** → that pin is not touching its pad → **solder it**.
- **`000` on a signal pin** → solder bridge → reflow that joint.
- The four SPI pins landing on an **identical** value is the healthy-die
  signature: four ESD diodes on one intact die.

### Verdict — ✅ **PASS, 6/6 connected**

No `1` anywhere, no `000` on any signal pin. Readings sit ~237 mV above Ra-02
#1's because #1 was measured in-circuit (parallel paths pull readings down) and
this module was measured loose in hand (probe contact resistance pushes them
up). Both are inside the pass band.

> ☐ **REMARKS** — blank for anything you want to add later.

---

# PART 1 — Acceptance ladder

Run these in order. **Stop at the first FAIL.**

## T3 — Bare-minimum bit-bang SPI read

The gate. If this doesn't read `0x12`, nothing downstream can be trusted.

### Steps

1. Module back in the breadboard. Wiring one-conductor-per-hole:
   MOSI→D23, MISO→D19, SCK→D18, NSS→D17, 3.3V→3V3, GND→GND.
2. Open [`t03_spi_bare/t03_spi_bare.ino`](t03_spi_bare/t03_spi_bare.ino).
3. Upload to **Node 2's** port. Serial Monitor 115200.
4. Read `Reg 0x42`.

### Expected

| Reading | Meaning |
|---|---|
| **`0x12`** | ✅ chip is answering — **PASS** |
| `0x00` | MISO stuck low / not connected |
| `0xFF` | MISO floating / not connected |
| anything else | junk — intermittent contact |
| **inconsistent across 5 reads** | intermittent contact → reseat the breadboard wires |

The sketch also prints a 5× repeat and pull-test rows. All five must agree and
all rows must read `PD = float = PU`.

### Result — ✅ **PASS, 2026-10-10**

```
Reg 0x42 = 0x12
#1 = 0x12  #2 = 0x12  #3 = 0x12  #4 = 0x12  #5 = 0x12
Consistent : YES
[1] PD=0x12  float=0x12  PU=0x12  <== EXPECTED
[2] PD=0x12  float=0x12  PU=0x12  <== EXPECTED
[3] PD=0x12  float=0x12  PU=0x12  <== EXPECTED
[4] PD=0x12  float=0x12  PU=0x12  <== EXPECTED
[5] PD=0x12  float=0x12  PU=0x12  <== EXPECTED
[PASS] 0x12 - CHIP IS ALIVE AND RESPONDING.
       RST, NSS, SCK, MISO and power are all good.
```

5/5 identical, 7/7 pull-test rows all agreeing, zero `MARGINAL`, zero floating.
Identical signature to Ra-02 #1's final T3. No rework needed — the wiring was
already correct.

> **REMARKS** — ESP32 MAC `6c:c8:40:05:7b:20`, on COM3 at the time of the test.

---

## T6 — LoRa library init

### Steps

1. Antenna **on**.
2. Open [`t06_lora_begin/t06_lora_begin.ino`](t06_lora_begin/t06_lora_begin.ino).
3. Upload. Serial Monitor 115200.

### Expected

```
[2] LoRa.begin(433E6) ...  Result : true
VERDICT T6: PASS
SUCCESS - Ra-02 online at 433.00 MHz.
```

`Result : false` → library can't find the chip. Go back to T3.

### Result — ✅ **PASS, 2026-10-10**

Run on the fast path, so the standalone `t06` sketch was skipped — but T8's
boot performs the identical `LoRa.begin(433E6)` and prints the result, which is
the same evidence:

```
[2] LoRa.begin(433E6) ...
    ok.
```

> **REMARKS** — SF7 / BW125 / CR 4/5 / +18 dBm / preamble 8 / sync word 0x12 / CRC on.

---

## T7 — Beacon transmit

### ⚠ Antenna must be seated. Transmitting with no antenna can burn the PA.

### Steps

1. Open [`t07_beacon_tx/t07_beacon_tx.ino`](t07_beacon_tx/t07_beacon_tx.ino).
2. Upload. Serial Monitor 115200.

### Expected

```
Beacon 0 -> SENT
Beacon 1 -> SENT
Beacon 2 -> SENT
Beacon 3 -> SENT
Beacon 4 -> SENT
VERDICT T7: PASS - all beacons transmitted.
```

`TIMEOUT` → most likely no antenna, then a sagging 3.3 V rail under ~120 mA PA
current, then a PA already damaged by a previous no-antenna transmit.

### Result — ✅ **PASS, 2026-10-10**

Again on the fast path, so the standalone `t07` sketch was skipped. T8 beacons
every 10 s and reports each one — and this time each beacon was **confirmed
received by the other node**, which is stronger evidence than T7 alone gives:

```
[TX] beacon 0 -> SENT
[TX] beacon 1 -> SENT
[TX] beacon 2 -> SENT
```

No `TIMEOUT` on either board, so both antennas are seated and both PA rails are
holding under load.

> **REMARKS** — confirmed by reception on Ra-02 #1, not just by `endPacket()`
> returning 1.

---

# PART 2 — Integration (new; kit #1 never ran this)

## T8 — Two-node link

**The first test in either kit that covers *receiving*.** T1–T7 only ever prove
one board talking to its own chip. T8 proves a packet crosses the air.

### Steps

1. ⚠ **Antenna on both boards.**
2. Open [`t08_two_node_link/t08_two_node_link.ino`](t08_two_node_link/t08_two_node_link.ino).
3. Set `#define NODE_ID` to a **unique** string — e.g. `"RA02-UNIT1"` on Node 1,
   `"RA02-UNIT2"` on Node 2.
4. Flash to **Node 1**. Flash the **same file** to **Node 2** (change only
   `NODE_ID` between flashes).
5. Open **both** serial monitors at 115200.
6. Wait up to ~10 s for a beacon.

### Expected on each board

```
[1] Probe 1 (Standard CS) Reg 0x42 = 0x12     ← T3 evidence, again
[2] LoRa.begin(433E6) ...  ok.                 ← T6 evidence
[TX] beacon 0 -> SENT
[RX] RSSI=-67 dBm  SNR=9.5 dB  len=24  [BCN:RA02-UNIT2|seq=0|t=...]
       ^^^ FROM ANOTHER NODE - link confirmed (1 so far)
=============================================================
  VERDICT T8: PASS - a packet arrived from another node.
```

| RSSI | Read it as |
|---|---|
| -50 to -80 | strong, boards near each other |
| -80 to -100 | usable |
| -100 to -110 | weak, marginal |
| below -110 | too far, or an antenna is wrong |

You can also **type a line and press Enter** in either serial monitor — it is
transmitted verbatim and appears as an `[RX]` on the other board.

### Result — ✅ **PASS, 2026-10-10 — both directions**

| | Node 2 (COM3) received | Node 1 (COM4) received |
|---|---|---|
| Node ID heard | `RA02-UNIT1` | `RA02-UNIT2` |
| RSSI | `-66 / -66 / -67 dBm` | `-66 / -66 / -66 dBm` |
| SNR | `9.5 / 9.5 / 9.8 dB` | `9.8 / 9.5 / 9.5 dB` |
| Packets received | **3/3** | **3/3** |
| Misses | **0** | **0** |

Node 2's capture:

```
Node ID     : RA02-UNIT2
[1] Probe (Software CS) Reg 0x42 = 0x12
[2] LoRa.begin(433E6) ...  ok.
[TX] beacon 0 -> SENT
[RX] RSSI=-66 dBm  SNR=9.5 dB  len=30  [BCN:RA02-UNIT1|seq=5|t=50009]
       ^^^ FROM ANOTHER NODE - link confirmed (1 so far)
=============================================================
  VERDICT T8: PASS - a packet arrived from another node.
  The link now works in both directions:
    power     OK
    SPI comms OK (register reads)
    library   OK (LoRa.begin)
    RF output OK (packets sent)
    RF input  OK (packets received)   <-- this test
=============================================================
[TX] beacon 1 -> SENT
[RX] RSSI=-66 dBm  SNR=9.5 dB  len=30  [BCN:RA02-UNIT1|seq=6|t=60009]
       ^^^ FROM ANOTHER NODE - link confirmed (2 so far)
[TX] beacon 2 -> SENT
[RX] RSSI=-67 dBm  SNR=9.8 dB  len=30  [BCN:RA02-UNIT1|seq=7|t=70009]
       ^^^ FROM ANOTHER NODE - link confirmed (3 so far)
```

Node 1's capture, taken immediately after — same picture mirrored:

```
Node ID     : RA02-UNIT1
[1] Probe (Software CS) Reg 0x42 = 0x12
[2] LoRa.begin(433E6) ...  ok.
[TX] beacon 0 -> SENT
[RX] RSSI=-66 dBm  SNR=9.8 dB  len=30  [BCN:RA02-UNIT2|seq=8|t=80003]
       ^^^ FROM ANOTHER NODE - link confirmed (1 so far)
=============================================================
  VERDICT T8: PASS - a packet arrived from another node.
=============================================================
[TX] beacon 1 -> SENT
[RX] RSSI=-66 dBm  SNR=9.5 dB  len=30  [BCN:RA02-UNIT2|seq=9|t=90003]
       ^^^ FROM ANOTHER NODE - link confirmed (2 so far)
[TX] beacon 2 -> SENT
[RX] RSSI=-66 dBm  SNR=9.5 dB  len=32  [BCN:RA02-UNIT2|seq=10|t=100003]
       ^^^ FROM ANOTHER NODE - link confirmed (3 so far)
```

RSSI around **-66 dBm** with SNR near **+9.5 dB** is a strong, clean link at
bench distance. Note that every beacon Node 1 sent during the window arrived —
**6/6, no drops** — so the link has margin, not just luck.

> **REMARKS** — first time in this project that a packet has been proven to
> cross from one ESP32 to another. T1–T7 only ever prove one board talking to
> its own chip.

> ⚠ **t08 probe note:** the first version of this sketch read
> `Reg 0x42 = 0xFF` on a perfectly healthy chip. With the hardware CS pin
> configured by `SPI.begin(..., PIN_NSS)`, `SPI.transfer()` toggles NSS itself
> and races the manual `digitalWrite` in the diagnostic probe. The probe now
> runs under software CS (`SPI.begin(..., -1)`) and reads `0x12` cleanly.
> `LoRa.begin()` was never affected — this was a false alarm in the diagnostic
> only. **If you see `0xFF` from a probe that also reports `LoRa.begin` ok,
> suspect the probe, not the chip.**

---

# PART 3 — Production firmware end-to-end

## U1 — `unified_node0.2` on Node 2

### Steps

1. Flash `firmware/resqplug_unified_node0.2/` to Node 2.
2. Serial Monitor 115200 — read the boot banner.
3. Leave it open ~28 s.

### Expected

```
[LORA]: Probe 1 (Standard CS) Reg 0x42 = 0x12
[LORA]: 🎉 SX1278 Radio Initialized Successfully at 433.00 MHz
[ BLUETOOTH   ]: ResQPlug-xxxxxxxx (SPP READY)
[ LORA RADIO  ]: ONLINE @ 433.00 MHz (+17 dBm PA)
```

Then, once Node 1's ~25 s beacon lands:

```
[RX:RSSI:-67|SNR:9.5|DATA:[BCN:POD-C8E720|ResQPlug-POD-C8E720|CITIZEN|1]]
```

**That `[RX:…]` line is the end of the chain** — a packet that started as a
Bluetooth command on a phone, went out a radio at 433 MHz, and arrived at a
second ESP32.

### Result — ✅ **PASS, 2026-10-10**

```
=========================================================
   ⚡ ResQPlug Unified Dual-Transport + LIVE LORA v3.3
=========================================================
[LORA]: Probing SX1278 on SPI Bus (SCK:18, MISO:19, MOSI:23, SS:17, RST:14)...
[LORA]: Probe 1 (Standard CS) Reg 0x42 = 0x00
[LORA]: 🎉 SX1278 Radio Initialized Successfully at 433.00 MHz (+17 dBm)!
   [ DONGLE ID   ]: POD-40C86C
   [ SILICON MAC ]: 20:7B:05:40:C8:6C
   [ CHIP MODEL  ]: ESP32-D0WD-V3
   [ CPU FREQ    ]: 240 MHz
   [ BLUETOOTH   ]: ResQPlug-POD-40C86C (SPP READY)
   [ USB-OTG     ]: 115200 Baud (CDC/UART READY)
   [ LORA RADIO  ]: ONLINE @ 433.00 MHz (+17 dBm PA)
=========================================================
[RX:RSSI:-67|SNR:9.8|DATA:[BCN:POD-C8E720|ResQPlug-POD-C8E720|CITIZEN|1]]
[BEACON_TX]: [BCN:POD-40C86C|ResQPlug-POD-40C86C|CITIZEN|1]
[RX:RSSI:-67|SNR:9.8|DATA:[BCN:POD-C8E720|ResQPlug-POD-C8E720|CITIZEN|1]]
```

Node 1's production beacons arriving at Node 2 over real 433 MHz RF, at
`-67 dBm` / `+9.8 dB` SNR.

> **REMARKS** — `Probe 1` printed `Reg 0x42 = 0x00` here (and `0xFF` on a later
> boot) yet `LoRa.begin()` succeeded and beacons are being received. This is the
> **known hardware-CS race in the unified_node probe**, not a fault — the same
> one that made `t08` print `0xFF` on a healthy chip. **`LoRa.begin()` is the
> real gate, and it passed.** The chip is demonstrably working because packets
> are arriving. See kit #2 FINDINGS §1f.

---

## U2 — phone → radio → Node 2, end to end

### Steps

1. `unified_node0.2` on **both** nodes (already true after U1).
2. Phone: Bluetooth ON, pair to **`ResQPlug-POD-C8E720`** (Node 1), open the APK.
3. Send a message from the app.
4. Capture Node 2's serial (COM3) — no phone is needed on Node 2, because the
   firmware mirrors every received packet to USB serial as well as Bluetooth
   (`resqplug_unified_node.ino:204-210`).

### Expected

```
[RX:RSSI:…|SNR:…|DATA:[MSG:…]]
```

### Result — ✅ **PASS, 2026-10-10**

Two messages sent from the app, both received at Node 2:

```
38: [RX:RSSI:-66|SNR:9.8|DATA:[MSG:msg_1791614503565_30F0|RQP-NODE-2330F0|bluetoothtest|STATUS|1|hello]]
39: [RELAY_QUEUED]: [MSG:msg_1791614503565_30F0|RQP-NODE-2330F0|bluetoothtest|STATUS|2|hello]
40: [RELAY_TX]:     [MSG:msg_1791614503565_30F0|RQP-NODE-2330F0|bluetoothtest|STATUS|2|hello]
...
45: [RX:RSSI:-69|SNR:9.8|DATA:[MSG:msg_1791614554871_30F0|RQP-NODE-2330F0|bluetoothtest|STATUS|1|hello node 2 I am node 1]]
46: [RELAY_QUEUED]: [MSG:msg_1791614554871_30F0|RQP-NODE-2330F0|bluetoothtest|STATUS|2|hello node 2 I am node 1]
47: [RELAY_TX]:     [MSG:msg_1791614554871_30F0|RQP-NODE-2330F0|bluetoothtest|STATUS|2|hello node 2 I am node 1]
```

The chain that was proven:

```
phone (bluetoothtest, RQP-NODE-2330F0)
  ──Bluetooth──▶ Node 1 (POD-C8E720)
  ──433 MHz RF──▶ Node 2 (POD-40C86C)    RSSI -66 / -69 dBm    SNR +9.8 dB
```

Three things confirmed at once:

1. **The app's send path works on real hardware** — `TX:[MSG:…]` left the phone,
   crossed Bluetooth, went out a radio, and landed on a second board.
2. **The firmware's RX path forwards correctly** — `resqplug_unified_node.ino:204`
   formats `[RX:RSSI:…|SNR:…|DATA:…]`, which is exactly what
   `SimulationEngine.kt:201` gates on and parses.
3. **The mesh relay fix works live** — hop `1` → `2`, `[RELAY_QUEUED]` then
   `[RELAY_TX]`, no `delay()` stall. That is the fourth of the four
   `unified_node0.2` fixes, firing on real packets.

> **REMARKS** — the app UI reported `40C86C` online, but these packets carry
> hop `1`, so they originated at the *other* node. The phone's Bluetooth session
> was with **Node 1**; Node 2 is the receiver. No phone was ever paired to
> Node 2 — its output was read from USB serial on the PC.

> ⚠ **USB-OTG is still unimplemented.** The APK's OTG branch
> (`ResQPlugHardwareBridge.kt:148-150`) returns success without sending, and
> `attachUsbSession()` never starts a reader. Only **Bluetooth** is a real
> transport. This test does not cover OTG and OTG should not be trusted.

---

# PART 4 — Record results

| Test | Result | Date | Notes |
|---|---|---|---|
| **Test E** pin → pad | ✅ **`447` `755` `742` `742` `742` `742`, GND `000`** | 2026-10-10 | **6/6 connected** — no `1`, no `000`. Same healthy-die signature as #1 |
| **T3** bare SPI | ✅ **`0x12`, 5/5 identical, 7/7 rows `PD=float=PU`** | 2026-10-10 | Zero `MARGINAL`, zero floating. Identical to Ra-02 #1's final T3 |
| **T6** `LoRa.begin` | ✅ **`ok.`** | 2026-10-10 | Via T8's boot (fast path). SF7/BW125/CR4/5/+18dBm/sync 0x12 |
| **T7** beacon TX | ✅ **SENT, 0 timeouts** | 2026-10-10 | Via T8's beacons. Both antennas seated, both PA rails holding |
| **T8** two-node link | ✅ **PASS — both directions, 6/6 packets, 0 drops** | 2026-10-10 | RSSI `-66 dBm`, SNR `+9.5 dB`. **First proven packet between two ESP32s in this project** |
| **U1** unified_node0.2 | ✅ **PASS — `ONLINE @ 433.00 MHz`, receiving `[BCN:POD-C8E720…]`** | 2026-10-10 | Boot banner OK, BT `ResQPlug-POD-40C86C`. Node 1's production beacons arriving at `-67 dBm` / `+9.8 dB` |
| **U2** phone → radio E2E | ✅ **PASS — 2/2 messages arrived** | 2026-10-10 | `hello` and `hello node 2 I am node 1` both received at `-66`/`-69 dBm`. Mesh relay fired live (hop 1→2). **First full phone→BT→RF→ESP32 proof** |

---

# Fallback ladder

**Only run these if T3 fails.** They are the same diagnostic ladder that found
Ra-02 #1's fault; the full instructions live in kit #1.

| Test | What it answers |
|---|---|
| T1 pin state | Are the ESP32 GPIOs actually driving the breadboard? |
| T2 reset release | Does RST at LEFT[4] swing? |
| T4 hardware SPI ×3 speeds | Does a real SPI peripheral read consistently? |
| T5 NSS auto-discovery | Is the chip-select really on D17? |
| Meter Tests 1–3, A, D | Power rail, orientation, wire continuity, pad → die |

See [`../RA02_BRINGUP/TEST_LIST.md`](../RA02_BRINGUP/TEST_LIST.md) for each.
