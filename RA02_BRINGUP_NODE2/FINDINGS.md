# Findings — Ra-02 #2 (Node 2)

Evidence log for the **second** Ai-Thinker Ra-02 module. Same format and the
same pass criteria as [`../RA02_BRINGUP/FINDINGS.md`](../RA02_BRINGUP/FINDINGS.md)
so the two records are directly comparable.

---

## 1a. What this module is, and why it gets its own kit

| | Ra-02 #1 (Node 1) | Ra-02 #2 (Node 2) |
|---|---|---|
| ESP32 | **COM4**, MAC `20:e7:c8:a7:a0:c0` | **COM3**, MAC `6c:c8:40:05:7b:20` |
| Role in the mesh | base station | peer |
| Bluetooth name | `ResQPlug-POD-C8E720` | derived from its own eFuse MAC |
| History | failed T3 with floating MISO → pins never soldered → **soldered + wires reseated → T3/T6/T7 all PASS** | never diagnosed; **soldered from the start** |
| Diagnostics run | full ladder, T1–T7 + **T8** + meter Tests 1–3, A, D, E | **Test E, T3, T6, T7, T8** |

### A naming note worth reading twice

The original kit's notes call the proven module **"module 2"**
(`../RA02_BRINGUP/FINDINGS.md:226`) while `TEST_LIST.md:397` calls the first
failed one **"module 1"**. Those labels were assigned in the order the modules
were *examined*, and they do not match the labels used in conversation.

**This kit uses Node 1 / Node 2 throughout**, because that ties the name to the
ESP32 the module is wired to, not to the order someone happened to look at it.

| This kit | Older notes |
|---|---|
| Node 1 / Ra-02 #1 | "module 2" |
| Node 2 / Ra-02 #2 | "module 1" |

---

## 1b. ✅ Test E — header pin → pad, 2026-10-10 — PASS

The single diagnostic that found Ra-02 #1's root cause, run first on this
module because it is the cheapest decisive check: **does each header pin
actually reach its pad?**

Ra-02 #1's answer was no — `1 1 1 1 1` on every signal pin, because the pins
were only pushed in, never soldered.

Ra-02 #2's pins **are** soldered, and this module reads connected.

### Method

Module pulled out of the breadboard, standalone, nothing else attached.
DT-830B in **diode mode**. Red probe (positive) on the round metal U.FL antenna
shell — that is the ground reference. Black probe on the top of each header pin.

### Result

| Pin | Ra-02 #1 post-solder (in breadboard) | **Ra-02 #2 (standalone)** | |
|---|---|---|---|
| 3.3V | `345` | **`447`** | ✅ connected |
| RESET | `527` | **`755`** | ✅ connected |
| NSS | `533` | **`742`** | ✅ connected |
| MOSI | `503` | **`742`** | ✅ connected |
| MISO | `502` | **`742`** | ✅ connected |
| SCK | `505` | **`742`** | ✅ connected |
| GND (×4) | `001` | **`000`** | ✅ correct |

**6/6 signal pins connected.** No `1` anywhere (zero opens). No `000` on any
signal pin (zero solder shorts).

### Reading the numbers

**Why everything sits ~237 mV higher than Ra-02 #1.** Two effects, both
measurement setup, neither a fault:

1. **Ra-02 #1 was measured in the breadboard.** The ESP32's own protection
   diodes sit in parallel with the module's, which pulls the apparent forward
   drop down.
2. **Ra-02 #2 was measured loose in hand.** Hand-held probes on a free-standing
   module have more contact resistance, which pushes the reading up.

Same die, same measurement, different loading. Both land comfortably inside the
documented 300–800 pass band.

**Why all four SPI pins read an identical `742`.** This is the healthy-die
signature. NSS, MOSI, MISO and SCK each have their own ESD diode to ground on
the SX1278 die, and on an intact die they are all the same part. Ra-02 #1 showed
the same shape (`533/503/502/505`, spread of 31 mV) and went on to pass T3, T6
and T7. Four identical numbers is a *good* sign, not a suspicious one.

**Why GND reads `000` and not `001`.** GND is supposed to be a dead short to
the U.FL shell — they are the same copper plane. `000` is the correct answer.
Ra-02 #1 read `001`, which is the same thing one digit later.

### Verdict

✅ **Test E PASS.** Every header pin reaches its pad. No soldering to do.

---

## 1c. What Test E does **not** prove

Being explicit about this, because it is exactly the trap Ra-02 #1 fell into.

Test E measures **pin → module pad**. It says nothing about:

| Gap | How it bites |
|---|---|
| **pad → wire → breadboard clip → ESP32 GPIO** | Ra-02 #1 passed Test E after soldering and *still* failed T3 until six jumper wires were reseated one-conductor-per-hole |
| the module's 16 pins seated level in the breadboard | a tilted module means some pins barely engage |
| the 3.3 V rail actually reaching the module under power | a cold joint can read fine on a meter and drop out under ~120 mA PA current |
| the antenna | `LoRa.begin()` returns `true` regardless; transmit without one burns the PA |

**A meter cannot see any of these.** Only running SPI traffic and RF can.

---

## 1d. The fix package, and where this module stands in it

Ra-02 #1 came good on a **two-part package**, not a single repair:

| | Ra-02 #1 | Ra-02 #2 |
|---|---|---|
| **Part 1** — solder all 16 header pins | ✅ done 2026-10-07 | ✅ **confirmed by Test E, 2026-10-10** |
| **Part 2** — reseat breadboard wires one conductor per hole | ✅ done 2026-10-07 | ✅ **confirmed by T3 PASS, 2026-10-10** |
| → T3 PASS | ✅ `0x12`, 5/5, `PD=float=PU` | ✅ **`0x12`, 5/5, 7/7 rows** |

Part 2 did not need any work on this module — T3 passing on the first attempt,
with zero floating rows, is itself the proof that MOSI/MISO/SCK/NSS all reach
the right GPIO. For comparison, Ra-02 #1 produced floating `1` rows on every
pull-test until the wires were reseated.

Wiring as built on Node 2, kept here for reference:

- MOSI → **D23**, MISO → **D19**, SCK → **D18**, NSS → **D17**, 3V3, GND
- one pin per breadboard clip, none sharing a hole
- module sitting flat, all 16 pins in holes
- antenna clicked onto the U.FL socket

---

## 1e. What remains unproven — **nothing. Bring-up complete.**

| Stage | What it would prove | Status |
|---|---|---|
| T3 `Reg 0x42 = 0x12` | SPI actually reaches the chip at speed | ✅ **PASS 2026-10-10** |
| T6 `LoRa.begin() → true` | the library accepts it | ✅ **PASS 2026-10-10** |
| T7 5/5 beacons | the PA puts energy on the air | ✅ **PASS 2026-10-10** |
| **T8 packet from the other node** | **the whole point — receive** | ✅ **PASS 2026-10-10** |
| `unified_node0.2` boot + `[RX:…BCN:…]` | the production firmware works on this hardware | ✅ **PASS 2026-10-10** |
| `[MSG:…]` from the phone app | full chain: phone → BT → ESP32 → RF → ESP32 | ✅ **PASS 2026-10-10** |

Six for six. See §1f for T3–T8 and **§1g for the production-firmware results**.

---

## 1f. ✅ T3, T6, T7 and T8 — all PASS, 2026-10-10

### T3 — bare SPI: `0x12`, 5/5, 7/7 rows

```
Reg 0x42 = 0x12
#1 = 0x12  #2 = 0x12  #3 = 0x12  #4 = 0x12  #5 = 0x12
Consistent : YES
[1..5] PD=0x12  float=0x12  PU=0x12  <== EXPECTED
[PASS] 0x12 - CHIP IS ALIVE AND RESPONDING.
       RST, NSS, SCK, MISO and power are all good.
```

Zero `MARGINAL` rows, zero floating. **Identical signature to Ra-02 #1's final
T3.** That is the whole point of the Test-E-first ordering — the meter predicted
this, and the meter was right.

### T6 and T7 — via T8's boot

The standalone `t06` and `t07` sketches were skipped to reach the link test
faster, but T8's boot performs the same two gates and prints the same evidence:
`LoRa.begin(433E6) ... ok.` and `[TX] beacon N -> SENT`. No `TIMEOUT` on either
board means both antennas were seated and both PA rails held under load.

### T8 — the link, both directions

**This is the first proven packet-to-packet link in the project.** Everything
before T8 only ever proves one board talking to its own chip.

| | Node 2 (COM3) received | Node 1 (COM4) received |
|---|---|---|
| Heard | `RA02-UNIT1` | `RA02-UNIT2` |
| RSSI | `-66 / -66 / -67 dBm` | `-66 / -66 / -66 dBm` |
| SNR | `9.5 / 9.5 / 9.8 dB` | `9.8 / 9.5 / 9.5 dB` |
| Packets | **3/3** | **3/3** |
| Drops | **0** | **0** |

Full transcript is in [`TEST_LIST.md`](TEST_LIST.md). Two things worth calling
out:

- **`-66 dBm` at `+9.5 dB` SNR is a strong link**, not a marginal one. Both
  antennas are doing their job.
- **6/6 packets arrived.** Not one dropped in either direction. There is margin
  here, not luck.

### A false alarm worth remembering

The first version of `t08` printed `Reg 0x42 = 0xFF` on a perfectly healthy
chip. With the hardware CS pin configured by `SPI.begin(..., PIN_NSS)`,
`SPI.transfer()` toggles NSS itself and races the manual `digitalWrite` used by
the diagnostic probe. The fix was to run the probe under software CS
(`SPI.begin(..., -1)`), after which it reads `0x12` cleanly, then hand the bus
back to hardware CS for `LoRa.begin()`.

`LoRa.begin()` was **never** affected — it succeeded and beacons transmitted
throughout. This was a false alarm in the diagnostic only. The rule: **if a
probe reports `0xFF` but `LoRa.begin()` reports `ok`, suspect the probe, not
the chip.**

---

## 1g. ✅ Production firmware end-to-end — U1 and U2 PASS, 2026-10-10

This is the last section, and the one that matters most: the *actual* ResQPlug
firmware, not a test sketch, carrying a *real* message from a *real* phone.

### U1 — `unified_node0.2` boots and receives

```
[LORA RADIO  ]: ONLINE @ 433.00 MHz (+17 dBm PA)
[ BLUETOOTH   ]: ResQPlug-POD-40C86C (SPP READY)
[RX:RSSI:-67|SNR:9.8|DATA:[BCN:POD-C8E720|ResQPlug-POD-C8E720|CITIZEN|1]]
[BEACON_TX]: [BCN:POD-40C86C|ResQPlug-POD-40C86C|CITIZEN|1]
```

Node 1's *production* beacons — not a test sketch's — arriving at Node 2 at
`-67 dBm` / `+9.8 dB` SNR. Both boards carry all four `unified_node0.2` fixes:
real 8-slot TX queue, external-LED `pinMode`, non-blocking mesh relay, and
`RELAY_SKIP` logging.

### U2 — a message typed into the app

```
[RX:RSSI:-66|SNR:9.8|DATA:[MSG:msg_1791614503565_30F0|RQP-NODE-2330F0|bluetoothtest|STATUS|1|hello]]
[RELAY_QUEUED]: [MSG:…|STATUS|2|hello]
[RELAY_TX]:     [MSG:…|STATUS|2|hello]

[RX:RSSI:-69|SNR:9.8|DATA:[MSG:msg_1791614554871_30F0|RQP-NODE-2330F0|bluetoothtest|STATUS|1|hello node 2 I am node 1]]
[RELAY_QUEUED]: [MSG:…|STATUS|2|hello node 2 I am node 1]
[RELAY_TX]:     [MSG:…|STATUS|2|hello node 2 I am node 1]
```

```
phone (bluetoothtest, RQP-NODE-2330F0)
  ──Bluetooth──▶ Node 1 (POD-C8E720)
  ──433 MHz RF──▶ Node 2 (POD-40C86C)     RSSI -66 / -69 dBm    SNR +9.8 dB
```

**2/2 messages arrived.** Three separate things confirmed in one capture:

| # | Confirmed | Evidence |
|---|---|---|
| 1 | the app's send path works on real hardware | `[MSG:…]` left the phone, crossed BT, went out a radio, landed on a second board |
| 2 | the firmware's RX path formats correctly | `[RX:RSSI:…\|SNR:…\|DATA:…]` matches the gate at `SimulationEngine.kt:201` |
| 3 | **the mesh relay fix works live** | hop `1` → `2`, `[RELAY_QUEUED]` then `[RELAY_TX]`, no `delay()` stall |

Point 3 is worth dwelling on. The relay path was rewritten specifically because
it used to block the main loop with `delay(random(100,300))`. Here it queued,
waited its 100–300 ms backoff, and transmitted — all without stalling RX. That
is the fourth fix firing on real packets, not a test harness.

### Why no phone was needed on Node 2

The natural question when reading this: the app only ever paired to **Node 1**,
so how did Node 2's output get captured?

`resqplug_unified_node.ino:204-210` mirrors every received packet to **both**
interfaces:

```cpp
Serial.println(rxPayload);              // USB serial -> PC, always
if (SerialBT.hasClient()) {
    SerialBT.println(rxPayload);        // Bluetooth  -> phone, only if paired
}
```

No phone was paired to Node 2, so only the USB line fired — and that was read
by a capture script on the PC. **The PC serial monitor and the app are two
different windows onto the same output.** That is also why this is *better*
evidence than a phone screenshot: it is a timestamped, copy-pasteable record.

### A second look at the probe race

U1's boot printed `Probe 1 (Standard CS) Reg 0x42 = 0x00` (and `0xFF` on a
later boot), then reported `LoRa.begin()` success — and packets demonstrably
arrive. Same root cause as the `t08` false alarm in §1f: the hardware-CS pin
configured by `SPI.begin(..., PIN_NSS)` races the probe's manual `digitalWrite`.
**`LoRa.begin()` is the real gate, and it passed.**

### The gap that remains

USB-OTG. The APK's `Transport.USB_OTG` branch
(`ResQPlugHardwareBridge.kt:148-150`) returns `true` without writing anything,
and `attachUsbSession()` never starts a reader loop — `startReaderLoop()` is
only ever called from the Bluetooth path. The firmware *does* listen on USB
serial (`:168`), so the chip side is ready; the app side is not.

**Bluetooth is the only real transport.** Do not trust OTG.

---

## Changelog

| Date | Entry |
|---|---|
| 2026-10-10 | **Kit created.** Seven sketches + `ra02_pins.h` copied from `RA02_BRINGUP/`. New **`t08_two_node_link`** added — the first test in either kit that covers receiving. |
| 2026-10-10 | **Test E on Ra-02 #2: PASS.** `447 / 755 / 742 / 742 / 742 / 742`, GND `000`. 6/6 signal pins connected, no opens, no shorts. Four identical SPI readings = healthy-die signature. No soldering required. |
| 2026-10-10 | **Test E on Ra-02 #1 recorded** in kit #1 — `345 / 527 / 533 / 503 / 502 / 505`, GND `001`, taken in-breadboard. Kept here for comparison; the ~237 mV offset between the two sets is measurement setup, not a fault. |
| 2026-10-10 | **T3 on Ra-02 #2: PASS.** `Reg 0x42 = 0x12`, 5/5 identical, 7/7 pull-test rows all `PD = float = PU`. Zero `MARGINAL`, zero floating. Identical signature to Ra-02 #1. **No wiring rework needed** — the meter's prediction from Test E held. |
| 2026-10-10 | **t08 probe fixed.** First run printed `Reg 0x42 = 0xFF` on a healthy chip — hardware CS configured by `SPI.begin(..., PIN_NSS)` races the manual `digitalWrite` in the diagnostic probe. Probe moved to software CS (`SPI.begin(..., -1)`), bus handed back to hardware CS before `LoRa.begin()`. `LoRa.begin()` was never affected. |
| 2026-10-10 | **T6 + T7 on Ra-02 #2: PASS** (via T8's boot rather than the standalone sketches). `LoRa.begin(433E6) ... ok.`, beacons `SENT`, no `TIMEOUT` on either board — both antennas seated, both PA rails holding. |
| 2026-10-10 | **T8: PASS — both directions.** Node 2 received `RA02-UNIT1` at RSSI `-66/-66/-67 dBm`, SNR `9.5/9.5/9.8 dB`. Node 1 received `RA02-UNIT2` at RSSI `-66/-66/-66 dBm`, SNR `9.8/9.5/9.5 dB`. **6/6 packets, 0 drops.** First proven packet crossing between two ESP32s in this project. |
| 2026-10-10 | **Port assignment recorded:** Node 1 = COM4 (MAC `20:e7:c8:a7:a0:c0`), Node 2 = COM3 (MAC `6c:c8:40:05:7b:20`). COM5 is present on the machine but responds to no ESP32 tooling — not a Node. |
| 2026-10-10 | 🔑 **U1 PASS — production firmware on Node 2.** `unified_node0.2` boots, `LORA RADIO: ONLINE @ 433.00 MHz`, BT `ResQPlug-POD-40C86C` (dongle `POD-40C86C`). Node 1's production beacons arriving at `-67 dBm` / `+9.8 dB` SNR. All four firmware fixes live on both boards. |
| 2026-10-10 | 🔑🔑 **U2 PASS — full phone → radio → Node 2 chain proven.** Two messages typed into the APK (`hello`, `hello node 2 I am node 1`) both arrived at Node 2 at `-66`/`-69 dBm`, SNR `+9.8 dB`. Path: phone → BT → Node 1 → 433 MHz → Node 2. **Mesh relay fired live** (hop 1→2, `[RELAY_QUEUED]` → `[RELAY_TX]`), proving the non-blocking relay fix on real packets. |
| 2026-10-10 | **Bring-up complete — 6/6 gates closed** (Test E, T3, T6, T7, T8, U1, U2). USB-OTG deliberately left out: the APK branch `ResQPlugHardwareBridge.kt:148-150` returns success without sending and has no reader loop. Bluetooth is the only real transport. |
