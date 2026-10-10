# ResQPlug — Ra-02 Bring-Up Plan

> **Goal:** determine definitively whether the Ra-02 (SX1278) is alive, and if it
> is, get `LoRa.begin()` passing — then reconcile the repo's contradictory docs.
>
> **Rule:** nothing in Phase 3 runs until Phase 1 names the working NSS pin.

---

## Why this plan exists

Two models disagreed about the module ("dead" vs "alive") from the same log.
Neither had new data. The plan replaces interpretation with measurement:

| Ambiguity | Resolved by |
|---|---|
| Is the chip dead? | T3 — consistent `0x12` vs consistent `0xFF` vs **inconsistent** |
| Is RST actually releasing? | M1 + T2 — meter on LEFT [4] |
| Which pin is NSS really on? | T5 — scan instead of guess |
| Is it a power problem? | M0 — already answered: **3.20 V, no** |

**Working theory — OVERTURNED 2026-10-07.** The log's Step 10 (mixed
`0x00`/`0xFF` across speeds) and Step 9 (two chips failing identically) pointed
at *some* connection fault, and that part was right — but the fault was blamed
on the breadboard jumper wires (Step 10/11). The wires are fine.

**Actual root cause:** the Ra-02's header pins were **pushed into the module's
holes and never soldered.** Test D (bare pads, diode mode) proved the pads and
the silicon are healthy; Test E proved no pin reaches its pad. Full chain:

```
ESP32 ──wire── breadboard ──header pin   ✅ proven
header pin ═══╳═══════ module pad        🔴 THE GAP — friction only
module pad ────── silicon                ✅ proven
```

---

## Phase 0 — Meter gate

*No sketch needed for M0. Do these first; they take 2 minutes and can end the hunt.*

| ID | Measurement | Resolves |
|---|---|---|
| **M0** | 3.3 V at LEFT [3] | Power — **already done: 3.20 V ✅** |
| **M1** | RST at LEFT [4], while `t01_pin_states` runs | **The open question.** Stuck at 0 V = chip in permanent reset = full explanation for every `0xFF` |

**Gate:** if M1 = B (stuck 0 V), fix the wire and re-run M1 before any Phase 1 test.

---

## Phase 1 — Sketch tests

Run in order. **Stop at the first FAIL.** Each sketch prints a `VERDICT` line
mapping the result to a physical action, so no log interpretation is needed.

```
M0 ✅ ──> M1 ──> T1 ──> T2 ──> T3 ──> T4 ──┬──> T6 ──> T7
                                            │
                                            └──> T5 (if NSS pin still uncertain)
```

| # | Sketch | Question it answers |
|---|---|---|
| T1 | `t01_pin_states` | Does every ESP32 pin reach the corresponding Ra-02 pin? |
| T2 | `t02_rst_release` | Does RST actually swing 0 ↔ 3.2 V on the module? |
| T3 | `t03_spi_bare` | Can a raw, library-free, MOSI-tied-low read get `0x12`? |
| T4 | `t04_register_read` | Is hardware SPI stable across 3 speeds? (confirms/refutes log Step 10) |
| T5 | `t05_nss_autodiscover` | Which GPIO is the NSS wire actually on? |
| T6 | `t06_lora_begin` | Will the library accept the module? |
| T7 | `t07_beacon_tx` | Does the radio put energy on the air? |

### Decision table for T3's four outcomes

| `0x42` result | Meaning | Action |
|---|---|---|
| `0x12` | **Alive** | → T4 |
| varies across 5 reads | **Intermittent contact** | reseat MISO/SCK/NSS one at a time |
| `0xFF` always | Nobody driving MISO | check NSS reaches RIGHT [2], MISO RIGHT [4], RST (T2), power (M0) |
| `0x00` always | Selected but not shifting | check SCK reaches RIGHT [5], or MISO shorted to GND |

### Design notes on the sketches

- **T3 ties MOSI low in software** (`MOSI_LOW 1`), removing MOSI as a variable.
  The command becomes `0x00` — a register read of reg 0x00 — which still forces
  the chip to drive MISO with a real value.
- **T3 runs at ~200 Hz** (2.5 ms/bit) so even a poor wire-wrap contacts on every clock.
- **T5 scans 17, 16, 15, 27, 25, 5, 12** and flags strapping pins if they win.
  It drives every candidate HIGH during idle so two NSS lines are never asserted at once.
- **T7 refuses to be silent about the antenna** — transmitting into an open
  circuit reflects power into the PA and can destroy the SX1278.

---

## Phase 2 — Record

Fill the summary table in `TEST_LIST.md`. Land on one of:

`ALIVE` · `COMM FAIL` · `RST HELD LOW` · `NSS MISWIRED` · `OTHER`

---

## Phase 3 — Reconcile docs & firmware ⛔ gated on T5

**Do not start until T5 names the pin.** Standardising everything on D17 before
tests prove D17 would replace one wrong answer with another.

**Scope:** 7 markdown edits across 3 files + 5 firmware edits across 4 sketches,
all listed with exact line numbers in `TEST_LIST.md` → Phase 3.

Notable: `resqplug_unified_node0.2` (production) and both old test sketches all
define `LORA_SS 5`, while the troubleshooting log insists on 17. Whichever way
T5 goes, at least three of those files are currently wrong.

---

## Deliverables

```
RA02_BRINGUP/
├── README.md          entry point, how to run the sequence
├── PLAN.md            this file
├── TEST_LIST.md       step-by-step checklist with expected values + Remarks
├── FINDINGS.md        evidence collected so far
├── ra02_pins.h        canonical pin definitions
└── t01 … t07/         seven independent Arduino sketches
```

---

## Status

| Phase | State |
|---|---|
| 0 — Meter gate | ✅ **M0 done** (3.20 V). **M1 done** — static 3.19 V + Phase A swing. |
| 1 — Sketch tests | ✅ **T1 PASSED 8/8.** T2 skipped (covered by T1). ✅ **T3 PASS** `0x12` 5/5 identical. ✅ **T6 PASS** `LoRa.begin` → `true`. ✅ **T7 PASS** 5/5 beacons SENT. T4/T5 skipped — they existed only to isolate a fault that is now fixed. |
| 1b — Physical tests | ✅ **Test 1** GND `010` · **Test 2** orientation (visual) · **Test 3** SCK/MOSI `001` · **NSS dip** live · **Test A** GND→PCB `000`/`001` |
| 1c — Diode tests | ✅ **Test D** all pads → die (`421`/`709`/`697`×4). ✅ **Test E after solder** 6/6 (`345`/`527`/`533`/`503`/`502`/`505`). ✅ Bridge check clean |
| 1d — Repair | ✅ **All 16 pins soldered** + **six breadboard wires reseated** one-conductor-per-hole |
| 2 — Record | ✅ **Recorded** — `TEST_LIST.md` Phase 2 + FINAL VERDICT, `FINDINGS.md` §1d/§1e |
| 3 — Reconcile | ✅ **DONE 2026-10-07** — 7 doc edits + 9 firmware edits across 7 files. All 4 sketches compile. NSS=D17 was proven by T3/T6/T7 before the sweep ran. |

### Verdict — all links proven end to end

| Link | Status |
|---|---|
| ESP32 → header pin | ✅ all 8 proven |
| header pin → module pad | ✅ soldered — Test E 6/6 |
| module pad → silicon | ✅ proven (Test D) |
| chip answering on SPI | ✅ **T3 `0x12`, 5/5, `PD=float=PU`** |
| **library accepts the chip** | ✅ **T6 `LoRa.begin(433E6)` → `true`** |
| **RF output** | ✅ **T7 — 5/5 beacons transmitted** |
| orientation · pinout · GND · labels | ✅ all eliminated |

### Compile status (arduino-cli 1.5.1 / ESP32 core 3.3.11, `--warnings all`)

| Sketch | Result |
|---|---|
| t01, t02, t03, t04, t05 | ✅ **COMPILE OK — zero warnings** |
| t06, t07 | ✅ **COMPILE OK** (LoRa 0.8.0 installed 2026-10-07; only library-internal `B111`/`B1000` deprecation warnings) |

### Critical path — COMPLETE

```
SOLDER all 16 header pins          ✅ done 2026-10-07
        │
        ├─> Test E — 6/6 connected  ✅
        │
        └─> reseat 6 breadboard wires, one conductor per hole   ✅
                  │
                  ├─> T3 `0x12`, 5/5 identical, PD=float=PU    ✅ PASS
                  ├─> install LoRa lib 0.8.0                    ✅
                  ├─> T6 `LoRa.begin(433E6)` → true            ✅ PASS
                  └─> T7 5/5 beacons SENT                       ✅ PASS
```

Phase 3's gate has moved from **T5** to **T3**, and T3 has now passed — so the
GPIO 5 → 17 sweep across the repo is safe to perform.

**Note on the iron:** T6/T7 also need `arduino-cli lib install "LoRa"`, which
writes to `C:\Users\KEN\Documents\Arduino\libraries\`.

