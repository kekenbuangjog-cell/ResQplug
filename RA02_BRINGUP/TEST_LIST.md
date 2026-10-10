# ResQPlug — Ra-02 Bring-Up Test List

> **Hardware:** Ai-Thinker Ra-02 (SX1278 @ 433 MHz) + 30-pin ESP32 DevKit
> **Meter:** DT-830B
> **Status:** 🔴 **ROOT CAUSE FOUND — unsoldered header pins.** Awaiting soldering.
> **Run tests in order. STOP at the first FAIL.**

---

## Meter settings quick reference

| What you're measuring | DT-830B dial | Probes |
|---|---|---|
| Any voltage (3.3V, signal levels) | **DCV 20** (V--- section, number 20) | black→GND, red→test pin |
| Is this wire continuous? | **diode/beep** (►\|) symbol) | one probe each end of the wire, **board unplugged** |
| Resistor value | **2000 Ω** or **20k Ω** | board unplugged |

⚠ Black probe goes to `COM`, red to the top `VΩmA` jack. Never the `10ADC` jack.
⚠ DT-830B reads roughly ±0.1 V off on the 20 V range. 3.20–3.35 V is a good 3.3 V rail.

---

## Ra-02 pin reference (antenna at top)

```
        LEFT HEADER              RIGHT HEADER
      ┌────────────┐          ┌────────────┐
 row1 │ GND        │          │ GND        │ (skip)
 row2 │ GND (skip) │          │ NSS  ──────┼──> D17
 row3 │ 3.3V ──────┼──> 3V3   │ MOSI ──────┼──> D23
 row4 │ RST  ──────┼──> D14   │ MISO ──────┼──> D19
 row5 │ DIO0 ──────┼──> D26   │ SCK  ──────┼──> D18
 row6 │ DIO1       │          │ DIO5       │
 row7 │ DIO2       │          │ DIO4       │
 row8 │ DIO3       │          │ GND        │
      └────────────┘          └────────────┘
```

---

# PHASE 0 — Meter gate (no sketch required)

## M0 — Power rail at the module

**Tool:** DT-830B on DCV 20 · **Board:** powered, any sketch running
**Gate for:** everything

| Step | Action | Expected | Result |
|---|---|---|---|
| 1 | Black on GND, red on **LEFT [3] 3.3V** | 3.20 – 3.35 V | [ ] PASS [ ] FAIL |
| 2 | Repeat while gently wiggling the red wire | Reading stays steady | [ ] PASS [ ] FAIL |

**REMARKS:** ______________________________________________

**Verdict:**
- [x] **3.20 V** — power path confirmed good. Not a power problem, not a resistor problem.
- [ ] 2.8 – 3.1 V — sag under load → add decoupling (100 nF + 100 µF across 3V3/GND at module)
- [ ] 0.00 V — wire open → reseat, re-run M0

---

## M1 — RST at LEFT [4] ⭐ *the unanswered measurement*

**Tool:** DT-830B on DCV 20 · **Board:** running `t01_pin_states`
**Why:** your log Step 3 found RST floating, wired it to D14, then Step 4 said *"still 0xFF"* — but RST was never re-measured after rewiring. This closes that gap.

| Step | Action | Expected | Result |
|---|---|---|---|
| 1 | Upload `t01_pin_states`, open Serial @ 115200 | `NSS=D17 HIGH  RST=D14 HIGH` | [ ] PASS [ ] FAIL |
| 2 | Black on GND, red on **LEFT [4] RST** | ~3.2 V | [ ] PASS [ ] FAIL |
| 3 | Watch it during the `[3] Toggle test` in the log | Swings 0.0 ↔ 3.2 V | [ ] PASS [ ] FAIL |
| 4 | **Identify your MISO probe position** — MISO is RIGHT [4], NOT left | Confirm you probed RIGHT column | [ ] PASS [ ] FAIL |

**REMARKS:** ______________________________________________

**Verdict:**
- [ ] **A) Swings 0.0 ↔ 3.2 V** → RST path good. Go to Phase 1 / T3.
- [ ] **B) Stuck at 0.0 V** → **RST wire open. This is your bug.** Reseat D14→LEFT[4], re-run M1. Do not proceed — a chip in permanent reset always returns 0xFF.
- [ ] **C) Stuck at ~3.2 V** → RST wire landed on a permanent 3.3 V rail, not D14. Move it to GPIO14, re-run M1.
- [ ] **D) Was probed on the LEFT side** → you measured RST, not MISO. Re-probe RIGHT [4] for MISO.

---

# PHASE 1 — Sketch tests (stop at first FAIL)

---

## T1 — Pin state verification

**Sketch:** `t01_pin_states/t01_pin_states.ino`
**Tool:** Serial @ 115200 + DT-830B on DCV 20 · **Gate for:** T2–T7

### Steps
1. Upload `t01_pin_states`. Open Serial Monitor at **115200 baud**.
2. Read the `[1] ESP32 pin readback` block — all four must say `OK`.
3. With the board still running, probe each Ra-02 pin (static levels):

| # | Probe (red) | Expected | Result |
|---|---|---|---|
| 1 | LEFT [3] 3.3V | 3.20 – 3.35 V | [ ] PASS [ ] FAIL |
| 2 | LEFT [4] RST | ~3.2 V | [ ] PASS [ ] FAIL |
| 3 | RIGHT [2] NSS | ~3.2 V | [ ] PASS [ ] FAIL |
| 4 | RIGHT [3] MOSI | ~0.0 V | [ ] PASS [ ] FAIL |
| 5 | RIGHT [4] MISO | ~0.0 V (idle only — proves nothing, see row 8) | [ ] PASS [ ] FAIL |
| 6 | RIGHT [5] SCK | ~0.0 V | [ ] PASS [ ] FAIL |

4. Now the two toggle phases. **The sketch alternates between them forever**,
   so you can take as long as you need — watch the `PHASE A` / `PHASE B` banners.

| # | Phase | Probe (red) | Expected | Result |
|---|---|---|---|---|
| 7 | **A** — RST toggle | LEFT [4] RST | **swings 0.0 ↔ ~3.2 V** | [ ] PASS [ ] FAIL [ ] stuck 0 V [ ] stuck 3 V |
| 8 | **B** — MISO toggle | RIGHT [4] MISO | **swings 0.0 ↔ ~3.2 V** | [ ] PASS [ ] FAIL (stuck 0 V) |

**REMARKS:** ______________________________________________

### Interpreting rows 7 and 8

| Row 7 (RST) | Meaning |
|---|---|
| Swings | ✅ D14 wire correct — chip can be reset |
| Stuck at 0.0 V | ❌ RST wire open → chip permanently in reset → **would cause `0xFF` on every read** |
| Stuck at ~3.2 V | ⚠️ wire landed on the 3.3 V rail, not D14 |

| Row 8 (MISO) | Meaning |
|---|---|
| Swings | ✅ MISO wire continuous, chip tri-stated as expected |
| **Stuck at 0.0 V** | ❌ **MISO WIRE IS OPEN — this alone explains `0xFF` on every read** |

> An unplugged wire reads **0 V** on a meter, so row 5's idle reading proves
> nothing. **Row 8 is the only thing that proves MISO continuity.**

### Verdict
- [ ] **PASS** — readbacks OK, all static levels match, **both rows 7 and 8 swing** → T2 is now redundant (RST proven), go to **T3**
- [ ] **FAIL row 7 stuck 0 V** → RST wire open → reseat D14 → re-run T1
- [ ] **FAIL row 7 stuck 3 V** → RST wire on the 3.3 V rail → move to D14 → re-run T1
- [ ] **FAIL row 8 stuck 0 V** → **MISO wire open** → reseat D19 → RIGHT [4], re-run T1
- [ ] **FAIL a static row wrong** → that wire isn't delivering → reseat it → re-run T1

---

## T2 — Reset release

**Sketch:** `t02_rst_release/t02_rst_release.ino`
**Tool:** DT-830B on DCV 20, red on **LEFT [4] RST** · **Gate for:** T3

### Steps
1. Put the probe on LEFT [4] RST before uploading, so you see the whole sequence.
2. Upload `t02_rst_release`, open Serial @ 115200.
3. Watch the meter through `[2]` and `[3]` (6 slow pulses).

| Phase | Serial says | Meter must show | Result |
|---|---|---|---|
| `RST=LOW` | LOW | 0.0 V | [ ] PASS [ ] FAIL |
| `RST=HIGH` | HIGH | ~3.2 V | [ ] PASS [ ] FAIL |

**REMARKS:** ______________________________________________

### Verdict
- [ ] **A) Meter swings with the log** → RST path good → go to T3
- [ ] **B) Meter stuck 0.0 V** → **RST wire open** → reseat D14 wire → re-run T2. **Do not go to T3.**
- [ ] **C) Meter stuck ~3.2 V** → RST wire on a permanent 3.3 V rail, not D14 → move it → re-run T2

---

## T3 — Bare-minimum bit-bang SPI read

**Sketch:** `t03_spi_bare/t03_spi_bare.ino` · raw GPIO, no libraries · `MOSI_LOW 0`
**Tool:** Serial @ 115200 (+ meter optional)
**Speed:** `BIT_DELAY_US 20000` → ~16 Hz, slow enough for the meter to see

### Steps
1. Upload `t03_spi_bare`.
2. Read `[2] Reg 0x42`.
3. Read `[4]` — the 5-repeat consistency check.
4. Read the streaming `PD / float / PU` lines from `loop()`.

| Check | Expected | Result |
|---|---|---|
| Reg 0x42 | `0x12` | [ ] PASS [ ] **FAIL — read `0x6A`** |
| 5 repeats identical | YES | [ ] PASS [ ] **FAIL — `0xB5, 0x95, 0xD5, 0xD5, 0x55`** |
| **PD / float / PU all agree** | all three equal | [ ] PASS [ ] **FAIL — `PD=0x00`, `PU=0xFF`, 9/9** |

**REMARKS:** Fails identically at 200 Hz and 16 Hz → not a timing artefact.
`PD=0x00 / PU=0xFF` proves **MISO is floating — the chip is not answering at
all**. The varying values were floating-line noise, not corrupted chip data.

### Verdict
- [ ] **`0x12`** → chip alive, RST/NSS/SCK/MISO/power all good → go to T4
- [ ] **Inconsistent across 5 reads** → intermittent contact (original hypothesis)
- [ ] **`0xFF` consistent** → nobody driving MISO → check NSS reaches RIGHT [2]
- [ ] **`0x00` consistent** → MISO held low → check SCK reaches RIGHT [5]
- [ ] **Other non-trivial value** → chip IS responding → SPI mode/wiring order issue
- [x] **`PD=0x00` and `PU=0xFF` in every read** → 🔴 **MISO FLOATING, chip not
      answering.** Not a wire fault (MISO wire proven in T1 Phase B). Orientation
      and module GND subsequently **both eliminated** — root cause is the
      unsoldered header pins, see **ROOT CAUSE** below.

### Next actions — ALL THREE COMPLETED 2026-10-07

| # | Action | Result |
|---|---|---|
| **A** | Confirm the header was counted **from the antenna end** | ✅ **Orientation correct** — U.FL socket sits at the pin-1 end. Eliminated. |
| **B** | **Measure module GND** | ✅ **Connected.** Test 1 = `010` (ESP32 GND ↔ LEFT [1]); Test A = `000`/`001` (GND pins reach the module PCB via the U.FL shell). Eliminated. |
| **C** | Confirm NSS dips during a read | ✅ **Dips to ~0 V repeatedly**, back to ~3.2 V — chip-select is delivered dynamically, not just statically. |

### ROOT CAUSE FOUND — Test E, 2026-10-07

The **pin → module-pad** joint was never tested until now, because the
header pins were **pushed into the module's holes, never soldered.**

| Pin | Bare pad (Test D, pins removed) | With pin inserted (Test E) | |
|---|---|---|---|
| 3.3V | `421` | **`1`** | 🔴 open |
| RESET | `709` | **`1`** | 🔴 open |
| NSS | `697` | **`1`** | 🔴 open |
| MOSI | `697` | **`1`** | 🔴 open |
| SCK | `697` | **`1`** | 🔴 open |
| MISO | `697` | **`500` fluctuating** | 🔴 marginal — intermittent contact |

**Every pad reaches the silicon. No pin reaches its pad.** The `500`-and-flickering
MISO reading is the direct cause of the junk values T3 printed (`0x29`, `0x4A`,
`0x94` …): a line that is mostly open with brief moments of contact.

**Fix:** solder all 16 header pins. Re-run Test E until every pin matches its
bare-pad baseline, then re-run T3.

### ✅ POST-SOLDER — T3 re-run 2026-10-07

**Test E after soldering:** `345 / 527 / 533 / 503 / 502 / 505`, GND `001` →
**6/6 signal pins connected** (was 0/6). Bridge check: all `1` (no shorts);
3.3V↔RESET `1263` = two ESD diodes through the die, **not** a bridge.

**Two T3 captures:**

| Check | Expected | Run 1 | Run 2 |
|---|---|---|---|
| `Reg 0x42` | `0x12` | `0x0C` | **`0x12`** ✅ |
| 5 repeats identical | YES | ❌ `0x3F, **0x12**, 0x47, 0x00, 0x55` | ❌ `0x0C, 0x29, 0x0C, 0x29, 0x00` |
| `PD=0x00` + `PU=0xFF` floating pair | **must NOT appear** | ✅ never appears | ✅ never appears |
| Pull rows driving MISO | chip drives | ✅ `PU=0x12`, `PD=0x55`, `PD=0x0F` | ✅ `PD=0x0F`, `PD=0x28`, `PU=0x55` |

Run 2's headline read printed **`[PASS] 0x12 - CHIP IS ALIVE AND RESPONDING`**.

### ✅ T3 FINAL — PASS, 2026-10-07

Six breadboard wires were reseated **one conductor per hole** (MOSI D23 ·
MISO D19 · SCK D18 · NSS D17 · 3V3 · GND). Result:

```
[2] Reading register 0x42 (silicon version)...
    Reg 0x42 = 0x12
[4] Reg 0x42 read 5x (consistency check):
    #1 = 0x12   #2 = 0x12   #3 = 0x12   #4 = 0x12   #5 = 0x12
    Consistent : YES
[PASS] 0x12 - CHIP IS ALIVE AND RESPONDING.
    [1..7]  PD=0x12   float=0x12   PU=0x12   <== EXPECTED   [PASS]
```

| Check | Required | Result |
|---|---|---|
| `Reg 0x42` | `0x12` | ✅ `0x12` |
| 5× repeat identical | YES | ✅ **5/5 all `0x12`** |
| `PD = float = PU` | all three agree | ✅ **7/7 rows all `0x12`** |
| Floating pair `PD=0x00`/`PU=0xFF` | must NOT appear | ✅ never appears |
| MARGINAL rows | must NOT appear | ✅ none |

### Verdict — FINAL

- [x] **PASS → T6.** Power, RST, NSS, SCK, MOSI, MISO all proven at the
      register level. T4 and T5 are now redundant (they existed only to
      isolate a fault that the soldering + reseat fixed).

**T6 PASS** `LoRa.begin(433E6)` → `Result : true` ·
**T7 PASS** 5/5 beacons transmitted.

---

## T4 — Hardware SPI, 3 speeds

**Sketch:** `t04_register_read/t04_register_read.ino`
**Tool:** Serial @ 115200 · **Compare against T3**

### Steps
1. Upload `t04_register_read`.
2. Compare the three speed rows in `[1]`.
3. Compare Mode 0 vs Mode 1 in `[2]`.

| Check | Expected | Result |
|---|---|---|
| 1 MHz / 250 kHz / 100 kHz | `0x42 = 0x12` at all three | [ ] PASS [ ] FAIL |
| No DRIFT flag on any row | none | [ ] PASS [ ] FAIL |
| Mode 0 vs Mode 1 | same value | [ ] PASS [ ] FAIL |

**REMARKS:** ______________________________________________

### Verdict
- [ ] **PASS everywhere** → hardware SPI good → go to T6 (skip T5 unless you still need to confirm the NSS pin)
- [ ] **Mixed values across speeds** → **intermittent contact** (this is what log Step 10 saw) → reseat wires. A dead chip never varies.
- [ ] **T3 pass + T4 fail** → hardware SPI config/pin issue, chip is fine
- [ ] **T3 fail + T4 fail** → back to T3 verdict, physical layer
- [ ] **Different in Mode 0 vs Mode 1** → marginal timing/contact

---

## T5 — NSS auto-discovery

**Sketch:** `t05_nss_autodiscover/t05_nss_autodiscover.ino` · tries GPIO 17,16,15,27,25,5,12
**Tool:** Serial @ 115200 · **Purpose:** ends the D5-vs-D17 question with a measurement

### Steps
1. Upload `t05_nss_autodiscover`.
2. Read the `[1]` scan table.
3. Note which GPIO prints `<=== RESPONSIVE`.

| Check | Result |
|---|---|
| Which GPIO responded 0x12 (3/3)? | GPIO _______ |
| Is it a strapping pin (5, 12, 15)? | [ ] Yes [ ] No |

**REMARKS:** ______________________________________________

### Verdict
- [ ] **A GPIO responded 0x12** → that is your real NSS pin → set it in `ra02_pins.h`, every sketch's SYNC block, and T6/T7's `PIN_NSS` → go to T6
- [ ] **Only GPIO 5 responded** → it works but is a boot strapping pin → use it to prove the link, then move to a safe pin as follow-up
- [ ] **No candidate responded** → NSS is not the problem → go back to M1 (RST), M0 (power), then T3
- [ ] **Multiple responded** → another pin is floating/shorted onto the bus → inspect for shorts

---

## T6 — LoRa library init

**Sketch:** `t06_lora_begin/t06_lora_begin.ino` · **requires** `sandeepmistry/arduino-LoRa`
**Gate for:** T7

> Confirm `PIN_NSS` inside the sketch matches what T5 discovered *before* uploading.

### Steps
1. Install the library: Sketch → Include Library → Manage Libraries → search **"LoRa"** → install *by Sandeep Mistry*.
2. Set `PIN_NSS` in the sketch if T5 found a different pin.
3. Upload and open Serial @ 115200.

| Check | Expected | Result |
|---|---|---|
| `LoRa.begin(433E6)` | `true` | [ ] PASS [ ] FAIL |
| Config block prints | TxPower/SF/BW/CR shown | [ ] PASS [ ] FAIL |
| 5 s receive sweep | completes without crash | [ ] PASS [ ] FAIL |

**REMARKS:** ______________________________________________

### Verdict
- [ ] **PASS** → chip accepted by the library → go to T7 (antenna ON)
- [ ] **FAIL (`false`)** → work the printed checklist in order: (1) does T3 return 0x12? (2) is `PIN_NSS` right? (3) is RST swinging? (4) antenna on? (5) 3.3 V under power? (6) library installed?

---

## T7 — Beacon transmit

**Sketch:** `t07_beacon_tx/t07_beacon_tx.ino` · **⚠ ANTENNA MUST BE ATTACHED**

> Transmitting with no antenna reflects power into the PA and can destroy the SX1278.

### Steps
1. **Snap the IPEX 433 MHz antenna on.** Verify it's seated.
2. Upload `t07_beacon_tx`, open Serial @ 115200.
3. Watch all 5 beacons.

| Check | Expected | Result |
|---|---|---|
| Beacons sent | 5/5 `SENT` | [ ] PASS [ ] FAIL |
| No `TIMEOUT` | none | [ ] PASS [ ] FAIL |
| EXT LED blinks per TX | visible | [ ] PASS [ ] FAIL |

**REMARKS:** ______________________________________________

### Verdict
- [ ] **PASS 5/5** → full link proven: power ✅ SPI ✅ library ✅ RF ✅
- [ ] **TIMEOUT** → most likely **antenna not connected**; else supply sag (meter 3.3 V during TX) or PA damaged by an earlier no-antenna TX
- [ ] **PASS but no range** → flash `MODE=1` on a second pair to confirm reception

---

# PHASE 2 — Record results

Fill in the summary after running Phase 1:

| Test | Result | Date | Notes |
|---|---|---|---|
| M0 Power | **3.20 V** ✅ | 2026-10-06 | Wire carries it |
| M1 RST | **3.19 V static, swings in Phase A** ✅ | 2026-10-06 | D14 → LEFT [4] proven |
| T1 Pin states | **PASS 8/8** ✅ | 2026-10-06 | Phase A RST swing + Phase B MISO swing both observed |
| T2 RST release | **SKIPPED** — covered by T1 Phase A | 2026-10-06 | redundant |
| **Test 1** GND continuity | **`010`** ✅ | 2026-10-07 | ESP32 GND ↔ LEFT [1] connected |
| **Test 2** Orientation | **PASSED (visual)** ✅ | 2026-10-07 | U.FL socket at pin-1 end — no meter needed |
| **Test 3** SCK + MOSI | **`001` / `001`** ✅ | 2026-10-07 | D18 ↔ RIGHT[5], D23 ↔ RIGHT[3] both continuous |
| **NSS dynamic dip** | **0.0 V ↔ 3.2 V repeatedly** ✅ | 2026-10-07 | chip-select delivered during a live read |
| **T3 on module 1** | 🔴 **FAIL — MISO floating** | 2026-10-06 | `PD=0x00 / PU=0xFF`, 9/9. See FINDINGS §1b |
| **T3 on module 2** | 🔴 **FAIL — MISO floating, identical** | 2026-10-07 | `PD=0x00 / PU=0xFF`, 5/5 → two chips, same failure |
| **Test A** header→PCB (GND) | **`000` / `001`** ✅ | 2026-10-07 | GND pins are joined to the module board |
| **Test D** pad→chip (diode) | **`421` `709` `697` `697` `697` `697`** ✅ | 2026-10-07 | **all pads reach the die — module is NOT dead** |
| **Test E** pin→pad (diode) | 🔴 **`1` `1` `1` `1` `1` + MISO `500` flicker** | 2026-10-07 | **ROOT CAUSE — pins are not soldered** |
| **Solder repair** | **all 16 pins** ✅ | 2026-10-07 | user's first soldering job — joints accepted |
| **Test E after solder** | **`345` `527` `533` `503` `502` `505`, GND `001`** ✅ | 2026-10-07 | **6/6 connected, was 0/6** — no `1`, no `000` |
| **Bridge check** | **all `1`; 3.3V↔RESET `1263`** ✅ | 2026-10-07 | no shorts; `1263` = 2 ESD diodes through the die |
| **T3 post-solder, run 2** | **`Reg 0x42 = 0x12`** ✅ | 2026-10-07 | **CHIP IS ALIVE** — floating pair never appears |
| **T3 5× consistency** | 🟡 **still FAILS** | 2026-10-07 | remaining marginal contact — see FINDINGS §1e |
| **Reseat 6 breadboard wires** | **one conductor per hole** ✅ | 2026-10-07 | MOSI D23 · MISO D19 · SCK D18 · NSS D17 · 3V3 · GND |
| **T3 final** | **`0x12`, 5/5 identical, `PD=float=PU` 7/7** ✅ | 2026-10-07 | **T3 PASS — zero marginal rows** |
| **LoRa library** | **0.8.0 installed** ✅ | 2026-10-07 | `arduino-cli lib install "LoRa"` |
| **T6 LoRa begin** | **`Result : true`** ✅ | 2026-10-07 | **`SUCCESS - Ra-02 online at 433.00 MHz`** |
| **T7 Beacon TX** | **5/5 SENT** ✅ | 2026-10-07 | **`VERDICT T7: PASS - all beacons transmitted`** |
| T4 HW SPI | ⬜ **skipped** | 2026-10-07 | only existed to isolate a fault that T3+reseat fixed |
| T5 NSS discovery | ⬜ **skipped** | 2026-10-07 | D17 proven by T3, T6 and T7 all passing on it |
| **`unified_node0.2` boot** | **`0x12` + `ONLINE @ 433.00 MHz`** ⚡ | 2026-10-10 | **First production-firmware run on hardware.** Probe 1 succeeded first try, so NSS on GPIO 17 is confirmed on the *library* path too, not just bit-bang. BT name `ResQPlug-POD-C8E720`. See FINDINGS §1f |
| T8 two-node link | ✅ **PASS 2026-10-10** | 2026-10-10 | **Both directions.** Node 2 heard `RA02-UNIT1` at RSSI `-66/-66/-67 dBm`, SNR `9.5/9.5/9.8 dB`; Node 1 heard `RA02-UNIT2` at `-66/-66/-66 dBm`, `9.8/9.5/9.5 dB`. **6/6 packets, 0 drops.** Lives in [`../RA02_BRINGUP_NODE2/`](../RA02_BRINGUP_NODE2/TEST_LIST.md) — see §1f |

**FINAL VERDICT: ✅ ALIVE — BRING-UP COMPLETE**

| Question | Answer |
|---|---|
| Is the module dead? | **NO.** Every pad reaches the silicon (Test D) and the chip returns `0x12`. |
| Was the wiring correct? | **YES** — pinout, orientation, and all eight ESP32→header paths verified. |
| What was wrong? | **Two stacked faults**, both in the connection environment: |
| &nbsp;&nbsp;Fault 1 | **Header pins were never soldered** — friction only → Test E read `1` on all six signal pins (§1d). |
| &nbsp;&nbsp;Fault 2 | **Breadboard wires sharing holes / not fully seated** → 5× repeat inconsistent (§1e). |
| How was it proven fixed? | **T3** `0x12` 5/5 identical with `PD=float=PU` 7/7 · **T6** `LoRa.begin(433E6)` = `true` · **T7** 5/5 beacons transmitted. |
| Eliminated all along the way | dead silicon · wrong pinout · wrong wire positions · orientation · module GND · jumper wires · NSS delivery · solder-bridge on 3.3V/RESET · timing/speed artefact · MOSI/MISO swap. |

**The original framing — "Sonnet says dead / Gemini says alive" — is resolved:
the module was never dead. It was never soldered.**

**Phase 3 — RECONCILE — ✅ complete.** `GPIO 5 → 17` swept through 3 markdown
docs and 4 firmware sketches (7 + 9 edits). All 4 sketches compile.
See §3 below.

---

# PHASE 3 — Reconcile docs & firmware ✅ *COMPLETED 2026-10-07*

> Gate: the working NSS pin had to be **proven**, not guessed. T3 (`0x12`,
> 5/5, `PD=float=PU`), T6 (`LoRa.begin(433E6)` = `true`) and T7 (5/5 beacons)
> all pass on **D17**, so standardising on 17 now swaps in a tested answer.

## 3a. Markdown docs — ✅ 7/7 done

| File | Line* | Was | Now | |
|---|---|---|---|---|
| `LORA_WIRING_AND_DIY_HOOKUP_GUIDE.md` | 26 | ASCII `NSS  ---> ESP32 GPIO 5` | **GPIO 17** | ✅ |
| `LORA_WIRING_AND_DIY_HOOKUP_GUIDE.md` | 115 | `#define LORA_SS    5` | **17** | ✅ |
| `LORA_WIRING_AND_DIY_HOOKUP_GUIDE.md` | 35 | `[C1] [C2]` drawn, never explained | **new §"About [C1] [C2]"** — 100 nF + 100 µF across 3V3/GND at the module, why they matter, polarity warning | ✅ |
| `HARDWARE_OTG_TROUBLESHOOTING.md` | 151 | `NSS / CS` → **GPIO 5** | **GPIO 17** + strapping-pin warning | ✅ |
| `HARDWARE_OTG_TROUBLESHOOTING.md` | 155 | `DIO0` → **GPIO 2** | **GPIO 26** + strapping/LED warning | ✅ |
| `EXTERNAL_LED_FIREBASE_MVP_ACTIVITY_GUIDE.md` | 40 | strapping list `0, 2, 12, 15` | **`0, 2, 5, 12, 15`** | ✅ |
| `EXTERNAL_LED_FIREBASE_MVP_ACTIVITY_GUIDE.md` | 41 | LoRa pins `GPIO 5, 14, 18, 19, 23, 26` | **`GPIO 17, 14, 18, 19, 23, 26`** | ✅ |

\* line numbers **as found, before editing**. The wiring guide's code block
shifted down ~19 lines after the new C1/C2 section was inserted.

> **Deliberately untouched:** `LORA_RA02_ESP32_TROUBLESHOOTING_LOG.md` (every
> `GPIO 5` there is historical narrative or the *warning against* it) ·
> `HARDWARE_OTG_TROUBLESHOOTING.md` **line 156** (the RST row's `GPIO 5` sits
> in the **ESP32-S3** column, where 5 is not a strapping pin).

## 3b. Firmware — ✅ 9/9 edits, 4 files

| File | Line* | Was | Now | |
|---|---|---|---|---|
| `resqplug_unified_node0.2` | 40 | `#define LORA_SS    5` | **17** | ✅ |
| `resqplug_unified_node0.2` | 309 | Serial `"… MOSI:23, SS:5 …"` | **`SS:17`** | ✅ ⭐ |
| `resqplug_unified_node0.2` | 339 | comment `GPIO 5` | **17** | ✅ |
| `resqplug_unified_node0.2` | 340 | `"Probe 2 (Software CS on GPIO 5)"` | **17** | ✅ |
| `resqplug_unified_node0.1` | 40 | `#define LORA_SS    5` | **17** | ✅ |
| `resqplug_lora_test` | 7 | comment `NSS -> GPIO 5` | **17** | ✅ |
| `resqplug_lora_test` | 24 | `#define LORA_SS    5` | **17** | ✅ |
| `resqplug_lora_diagnostic` | 11 | comment `NSS -> ESP32 GPIO 5` | **17** | ✅ |
| `resqplug_lora_diagnostic` | **24** | **`#define PIN_NSS    5`** | **17** | ✅ ⭐⭐ |
| `resqplug_lora_diagnostic` | 78 | comment `on GPIO 5` | **17** | ✅ |

⭐ **Line 309** was missing from the original table — a live Serial string that
would have kept printing `SS:5`.
⭐⭐ **Line 24** was also missing — and it is the *actual* `#define`, not just a
comment. The original table listed only lines 11 and 78, both of which are
comments, so the original Phase 3 would have fixed the prose and left the bug.

`resqplug_lora_beacon_test` already had `PIN_NSS 17` — no change.
`esp32_node`, `esp32_led_local_blink`, `resqplug_bluetooth_radio` use no LoRa
pins — no change.

### Verification — ✅ all four checks pass (decision: **compile only, no flashing**)

| Check | Result |
|---|---|
| Grep: `LORA_SS` / `PIN_NSS` still set to `5` | ✅ **0 hits in any `.ino`/`.h`** — remaining hits are only the change-record tables in `TEST_LIST.md`/`PLAN.md` |
| Grep: stray `GPIO 5` outside the log | ✅ **0 incorrect hits** — every survivor is a *warning against* GPIO 5, a historical record, or the deliberately-untouched ESP32-S3 column (`HARDWARE_OTG` L156) |
| `arduino-cli compile --warnings all` × 4 sketches | ✅ **4/4 PASS** — `lora_test` 278 856 B · `lora_diagnostic` 277 432 B · `unified_node0.2` 1 080 047 B (82%) · `unified_node0.1` 1 075 976 B (82%). Only library-internal `B111`/`B1000` deprecation warnings |
| UTF-8 / no BOM / LF-only on every touched file | ✅ **8/8 pass** |

> **Note:** `unified_node0.1`/`0.2` cannot be compiled in place because
> Arduino requires *folder name == file name*, and the folder is
> `resqplug_unified_node0.2` while the file is `resqplug_unified_node.ino`.
> They were compiled from a name-matched temporary copy. **Pre-existing repo
> structure, unrelated to the pin change** — flag if you want it fixed.

## 3c. Log update — ✅ done

`LORA_RA02_ESP32_TROUBLESHOOTING_LOG.md`:
- ✅ meter readings from Phase 0
- ✅ the 6-way NSS contradiction table (§2 of `FINDINGS.md`)
- ✅ corrected final verdict (§10)
- ✅ **§11 BRING-UP COMPLETE** — T3 / T6 / T7 results, both stacked faults

