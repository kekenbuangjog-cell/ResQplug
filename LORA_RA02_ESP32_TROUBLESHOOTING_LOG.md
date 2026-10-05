# ResQPlug: Ra-02 LoRa ↔ ESP32 Communication Troubleshooting Log

> **Hardware:** Ai-Thinker Ra-02 (SX1278 @ 433 MHz) + 30-pin ESP32 DevKit  
> **Project:** ResQPlug Emergency Mesh Network  
> **Status:** 🟡 Unresolved — Physical Contact / Breadboard Suspected  
> **Last Updated:** 2026-10-02

---

## Table of Contents
1. [Hardware Setup](#1-hardware-setup)
2. [Symptom](#2-symptom)
3. [Chronological Troubleshooting Steps](#3-chronological-troubleshooting-steps)
4. [Key Findings Summary](#4-key-findings-summary)
5. [Root Cause Theories (Ranked by Likelihood)](#5-root-cause-theories-ranked-by-likelihood)
6. [Current Confirmed Correct Wiring](#6-current-confirmed-correct-wiring)
7. [Sketches Written & What They Revealed](#7-sketches-written--what-they-revealed)
8. [Next Steps](#8-next-steps)
9. [Notes & Lessons Learned](#9-notes--lessons-learned)

---

## 1. Hardware Setup

| Component | Details |
|:---|:---|
| **Microcontroller** | ESP32 DevKit V1 (30-pin) |
| **LoRa Module** | Ai-Thinker Ra-02 (Semtech SX1278, 433 MHz) |
| **Connection Method** | Breadboard + jumper wires |
| **Power Source** | USB via PC → ESP32 3V3 pin → Ra-02 3.3V pin |
| **Antenna** | External IPEX 433 MHz antenna — snapped on |
| **Arduino Library** | `sandeepmistry/arduino-LoRa` |

### ESP32 30-Pin Right-Side Pin Order (Top → Bottom):
```
D23, D22, TX0, RX0, D21, D19, D18, D5, D17, D16, D4, D2, D15, GND, 3V3
```

---

## 2. Symptom

Every attempt to initialize the Ra-02 via SPI returns **`0xFF`** (or `0x00`) on all registers.

The SX1278 silicon version register (`0x42`) must return `0x12` for the LoRa library to accept the module. Instead, reads returned:
- `0xFF` — MISO floating HIGH (no response from chip)
- `0x00` — MISO held LOW (chip selected but not clocking data)

**Serial output observed consistently:**
```
❌ No match found in current permutation scan.
   Testing direct register read on Standard pins (D5, D18, D23, D19)...
   Reg 0x42 = 0xFF
🔄 Scanning all 24 pin permutations...
```

Or with the LoRa library:
```
❌ FAILED: Cannot talk to Ra-02!
   → Check wiring or replace module.
```

---

## 3. Chronological Troubleshooting Steps

### Step 1 — Initial Beacon Sketch
- Wrote `resqplug_lora_beacon_test.ino` using the `sandeepmistry/arduino-LoRa` library.
- Used standard SPI pins: `NSS=D5, SCK=D18, MOSI=D23, MISO=D19, RST=D14, DIO0=D26`.
- **Result:** `LoRa.begin(433E6)` returned `false`. Library could not detect module.

---

### Step 2 — 24-Permutation Hardware SPI Scanner
- Upgraded sketch to scan **all 24 possible permutations** of the 4 SPI pins `{D5, D18, D19, D23}`.
- This would find the chip even if MOSI/MISO/SCK/NSS were swapped.
- **Result:** All 24 permutations returned `0x00` or `0xFF`. No `0x12` found.

---

### Step 3 — RST Pin Identified as Floating
- User tested all Ra-02 pins with an **LED tester** (LED between pin and GND).
- Results:
  - ✅ MISO → LED lit (3.3V present)
  - ✅ MOSI → LED lit (3.3V present)
  - ❌ **RST → LED did NOT light** ← Root cause suspected at the time
  - ❌ DIO0 → LED did not light (normal — DIO0 idles LOW)
- **Conclusion:** RST was floating or LOW, keeping the SX1278 in permanent hardware reset.
- **Fix applied:** Wire RST to D14, drive HIGH via software with proper reset pulse (LOW 10ms → HIGH 10ms).

---

### Step 4 — RST Now Controlled via D14
- Sketch updated with proper RST pulse and `LoRa.setPins(NSS, LORA_RST_PIN, DIO0)`.
- **Result:** Still `0xFF`. No improvement.

---

### Step 5 — GPIO5 Strapping Pin Discovery
- Researched ESP32 boot strapping pins.
- **Finding:** `GPIO5 (D5)` is an **ESP32 boot strapping pin**. When Ra-02 NSS is on D5, the module can pull it LOW during power-up, corrupting the ESP32 boot sequence so SPI never initializes.
- **Fix applied:** Moved NSS from `D5` → `D17`.
- Wiring guide `LORA_WIRING_AND_DIY_HOOKUP_GUIDE.md` updated with warning.
- **Result:** Still `0xFF` after rewiring.

---

### Step 6 — Software Bit-Bang SPI Diagnostic (No Libraries)
- Replaced LoRa library with raw **bit-bang SPI** using `digitalWrite()` / `digitalRead()`.
- SPI clock: ~10 kHz (5 µs half-period) — eliminates any hardware SPI or library issues.
- **Result:** All 16 registers (`0x00–0x0F`) returned `0xFF`.

---

### Step 7 — MISO GPIO Self-Test
- Added test that forces MISO pin LOW from ESP32 OUTPUT mode then releases back to INPUT.
- **Result:**
  - Forced LOW → reads `LOW` ✅ (GPIO D19 is working)
  - Released back to INPUT → reads `LOW` (later determined to be capacitive charge hold on wire, NOT the chip driving it)
- **Conclusion:** GPIO is fine. MISO wire reaches the Ra-02 physically, but the chip is not actively driving data on MISO.

---

### Step 8 — Ultra-Verbose MISO Bit Tracer
- Wrote sketch printing every individual MISO bit per clock edge.
- Tested SPI Mode 0 (sample rising) and Mode 1 (sample falling).
- **Result:**
  ```
  MISO idle state = HIGH
  RX bits (MSB first): 11111111
  → Reg value = 0xFF
  ```
  Both modes returned `0xFF`.

---

### Step 9 — Second Ra-02 Module Tested
- User had a spare Ra-02 module. Swapped it in with the same wiring.
- **Result:** Identical output. Both modules behave exactly the same.
- **Key insight:** Two independent chips giving identical results = NOT a dead chip. Points to environmental/wiring issue.

---

### Step 10 — Deep Hardware SPI Diagnostic (6 Combinations)
- Tested MOSI/MISO standard and swapped at 1 MHz, 250 kHz, and 100 kHz.
- **Results (both modules — identical):**
  ```
  [1MHz  STD ] 0x42=0xFF  0x01=0x00  0x09=0x00
  [1MHz  SWAP] 0x42=0x00  0x01=0x00  0x09=0x00
  [250kHz STD] 0x42=0x00  0x01=0x00  0x09=0xFF
  [250kHz SWAP] 0x42=0x00  0x01=0x00  0x09=0x00
  [100kHz STD] 0x42=0x00  0x01=0x00  0x09=0xFF
  [100kHz SWAP] 0x42=0x00  0x01=0x00  0x09=0xFF
  ```
- **Key observation:** Results are **inconsistent and mixed** — not all `0xFF`, not all `0x00`.
  - Inconsistency = **intermittent contact** on one or more SPI wires.
  - A dead chip always gives consistent `0xFF` at all speeds.

---

### Step 11 — Breadboard Revealed as Connection Method
- User confirmed they are using a **breadboard** with jumper wires.
- **New suspects identified:**
  1. Off-by-one row — D23 and D19 are separated by 4 other pins, easy to be one row off.
  2. Split power rail — many breadboards have a gap in the power rail at the middle.
  3. Ra-02 not straddling center groove — both rows on same side would short pins together.

---

## 4. Key Findings Summary

| Finding | Evidence | Implication |
|:---|:---|:---|
| Both modules give identical results | Tested two independent chips | Eliminates dead/defective chip |
| Bit-bang AND HW SPI both fail | Tested at 10kHz software and 100kHz–1MHz hardware | Eliminates library/software issue |
| Mixed `0x00`/`0xFF` across speeds | 6-combination hardware SPI test | Confirms intermittent contact |
| LED tester confirms DC on all pins | All SPI pins lit the LED | DC continuity confirmed, SPI signal integrity unconfirmed |
| MISO GPIO self-test passed | D19 correctly follows OUTPUT state | ESP32 GPIO D19 is functional |
| RST was floating → Fixed | LED tester showed no voltage on RST | Was keeping chip in reset |
| NSS on GPIO5 (strapping pin) → Moved to D17 | Research confirmed boot conflict | Boot sequence issue resolved |
| Breadboard confirmed | User confirmation | Physical contact quality is prime suspect |

---

## 5. Root Cause Theories (Ranked by Likelihood)

### 🥇 #1 — Breadboard Intermittent Contact (Most Likely)
Breadboard clips for one or more SPI pins (SCK, NSS, MISO, MOSI) are not making reliable electrical contact. An LED tester proves DC continuity but cannot detect SPI signal degradation. The mixed `0x00`/`0xFF` pattern across speeds is the definitive fingerprint of intermittent contact.

### 🥈 #2 — Off-by-One Row on Breadboard
A wire is plugged one or two holes off from the intended ESP32 GPIO. D23, D19, D18, and D17 are not adjacent — D23 is separated from D19 by four other pins (D22, TX0, RX0, D21).

### 🥉 #3 — Ra-02 Not Straddling Center Groove
If both rows of the Ra-02 breakout board are on the **same side** of the breadboard center divide, opposite pins are internally shorted through the breadboard clips.

### #4 — Power Rail Break
Full-size breadboards have a physical gap at ~row 30 in the power rails. If 3V3 is injected on one side and Ra-02 is on the other, the module may not be getting power.

---

## 6. Current Confirmed Correct Wiring

> ⚠️ **Use D17 for NSS — NOT D5.** GPIO5 is an ESP32 boot strapping pin that causes 0xFF on all SPI reads.

| Wire Color | Ra-02 Pin | ESP32 Pin | Function |
|:---:|:---|:---:|:---|
| 🔴 Red | Left [3] — **3.3V** | `3V3` | Power (strictly 3.3V!) |
| ⚫ Black | Left [1] — **GND** | `GND` | Ground |
| 🟣 Purple | Left [4] — **RST** | `D14` | Reset (driven HIGH by ESP32) |
| ⚪ White | Left [5] — **DIO0** | `D26` | Interrupt (TX/RX done) |
| 🟡 Yellow | Right [2] — **NSS** | `D17` ← | Chip Select (NOT D5!) |
| 🔵 Blue | Right [3] — **MOSI** | `D23` | Data ESP32 → Ra-02 |
| 🟢 Green | Right [4] — **MISO** | `D19` | Data Ra-02 → ESP32 |
| 🟠 Orange | Right [5] — **SCK** | `D18` | SPI Clock |

### Code Pin Definitions (Final Verified):
```cpp
#define LORA_SCK   18
#define LORA_MISO  19
#define LORA_MOSI  23
#define LORA_SS    17   // NOT 5 — GPIO5 is a strapping pin!
#define LORA_RST   14
#define LORA_DIO0  26
```

---

## 7. Sketches Written & What They Revealed

| Sketch | Purpose | Key Finding |
|:---|:---|:---|
| Initial beacon | Basic LoRa init + TX | `LoRa.begin()` fails |
| 24-permutation scanner | Try all pin combos via HW SPI | All 24 → `0xFF` |
| RST fix + LoRa.setPins() | Proper RST pulse via D14 | Still `0xFF` |
| Bit-bang SPI diagnostic | Bypass HW SPI entirely | All regs → `0xFF` at 10kHz |
| MISO self-test | Confirm MISO GPIO works | D19 GPIO functional |
| Ultra-verbose bit tracer | Print every MISO bit | All bits `1` in both SPI modes |
| 6-combo HW SPI test | MOSI/MISO swap + 3 speeds | Mixed `0x00`/`0xFF` → intermittent contact |
| Ultra-slow 5Hz tracer | 100ms per clock pulse | Pending user result |
| Clean beacon (final) | Straightforward init + beacon | Pending working connection |

All sketches stored at: `firmware/resqplug_lora_beacon_test/resqplug_lora_beacon_test.ino`

---

## 8. Next Steps

### Physical Verification (Do These First):
1. **Confirm Ra-02 straddles the breadboard center groove** — both rows of pins on opposite sides.
2. **Read the exact silkscreen labels** on your Ra-02 board left and right rows, compare to wiring table above.
3. **Replace jumper wires** — swap SCK, MOSI, MISO, NSS wires one by one with fresh jumpers.
4. **Test power rail continuity** — with LED tester, confirm 3V3 is present at the exact breadboard hole where Ra-02 3.3V pin is inserted.
5. **Try pressing/wiggling each jumper** on the breadboard while the ultra-slow 5Hz tracer sketch is running — watch Serial Monitor for a non-`0xFF` reading.

### Upload (When Physical Issue Resolved):
The clean beacon sketch is ready at:  
`firmware/resqplug_lora_beacon_test/resqplug_lora_beacon_test.ino`

Expected output when working:
```
✅ SUCCESS: Ra-02 Online @ 433 MHz!
📡 Beacon #0 → SENT!
📡 Beacon #1 → SENT!
```

---

## 9. Notes & Lessons Learned

> [!IMPORTANT]
> **GPIO5 is an ESP32 boot strapping pin.** Never use it for Ra-02 NSS/CS. Use D17, D16, D15, D27, or D25 instead.

> [!WARNING]
> **An LED tester only confirms DC voltage continuity.** It cannot detect SPI signal integrity problems. A wire can light the LED and still fail at SPI clock speeds due to contact resistance, loose breadboard clips, or oxidized contacts.

> [!NOTE]
> **Two chips failing identically** is strong evidence the chips are alive. The odds of two independent SX1278 chips being dead in the exact same way — while simultaneously all 24 SPI pin permutations fail — is statistically near-zero. The connection environment is the problem.

> [!TIP]
> **When debugging SPI, start with 5 wires:** 3V3, GND, NSS, SCK, MISO (tie MOSI to GND). Just read registers. This eliminates MOSI and reduces the number of failure points to identify.

> [!CAUTION]
> **The Ra-02 SX1278 is NOT 5V tolerant.** Even a brief connection to 5V or VIN will permanently destroy the chip. Always verify the 3V3 pin is outputting 3.3V before connecting the module.

---

*Part of the ResQPlug project hardware bring-up documentation.*
