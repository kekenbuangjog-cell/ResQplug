# ResQPlug — Ra-02 Bring-Up: Findings So Far

> Evidence collected before this folder existed. Updated as tests run.
> **Last updated:** 2026-10-07
> **Status:** 🔴 **ROOT CAUSE FOUND** — unsoldered header pins (§1d)

---

## 1. Meter readings (DT-830B)

| # | Measurement | Reading | Verdict |
|---|---|---|---|
| M0 | 3.3 V at Ra-02 LEFT [3] (black on GND) | **3.20 V** | ✅ Power path good — within normal range for ESP32's 3.3 V rail and the meter's ±0.1 V error |
| M1 | RST at Ra-02 LEFT [4], static, `t01` running | **3.19 V** | ✅ High = out of reset |
| M1b | RST during `t01` Phase A toggle | **swings 0.0 ↔ 3.18 V** | ✅ **D14 → LEFT [4] proven** — the wire is real, not the 3.3 V rail |
| M2 | NSS at Ra-02 RIGHT [2], static, `t01` running | **3.18 V** | ✅ `t01` drives D17 high, module sees it → path delivers |
| M3 | MISO at Ra-02 RIGHT [4], idle | 0.00 V | ⚪ Proves nothing — tri-stated when deselected |
| M3b | MISO during `t01` Phase B toggle | **swings 0.0 ↔ 3.18 V** | ✅ **D19 → RIGHT [4] proven** — the wire is not open |
| M4 | SCK at RIGHT [5], idle | 0.00 V | ✅ normal (held low) |
| M5 | MOSI at RIGHT [3], idle | 0.00 V | ✅ normal (held low) |

**Board state:** `t01_pin_states` flashed and running; Serial verdict reported
`ESP32 PIN STATES OK` (all four readbacks passed). NSS wire visually confirmed
in **D17** (blue 22 AWG solid).


**M0 rules out:** the "insufficient current, needs a resistor" theory. A resistor
*limits* current and would only drop the voltage further. The correct component
for supply stability would be a **capacitor** (100 nF + 100 µF across 3V3/GND at
the module) — and notably the wiring guide's own diagram draws `[C1] [C2]` on
line 34 but never instructs you to install them.

**MISO 0.00 / 0.07 V is not diagnostic.** When NSS is HIGH the SX1278
tri-states (disables) its MISO output, so the line floats and a meter's own
input resistance pulls it to ~0 V. This also explains why the tracer sketch
reported `MISO idle = HIGH` while the meter read 0 V — a floating input reads
differently on an ESP32 GPIO than on a meter. Both readings mean the same
thing: **nobody is driving MISO at that moment.**

**Probe-position note:** your diagram confirms `MISO = RIGHT [4]` and
`RST = LEFT [4]`. A reading taken on the *left* column row 4 is RST, not MISO —
so the "0 on the left side" reading may actually be the RST result, which would
be a significant finding (chip held in reset).

---

## 1b. T3 result — the chip is NOT answering

`t03_spi_bare` was flashed and run repeatedly (verified by capturing Serial
directly over COM3). Two rounds of evidence:

**Round 1 — fast bit-bang (200 Hz):** `0x42 → 0x6A`, then
`0xB5, 0x95, 0xD5, 0xD5, 0x55`. Inconsistent.

**Round 2 — slowed to ~16 Hz so it could not be a timing artefact:** still
inconsistent (`0A A1 94 52 4A 29 85 52 4A 29`).

**Round 3 — the decisive test.** Each read was performed three times with a
different input mode on the ESP32's MISO pin:

| Mode | Internal effect | Chip's MISO driver |
|---|---|---|
| `INPUT_PULLDOWN` | ~50 kΩ to GND | **wins** if chip is driving |
| `INPUT` | high-Z, nothing fighting | — |
| `INPUT_PULLUP` | ~45 kΩ to 3.3 V | **wins** if chip is driving |

The SX1278's MISO driver is milliamps-strong and easily overrides these weak
pulls, so **a chip that is answering returns the same value in all three modes.**
A line nobody drives is at the mercy of the pulls.

**Result — 9 for 9 identical:**

```
[ 1]  PD=0x00   float=0x29   PU=0xFF
[ 2]  PD=0x00   float=0x28   PU=0xFF
[ 3]  PD=0x00   float=0x4A   PU=0xFF
...            (all nine)
```

### Verdict

**MISO is FLOATING. Nothing is driving it.**

This **overturns** the earlier interpretation of the varying values: they were
never a chip sending corrupted data. They were a floating line picking up
noise — which is exactly why the values shifted between reads.

**So the chip is not answering at all.** That is now a measured fact, not an
inference.

### What this does and does not prove

| | |
|---|---|
| ✅ Proven | Wire continuity ESP32 pin → header hole (power, RST, NSS, MISO all toggled/measured) |
| ✅ Proven | Pinout table itself is correct — matches the **official Ai-Thinker datasheet** |
| ✅ Proven | Not a timing/speed/sketch artefact (fails identically at 200 Hz and 16 Hz) |
| ❌ **Not** proven | That the header **hole** is the intended **chip function** |
| ❌ **Not** proven | That the module is actually powered (3.20 V proves the *wire* carries it) |
| ❌ **Not** proven | That module GND is connected — *never measured* |

**The hole-vs-function gap is now the whole question.** Everything above only
proves the wire reaches a hole; it does not prove the hole is the pin we think.

---

## 1c. Pinout — verified against the official datasheet

Ai-Thinker Ra-02, 16 pads, numbered from the **antenna (U.FL) end**:

```
LEFT  1 GND   2 GND   3 3.3V   4 RESET   5 DIO0   6 DIO1   7 DIO2   8 DIO3
RIGHT 16 GND  15 NSS  14 MOSI  13 MISO   12 SCK   11 DIO5  10 DIO4   9 GND
```

Read **down** the left column and **up** the right column — pin 9 sits at the
bottom-right, directly opposite pin 8.

Numbering the right column top→bottom therefore gives
**GND, NSS, MOSI, MISO, SCK, DIO5, DIO4, GND** — which is exactly the table
this project has been using. ✅

### The trap

That table is only valid **counted from the antenna end.** Count from the far
end instead and every wire lands on the wrong pin:

| Wire | If counted from the far end it lands on | Effect |
|---|---|---|
| 3.3 V → "LEFT[3]" | DIO1 | **module never powered** |
| RST → "LEFT[4]" | DIO0 | reset not released on the real RST |
| NSS → "RIGHT[2]" | DIO4 | **chip never selected** |
| MISO → "RIGHT[4]" | SCK (an *input*) | **MISO floats** ← exactly what T3 measured |
| SCK → "RIGHT[5]" | MISO (an *output*) | clock lands on a data output |
| MOSI → "RIGHT[3]" | DIO5 | command never reaches the chip |

**That single reversal reproduces every symptom we have observed** — including
the floating MISO — while leaving every *wire-to-hole* continuity test green.

**Status 2026-10-07:** the reversal trap was **tested and eliminated**. The
orientation is correct (U.FL at the pin-1 end) and the user's wire diagram was
**copied from the labels printed on the module itself**, not drawn from memory.

---

## 1d. 🔴 ROOT CAUSE — the header pins were never soldered

Discovered 2026-10-07. **This is the answer to the original question.**

The Ra-02 ships with **loose header pins**. They were **pushed into the
module's holes by friction only — no solder, at any point in this project.**

Every continuity test performed so far proved *ESP32 pin → header pin* and
stopped. The joint **header pin → module pad** was never measured, because it
had no test point. Two tests closed it.

### Test D — bare pad → silicon (pins removed, diode mode)

Red on the U.FL shell (module ground), black on each exposed pad:

| Pad | Reading | |
|---|---|---|
| 3.3V | `421` | ✅ reaches the die |
| RESET | `709` | ✅ reaches the die |
| NSS | `697` | ✅ reaches the die |
| MISO | `697` | ✅ reaches the die |
| SCK | `697` | ✅ reaches the die |
| MOSI | `697` | ✅ reaches the die |

The four SPI pads returning an **identical `697`** is the signature of four
healthy ESD diodes on one intact die. **The module is not damaged and not dead.**

### Test E — header pin → pad (pins inserted, same method)

Same probes, but black on the **top of the header pin** instead of the pad:

| Pin | Bare pad | With pin inserted | |
|---|---|---|---|
| 3.3V | `421` | **`1`** | 🔴 open |
| RESET | `709` | **`1`** | 🔴 open |
| NSS | `697` | **`1`** | 🔴 open |
| MOSI | `697` | **`1`** | 🔴 open |
| SCK | `697` | **`1`** | 🔴 open |
| MISO | `697` | **`500` fluctuating** | 🔴 **marginal** |

**Every pad is healthy. No pin reaches its pad.**

### The complete picture

```
ESP32 ──wire── breadboard ──header pin   ✅ proven (T1, Test 1, Test 3)
header pin ═══╳═════════ module pad      🔴 THE GAP — friction only, no solder
module pad ────── silicon                ✅ proven (Test D, diode drops)
```

### Why every earlier reading looked the way it did

| Observation | Explanation |
|---|---|
| `PD=0x00 / PU=0xFF` (floating) | MISO's pin never reaches the chip's MISO output |
| Junk values `0x29 0x4A 0x94 0x48` | MISO's pin reads `500 fluctuating` — mostly open with brief touches of contact |
| Both modules fail **identically** | same assembly method on both — neither was soldered |
| Test A passed `000`/`001` on GND | corner GND pins happened to seat, plus probe pressure pushed them home |
| Log Step 9 *"not a dead chip"* | conclusion was right, reasoning was wrong — it wasn't the breadboard *wires*, it was the module's own pins |
| Log Step 10 *"intermittent contact across speeds"* | the intermittency was the unsoldered joint, not the jumper wires |

### Why pushing the pins in does not fix it

Repeated attempts to press the pins home produced **no change** — friction in a
castellated hole gives no reliable metal-to-metal contact, and a multimeter
probe pressing down can manufacture a reading that does not exist in service.

### Fix

**Solder all 16 header pins.** Then:

1. Re-run **Test E** — every pin must match its Test D baseline (`000` for GND,
   `421`, `709`, `697`×4). Any `1` = a joint you missed.
2. Re-run **T3** — target `Reg 0x42 = 0x12` and `PD = float = PU`.
3. Install the LoRa library → **T6** → **T7**.

---

## 1e. ✅ POST-SOLDER RESULT — the chip answers

All 16 pins on module 2 were soldered (2026-10-07). Post-solder **Test E**
and two full **T3** captures were run.

### Test E after soldering — 6/6 signal pins now connect

| Pin | Test D (bare pad) | Test E before solder | Test E **after solder** |
|---|---|---|---|
| 3.3V | `421` | `1` ❌ | **`345`** ✅ |
| RESET | `709` | `1` ❌ | **`527`** ✅ |
| NSS | `697` | `1` ❌ | **`533`** ✅ |
| MOSI | `697` | `1` ❌ | **`503`** ✅ |
| MISO | `697` | `1` ❌ | **`502`** ✅ |
| SCK | `697` | `1` ❌ | **`505`** ✅ |
| GND | `000` | `001` | **`001`** ✅ |

No `1` opens, no `000` shorts. (Post-solder numbers run lower than the bare-pad
baseline because the probe now lands on the **top of a soldered pin**, not on a
flat pad — different contact area, different pressure. The meaningful test is
*connected vs `1`*, and every pin now reads connected.)

**Bridge check (diode mode), red on U.FL shell:**

| Pair | Reading | Meaning |
|---|---|---|
| GND ↔ 3.3V | `1` | ✅ no power short |
| NSS ↔ MOSI | `1` | ✅ no bridge |
| MOSI ↔ MISO | `1` | ✅ no bridge |
| MISO ↔ SCK | `1` | ✅ no bridge |
| 3.3V ↔ RESET | `1263` | ✅ **two diodes through the die, not a bridge** |

A solder blob is metal: it reads **`000`, both polarities, always.** `1263` mV
is ~2 × 0.63 V — the 3.3 V rail going down through the substrate junction and
back up through the RESET pin's own ESD clamp. The DT-830B tests at ~2.8 V /
1 mA and displays millivolts, so a two-diode path is well inside its range.
That number is a *healthy* chip, not a fault.

### T3 after soldering — two captures

**The floating signature is gone.** Before soldering: `PD=0x00 / PU=0xFF` on
**every** read. After soldering: **not one** read showed that pair.

| Run | `Reg 0x42` (main read) | 5× repeat | Pull-test rows |
|---|---|---|---|
| 1 | `0x0C` | `0x3F`, **`0x12`**, `0x47`, `0x00`, `0x55` | `PU=0x12`, `PD=0x55`, `PD=0x0F` — never `0xFF` |
| 2 | **`0x12`** ✅ | `0x0C`, `0x29`, `0x0C`, `0x29`, `0x00` | `PD=0x0F`, `PD=0x28`, `PU=0x55` — never `0xFF` |

Run 2's headline read is `Reg 0x42 = 0x12`, and the sketch printed
**`[PASS] 0x12 - CHIP IS ALIVE AND RESPONDING`**.

### Verdict (interim — before the reseat)

**The module is ALIVE.** `0x12` is the SX1278 silicon-version register and it
cannot appear by accident on a floating line. Everything needed for
`LoRa.begin()` — power, RST, NSS, SCK, MISO — now reaches the die.

**Remaining defect at this point: intermittent corruption.** The 5× consistency
check failed and the pull-test rows read `MARGINAL` rather than
all-three-agree. That was **not** a dead chip (a dead chip returns the same
wrong value every time) and **not** the old fault (which was total, not
partial).

Timing and protocol were excluded: `BIT_DELAY_US` is 20 000 µs (60 ms per bit)
and the read routine is correct — the address-phase bits are shifted out of the
8-bit accumulator, so only the data byte remains. At that speed every edge is
settled long before it is sampled.

**It was remaining marginal contact** — one or more of MISO / SCK / NSS / MOSI
losing touch mid-transaction, at the **breadboard clip**, not the new solder
joints.

### ✅ T3 FINAL — PASS after reseating the breadboard wires

Six wires were reseated **one conductor per hole** (MOSI → D23, MISO → D19,
SCK → D18, NSS → D17, 3V3, GND). Red and black were left alone — already firm.

```
Reg 0x42 = 0x12
5x: 0x12 0x12 0x12 0x12 0x12     Consistent : YES
[1..7]  PD=0x12  float=0x12  PU=0x12   [PASS]
[PASS] 0x12 - CHIP IS ALIVE AND RESPONDING
```

| Check | Required | Result |
|---|---|---|
| `Reg 0x42` | `0x12` | ✅ `0x12` |
| 5× repeat identical | YES | ✅ **5/5** |
| `PD = float = PU` | all agree | ✅ **7/7 rows, all `0x12`** |
| Floating pair / MARGINAL rows | absent | ✅ **zero of either** |

The hypothesis is confirmed: **the last fault was breadboard seating, not
solder, not timing, not silicon.**

### ✅ T6 — `LoRa.begin(433E6)` → `true`

LoRa 0.8.0 installed via `arduino-cli lib install "LoRa"`. T6 compiled clean
(only library-internal `B111`/`B1000` deprecation warnings) and was flashed to
COM3.

```
NSS=17  RST=14  DIO0=26  SCK=18  MISO=19  MOSI=23
Frequency : 433.0 MHz
[2] LoRa.begin(433E6) ...  Result : true
[3] TxPower=+18dBm  SF=7  BW=125kHz  CR=4/5  SyncWord=0x12
[4] Idle receive for 5 seconds ... Packets received in 5s : 0
VERDICT T6: PASS
SUCCESS - Ra-02 online at 433.00 MHz.
```

### ✅ T7 — 5/5 beacons transmitted

```
Freq=433.00MHz  TxPower=+18dBm  SF=7  BW=125kHz  CR=4/5  SyncWord=0x12  CRC=on
Beacon 0..4 -> SENT
VERDICT T7: PASS - all beacons transmitted.
```

### Bring-up verdict

| Stage | Result |
|---|---|
| power | ✅ 3.20 V at the module |
| ESP32 → header pin | ✅ T1 8/8 |
| header pin → module pad | ✅ Test E 6/6 after soldering |
| module pad → silicon | ✅ Test D `421`/`709`/`697`×4 |
| register reads | ✅ T3 `0x12`, 5/5, `PD=float=PU` |
| library | ✅ T6 `LoRa.begin(433E6)` = `true` |
| RF output | ✅ T7 5/5 sent |

**The module was never dead. It was never soldered.** Two stacked faults, both
in the connection environment, now closed.

---

## 1f. ⚡ 2026-10-10 — production firmware on hardware, and a second node

Two things happened on this date. The first is that the production firmware
finally ran on this board; the second is that a second Ra-02 module joined the
project and got its own bring-up kit.

### `unified_node0.2` — first run on real hardware

The bring-up suite (`t01`–`t07`) had proven the radio works. The production
firmware — the thing the app actually talks to — had **never been executed**
until now. It was rebuilt with four fixes and flashed to Node 1.

```
=========================================================
   ⚡ ResQPlug Unified Dual-Transport + LIVE LORA v3.3
=========================================================
[LORA]: Probing SX1278 on SPI Bus (SCK:18, MISO:19, MOSI:23, SS:17, RST:14)...
[LORA]: Probe 1 (Standard CS) Reg 0x42 = 0x12
[LORA]: 🎉 SX1278 Radio Initialized Successfully at 433.00 MHz (+17 dBm)!
   [ DONGLE ID   ]: POD-C8E720
   [ SILICON MAC ]: C0:A0:A7:C8:E7:20
   [ CHIP MODEL  ]: ESP32-D0WD-V3
   [ CPU FREQ    ]: 240 MHz
   [ BLUETOOTH   ]: ResQPlug-POD-C8E720 (SPP READY)
   [ USB-OTG     ]: 115200 Baud (CDC/UART READY)
   [ LORA RADIO  ]: ONLINE @ 433.00 MHz (+17 dBm PA)
=========================================================
```

**Probe 1 succeeds on the first attempt.** That matters beyond the boot
banner: `t06_lora_begin` used to be the only place `LoRa.begin()` was proven,
and it never printed the raw register value. Seeing `Reg 0x42 = 0x12` from the
production firmware confirms **NSS on GPIO 17 works on the library path too**,
not just in the bit-bang test. The Phase 3 pin reconciliation is now confirmed
end to end on hardware.

### The four fixes that shipped with it

| # | Before | After |
|---|---|---|
| 1 | `[ACK:TX_QUEUED_SIMULATED]` replied that a message was queued while the radio was offline — **and then discarded it** | Real 8-slot ring buffer. `[ACK:TX_QUEUED]` while held, `[ACK:TX_FLUSHED]` after auto-recovery, `[ACK:TX_QUEUE_FULL]` on overflow. Oldest drops first. |
| 2 | `EXTERNAL_LED_PIN` (GPIO 4) was written in three places but never given `pinMode(OUTPUT)` — **the external LED was dead** | Configured in `setup()` |
| 3 | `delay(random(100,300))` in the mesh relay made the node **deaf** to incoming packets for up to 300 ms | Parked in `pendingRelayPacket`, transmitted from `loop()` on a `millis()` timer. Node keeps listening while it waits. |
| 4 | Malformed packets were dropped by `handleMeshRelay` with no output at all | `[RELAY_SKIP]: <packet> (no packet id)` / `(no parseable hop field)` / `(hop 3 >= max 3)` |

Size 1,081,991 B (82%). All four other firmware targets still compile unchanged.

### Ra-02 #2 joins the project

The second module — wired to a second ESP32 as **Node 2** — now has its own
kit at [`../RA02_BRINGUP_NODE2/`](../RA02_BRINGUP_NODE2/README.md), with the
same format and the same pass criteria as this one.

Its pins **are** soldered, and its Test E already passes:

| Pin | Ra-02 #1 (this module, in breadboard) | Ra-02 #2 (standalone) |
|---|---|---|
| 3.3V | `345` | `447` |
| RESET | `527` | `755` |
| NSS / MOSI / MISO / SCK | `533` / `503` / `502` / `505` | **`742` × 4** |
| GND | `001` | `000` |

The ~237 mV offset is measurement setup, not a fault: #1 was measured in the
breadboard where the ESP32's protection diodes sit in parallel and pull the
reading down; #2 was measured loose in hand where probe contact resistance
pushes it up. Both are inside the 300–800 pass band, and both show the
four-identical-SPI-readings signature of an intact die.

Full reasoning: [`../RA02_BRINGUP_NODE2/FINDINGS.md`](../RA02_BRINGUP_NODE2/FINDINGS.md) §1b.

### What neither module had ever been tested for — closed 2026-10-10

**Receiving.** T1–T7 only ever prove one board talking to its own chip. T7 puts
energy on the air and stops there.

That gap was closed by a new sketch, **`t08_two_node_link`**, in kit #2. It runs
identically on both boards — change only `NODE_ID` — beacons every 10 s, prints
every packet it hears with RSSI/SNR, and prints `VERDICT T8: PASS` the moment a
packet from the other node arrives.

**T8 passed in both directions on 2026-10-10:**

| | Node 2 received `RA02-UNIT1` | Node 1 received `RA02-UNIT2` |
|---|---|---|
| RSSI | `-66 / -66 / -67 dBm` | `-66 / -66 / -66 dBm` |
| SNR | `9.5 / 9.5 / 9.8 dB` | `9.8 / 9.5 / 9.5 dB` |
| Packets | **3/3, 0 drops** | **3/3, 0 drops** |

Full transcript in [`../RA02_BRINGUP_NODE2/TEST_LIST.md`](../RA02_BRINGUP_NODE2/TEST_LIST.md)
and analysis in
[`../RA02_BRINGUP_NODE2/FINDINGS.md`](../RA02_BRINGUP_NODE2/FINDINGS.md) §1f.

---

## 2. Contradictions found in the repository

### NSS pin defined six different ways

| File | Line | NSS | Verdict |
|---|---|---|---|
| `LORA_WIRING_AND_DIY_HOOKUP_GUIDE.md` — ASCII diagram | 26 | GPIO 5 | ❌ |
| `LORA_WIRING_AND_DIY_HOOKUP_GUIDE.md` — wiring table | 45 | **GPIO 17** | ✅ |
| `LORA_WIRING_AND_DIY_HOOKUP_GUIDE.md` — sample code | 115 | 5 | ❌ |
| `HARDWARE_OTG_TROUBLESHOOTING.md` | 151 | GPIO 5 | ❌ |
| `LORA_RA02_ESP32_TROUBLESHOOTING_LOG.md` | 221 | **17** | ✅ |

The wiring guide **contradicts itself internally** — diagram says 5, table says
17, code says 5. If you wired from the diagram or copied the code, you're on
the strapping pin the log blames for `0xFF`.

### DIO0 pin

| File | Line | DIO0 | Verdict |
|---|---|---|---|
| `HARDWARE_OTG_TROUBLESHOOTING.md` | 155 | **GPIO 2** | ❌ (strapping pin + onboard LED) |
| All other references | — | GPIO 26 | ✅ |

### Firmware — production code disagrees with the log

| Sketch | Line | NSS |
|---|---|---|
| `firmware/resqplug_unified_node0.2/` — **current** | 40 | **5** |
| `firmware/resqplug_unified_node0.1/` | 40 | **5** |
| `firmware/resqplug_lora_test/` | 24 | **5** |
| `firmware/resqplug_lora_diagnostic/` | 11 | **5** |
| `firmware/resqplug_lora_beacon_test/` — the one under test | 18 | **17** |

**Your production firmware is wired for GPIO 5 while your active test sketch is
on GPIO 17.** The two disagree, so at least one is wrong regardless of what the
module does.

`resqplug_unified_node0.2` also carries a fallback at lines 339–340:
*"Probe 2 (Software CS on GPIO 5)"* — another GPIO 5 reference.

### Strapping-pin documentation error

| File | Line | Says | Problem |
|---|---|---|---|
| `EXTERNAL_LED_FIREBASE_MVP_ACTIVITY_GUIDE.md` | 40 | strapping pins are `0, 2, 12, 15` | **omits 5** — ESP32 strapping pins are 0, 2, **5**, 12, 15 |
| `EXTERNAL_LED_FIREBASE_MVP_ACTIVITY_GUIDE.md` | 41 | LoRa pins include `GPIO 5` | directly contradicts the log's rule against it |

---

## 3. Troubleshooting log gap

From `LORA_RA02_ESP32_TROUBLESHOOTING_LOG.md`:

- **Step 3** — LED tester found RST not lighting → *fixed* by wiring to D14
- **Step 4** — *"RST Now Controlled via D14… Result: Still 0xFF. No improvement."*

**RST was never re-measured with a multimeter after the rewiring.** Step 4
records the SPI result, not the RST voltage. The claim that RST was fixed rests
on the wiring change alone.

---

## 4. Evidence table — is the chip alive?

### Established before this folder existed

| Evidence | Points to |
|---|---|
| Two independent modules fail **identically** | Unusual for dead silicon, which fails randomly |
| All 24 pin permutations return `0xFF` (log Step 2) | Ambiguous — consistent with no-chip-select |
| Bit-bang at 10 kHz also `0xFF` (log Step 6) | Rules out libraries, not wiring |
| GPIO MISO self-test passed (log Step 7) | ESP32 side fine |
| Power wire carries 3.20 V | Rules out a *wire* supply failure |

### Added 2026-10-06

| Evidence | Points to |
|---|---|
| T1 Phase A: RST swings 0.0 ↔ 3.18 V | D14 → RST path is real |
| T1 Phase B: MISO swings 0.0 ↔ 3.18 V | MISO wire is real and continuous |
| T3 fails identically at 200 Hz **and** 16 Hz | **Not** a timing or speed artefact |
| **T3 pull-up/pull-down: `PD=0x00 … PU=0xFF`, 9/9** | **MISO is floating — chip is not answering** |

### Added 2026-10-07

| Evidence | Points to |
|---|---|
| Test 1 GND continuity = `010` | ESP32 GND reaches LEFT [1] |
| Test 3: SCK `001`, MOSI `001` | every SPI wire reaches its header pin |
| NSS dips 0.0 ↔ 3.2 V during live reads | chip-select is delivered dynamically |
| Test A: GND pins → U.FL shell = `000` / `001` | GND pins **are** soldered/joined to the PCB |
| **Test D: all six pads → die** (`421`/`709`/`697`×4) | **silicon intact — dead silicon eliminated** |
| **Test E: all six pins → pad read `1`** | **pins are not joined to the pads — THE ROOT CAUSE** |
| T3 on module 2 identical to module 1 | two chips, same assembly, same fault |

**Weight of evidence: ROOT CAUSE ESTABLISHED.** The chip was never dead and the
wiring was never wrong. The signals stopped at an **unsoldered header pin** —
a link no test in this project had measured until Test E.

> The pre-existing log's conclusion *"connection environment, not dead silicon"*
> was **directionally right but wrong about which connection**: it blamed the
> breadboard jumper wires (Step 10/11). The jumper wires are fine. The fault is
> inside the module assembly itself.

---

## 5. Open questions

### Resolved

| # | Question | Resolution |
|---|---|---|
| 1 | Is RST actually releasing? | ✅ **Phase A swings 0.0 ↔ 3.18 V** — D14 → LEFT [4] proven, not the 3.3 V rail |
| 2 | Which pin is the NSS wire physically on? | ✅ **D17**, confirmed visually (blue 22 AWG solid) |
| 3 | **Is the MISO wire continuous?** | ✅ **Phase B swings 0.0 ↔ 3.18 V** — the wire is not open |
| 5 | Is the read consistent or does it vary? | ✅ **Varies** — but now explained: the line floats, so the values were noise, not data |
| **A** | **Was the header counted from the antenna end?** | ✅ **Orientation correct.** U.FL sits at the pin-1 end. Also confirmed the wire diagram was **copied from the module's own printed labels**, not drawn. |
| **B** | **Is module GND actually connected?** | ✅ **Connected.** Test 1 = `010` (ESP32 GND ↔ LEFT [1]) and Test A = `000`/`001` (GND pins reach the PCB). |
| **C** | **Does NSS dip to 0 V during a read?** | ✅ **Dips to ~0 V and back to ~3.2 V repeatedly** on a live `t03` run. |
| **D** | **Is the silicon intact?** | ✅ **Yes.** Test D diode drops `421` / `709` / `697`×4 — every pad reaches the die. |
| **E** | **Does each header pin reach its pad?** | 🔴 **No.** Test E returns `1` on 3.3V, RESET, NSS, MOSI, SCK and `500` flickering on MISO. **This is the root cause.** |

### Still open

| # | Question | Status | Resolved by |
|---|---|---|---|
| **F** | **Are the pins soldered yet?** | 🔴 **No — this is the only blocker** | Solder all 16 pads → re-run Test E |
| 6 | Does hardware SPI agree with bit-bang? | ⬜ blocked | **T4** (after T3 passes) |
| 7 | Which NSS pin does the chip answer on? | 🟡 **D17** proven statically + dynamically — T5 now likely redundant | **T5** |
| 8 | Will the library accept the module? | ⬜ blocked — LoRa lib not installed | **T6** |
| 9 | Does it transmit? | ⬜ blocked | **T7** |

> **The question has changed again — and for the last time.**
> It is no longer *"is the chip alive?"* (Test D says yes) nor *"which wire is
> wrong?"* (none are). The single open question is **F: will the pins conduct
> once they are soldered?** Everything downstream — T4, T6, T7 — waits on that.

> **Why "push harder" cannot answer it:** the pins sit in castellated holes
> held only by friction. A meter probe pressing on the pin can create contact
> for the duration of a reading (this is almost certainly why Test A passed on
> the two GND pins) while the joint remains open in service.

---

## 6. Changelog

| Date | Change |
|---|---|
| 2026-10-02 | Troubleshooting log created (11 steps, status 🟡 unresolved) |
| 2026-10-06 | `RA02_BRINGUP/` created: 7 test sketches + plan + test list. Meter: M0 = 3.20 V ✅. Doc/firmware contradictions catalogued. |
| 2026-10-06 | `t01` flashed and verified — Serial reported `ESP32 PIN STATES OK`. Static readings M1 = 3.19 V, M2 = 3.18 V, M4/M5 = 0.00 V. NSS wire visually confirmed on **D17**. |
| 2026-10-06 | **`t01_pin_states.ino` revised** — RST and MISO toggles moved from one-shot `setup()` into a repeating `loop()` (Phase A / Phase B), and the incorrect comment at old line 130 (`"Ra-02 side should stay ~0V"`) corrected. Rationale: a one-shot 0.8 s toggle was unmeasurable, and the old comment described the opposite of a useful continuity test. |
| 2026-10-06 | `TEST_LIST.md` T1 rewritten: static rows split from toggle rows, MISO continuity added as row 8, per-row interpretation tables added. |
| 2026-10-06 | **Compile verified** with the bundled `arduino-cli` 1.5.1 against ESP32 core 3.3.11, `--warnings all`: **T1–T5 COMPILE OK, zero warnings.** T6/T7 fail on `LoRa.h: No such file or directory` — library not installed (expected). |
| 2026-10-06 | **T1 PASSED 8/8.** Phase A (RST) and Phase B (MISO) both swing 0.0 ↔ 3.18 V. D14→RST and D19→MISO proven continuous. |
| 2026-10-06 | **T3 flashed** (board on COM3, CH340). Reads inconsistent: `0x6A / 0xB5, 0x95, 0xD5, 0xD5, 0x55`. |
| 2026-10-06 | `t03_spi_bare.ino` revised twice: `BIT_DELAY_US` 2500 → **20000** (16 Hz, meter-visible) and `loop()` rewritten to stream reads. **Still inconsistent at 16 Hz → not a timing artefact.** |
| 2026-10-06 | **Added the pull-up/pull-down discriminator** to `t03`: each register read is taken in `INPUT_PULLDOWN`, `INPUT` and `INPUT_PULLUP`. A driving chip wins all three; a floating line is at the mercy of the pulls. |
| 2026-10-06 | 🔴 **KEY RESULT: `PD=0x00 / PU=0xFF` on 9 of 9 reads → MISO is floating, the chip is NOT answering.** The varying values were floating-line noise, not corrupted chip data. |
| 2026-10-06 | **Pinout verified against the official Ai-Thinker Ra-02 datasheet** — table confirmed correct *when counted from the antenna end*. Recorded the far-end-reversal trap (§1c). |
| 2026-10-07 | **Test 1 (GND continuity) = `010`** → ESP32 GND reaches LEFT [1]. **Test 2 (orientation) passed visually** — U.FL at the pin-1 end. |
| 2026-10-07 | **Test 3 — SCK `001`, MOSI `001`.** With T1's RST and MISO swings plus the 3.20 V rail, **all eight ESP32 → header-pin paths are now proven.** |
| 2026-10-07 | **NSS dip observed live:** falls to ~0 V and returns to ~3.2 V on every read. Chip-select is delivered dynamically, not just statically. |
| 2026-10-07 | **T3 re-run on a second Ra-02 module — identical failure**, `PD=0x00 / PU=0xFF` on 5/5. Two independent chips, same symptom. |
| 2026-10-07 | **Pinout re-verified against the official datasheet** (3 independent sources) **and against the module's own printed labels** — the user's wire diagram was copied from the silkscreen, not drawn. Wrong-pinout hypothesis eliminated. |
| 2026-10-07 | **Test A:** both GND header pins → U.FL shell = `000` / `001` → header pins *can* reach the PCB. |
| 2026-10-07 | **Test D (bare pads, diode mode):** `421` `709` `697` `697` `697` `697` → **every pad reaches the silicon. The module is not dead.** |
| 2026-10-07 | 🔴 **ROOT CAUSE — Test E:** with pins inserted, the same six read **`1` `1` `1` `1` `1`** plus **MISO `500` fluctuating.** **The header pins were never soldered** — friction contact only. See **§1d**. Eliminated: orientation, module GND, dead silicon, wire continuity, pinout. |
| 2026-10-07 | `TEST_LIST.md` Phase 2 table and FINAL VERDICT rewritten; T3 "Next actions" A/B/C all marked complete. Status → 🔴 ROOT CAUSE FOUND. |
| 2026-10-07 | **All 16 pins on module 2 soldered.** Post-solder Test E: `345 / 527 / 533 / 503 / 502 / 505`, GND `001` → **6/6 signal pins connected** (was 0/6), no shorts, no opens. |
| 2026-10-07 | **Bridge check:** GND↔3.3V `1`, NSS↔MOSI `1`, MOSI↔MISO `1`, MISO↔SCK `1` → no shorts. 3.3V↔RESET `1263` = two ESD diodes in series through the die (DT-830B tests at ~2.8 V, displays mV), **not** a solder bridge. |
| 2026-10-07 | 🔑 **T3 re-run after soldering — `PD=0x00 / PU=0xFF` never appears again.** Run 1: `PU=0x12`, `PD=0x55`, `PD=0x0F`. Run 2: `Reg 0x42 = 0x12` on the headline read, sketch printed **`[PASS] CHIP IS ALIVE`**. |
| 2026-10-07 | **Module declared ALIVE.** `0x12` appeared twice across two independent captures (headline read in run 2, `PU=0x12` in run 1). Remaining defect is *intermittent corruption*, not silence — see §1e. |
| 2026-10-07 | **Six breadboard wires reseated one-conductor-per-hole** (MOSI D23 · MISO D19 · SCK D18 · NSS D17 · 3V3 · GND). Red/black left alone — already firm. User also flagged and replaced a loose SPI wire. |
| 2026-10-07 | 🔑 **T3 FINAL PASS:** `Reg 0x42 = 0x12`, **5/5 identical**, **7/7 pull-rows all `PD = float = PU = 0x12`**, zero `MARGINAL`, zero floating pair. Confirms the last fault was breadboard seating. |
| 2026-10-07 | **LoRa library 0.8.0 installed** — `arduino-cli lib install "LoRa"`. T6 and T7 now compile; only library-internal `B111`/`B1000` deprecation warnings remain. |
| 2026-10-07 | 🔑 **T6 PASS — `LoRa.begin(433E6)` returned `true`.** `SUCCESS - Ra-02 online at 433.00 MHz`. Configured SF7 / 125 kHz / CR 4/5 / +18 dBm / sync word 0x12. |
| 2026-10-07 | 🔑 **T7 PASS — 5/5 beacons transmitted.** `VERDICT T7: PASS - all beacons transmitted`. Bring-up complete. |
| 2026-10-07 | `README.md` banner → ALL TESTS PASS; `PLAN.md` status + critical path marked complete; `TEST_LIST.md` T3 final table and FINAL VERDICT rewritten; `LORA_RA02_ESP32_TROUBLESHOOTING_LOG.md` §10 extended. |
| 2026-10-10 | ⚡ **`unified_node0.2` flashed to Node 1 — first production-firmware run on hardware.** `Probe 1 Reg 0x42 = 0x12`, `🎉 …Initialized`, BT `ResQPlug-POD-C8E720`, `LORA RADIO: ONLINE @ 433.00 MHz`. Probe 1 first try, no software-CS fallback → **NSS GPIO 17 confirmed on the library path**. See §1f. |
| 2026-10-10 | **Four production-firmware fixes shipped with that build:** real 8-slot TX queue replacing the message-discarding `[ACK:TX_QUEUED_SIMULATED]`; `pinMode(4, OUTPUT)` for the external LED (dead until now); non-blocking mesh-relay backoff replacing `delay(random(100,300))`; `[RELAY_SKIP]` logging for silently-dropped packets. |
| 2026-10-10 | **`RA02_BRINGUP_NODE2/` created** for Ra-02 #2 (Node 2) — sibling kit, same format and pass criteria. Seven sketches + `ra02_pins.h` copied; new **`t08_two_node_link`** added, the first test in either kit that covers *receiving*. |
| 2026-10-10 | **Ra-02 #2 Test E: PASS.** `447 / 755 / 742 / 742 / 742 / 742`, GND `000` — 6/6 connected, no opens, no shorts. Four identical SPI readings = healthy-die signature. ~237 mV above this module's numbers is measurement setup (in-breadboard vs hand-held), not a fault. |
| 2026-10-10 | 🔑 **Ra-02 #2 bring-up: T3, T6, T7, T8 all PASS.** T3 `0x12`, 5/5, 7/7 rows — identical to this module's final T3, no rework needed. T6/T7 via t08's boot. **T8 passed both directions:** Node 2 heard `RA02-UNIT1`, Node 1 heard `RA02-UNIT2`, RSSI `-66 dBm` / SNR `+9.5 dB`, **6/6 packets, 0 drops**. **First proven packet crossing between two ESP32s in this project.** |
| 2026-10-10 | **Ports identified:** Node 1 = COM4 (MAC `20:e7:c8:a7:a0:c0`), Node 2 = COM3 (MAC `6c:c8:40:05:7b:20`). Earlier COM3↔COM4 assumption was wrong — this module is on COM4. COM5 is on the machine but responds to no ESP32 tooling. |
| 2026-10-10 | 🔑🔑🔑 **Production firmware end-to-end proven on real hardware.** Both nodes flashed with `unified_node0.2`. Node 2 receives this module's production beacons at `-67 dBm` / `+9.8 dB` SNR (`U1`). A message typed into the APK — `hello node 2 I am node 1` — travelled **phone → Bluetooth → this node → 433 MHz → Node 2** and was captured on Node 2's serial at `-66`/`-69 dBm` (`U2`). **The mesh relay fired live** (hop 1→2), proving the non-blocking-relay fix on real packets. Full evidence in [`../RA02_BRINGUP_NODE2/FINDINGS.md`](../RA02_BRINGUP_NODE2/FINDINGS.md) §1g. |
| 2026-10-10 | **USB-OTG confirmed unimplemented** in the APK — `ResQPlugHardwareBridge.kt:148-150` returns success without sending, and no reader loop exists for the OTG path. The firmware *does* listen on USB serial, so only the app side is missing. **Bluetooth is the only real transport.** Left as known-outstanding work rather than fixed, so the LoRa bring-up could be closed out first. |

