# Plan — Ra-02 #2 (Node 2) bring-up

Companion to [`../RA02_BRINGUP/PLAN.md`](../RA02_BRINGUP/PLAN.md).

## Status

| Part | Stage | Status |
|---|---|---|
| 0 | Test E — pin → pad | ✅ **DONE 2026-10-10** — `447 755 742×4`, GND `000` |
| 1 | T3 — bare SPI gate | ✅ **PASS 2026-10-10** — `0x12`, 5/5, 7/7 rows |
| 1 | T6 — `LoRa.begin` | ✅ **PASS 2026-10-10** — `ok.` (via T8 boot) |
| 1 | T7 — beacon TX | ✅ **PASS 2026-10-10** — SENT, 0 timeouts (via T8) |
| 2 | T8 — two-node link | ✅ **PASS 2026-10-10** — both directions, 6/6, 0 drops |
| 3 | `unified_node0.2` — U1 beacons | ✅ **PASS 2026-10-10** — `ONLINE @ 433.00 MHz`, `[BCN:POD-C8E720…]` at `-67 dBm` |
| 3 | `unified_node0.2` — U2 phone E2E | ✅ **PASS 2026-10-10** — 2/2 `[MSG:…]` arrived, relay fired live |

**Bring-up complete. All gates closed.**

### T8 result

| | Node 2 (COM3) received | Node 1 (COM4) received |
|---|---|---|
| Heard | `RA02-UNIT1` | `RA02-UNIT2` |
| RSSI | `-66 / -66 / -67 dBm` | `-66 / -66 / -66 dBm` |
| SNR | `9.5 / 9.5 / 9.8 dB` | `9.8 / 9.5 / 9.5 dB` |
| Packets | **3/3, 0 drops** | **3/3, 0 drops** |

Identifiers: Node 1 = COM4, MAC `20:e7:c8:a7:a0:c0`, dongle `POD-C8E720`,
BT `ResQPlug-POD-C8E720`. Node 2 = COM3, MAC `6c:c8:40:05:7b:20`, dongle
`POD-40C86C`, BT `ResQPlug-POD-40C86C`. COM5 is not an ESP32 and is not used.

### U1 result — production firmware, both boards

```
[LORA RADIO  ]: ONLINE @ 433.00 MHz (+17 dBm PA)
[RX:RSSI:-67|SNR:9.8|DATA:[BCN:POD-C8E720|ResQPlug-POD-C8E720|CITIZEN|1]]
[BEACON_TX]: [BCN:POD-40C86C|ResQPlug-POD-40C86C|CITIZEN|1]
```

Node 1's *production* beacons arriving at Node 2 over real RF. Both boards run
the four `unified_node0.2` fixes (real TX queue, external LED init,
non-blocking mesh relay, `RELAY_SKIP` logging).

### U2 result — phone → radio → Node 2

```
[RX:RSSI:-66|SNR:9.8|DATA:[MSG:msg_1791614503565_30F0|RQP-NODE-2330F0|bluetoothtest|STATUS|1|hello]]
[RELAY_TX]: [MSG:msg_1791614503565_30F0|...|STATUS|2|hello]

[RX:RSSI:-69|SNR:9.8|DATA:[MSG:msg_1791614554871_30F0|RQP-NODE-2330F0|bluetoothtest|STATUS|1|hello node 2 I am node 1]]
[RELAY_TX]: [MSG:msg_1791614554871_30F0|...|STATUS|2|hello node 2 I am node 1]
```

```
phone ──BT──▶ Node 1 ──433 MHz──▶ Node 2        RSSI -66/-69 dBm  SNR +9.8
```

2/2 messages arrived. Mesh relay fired live (hop 1→2). No phone was paired to
Node 2 — its output was read from USB serial on the PC, because the firmware
mirrors every received packet to both interfaces.

> ⚠ USB-OTG remains unimplemented in the APK. Bluetooth is the only real
> transport.

## The gate logic

```
Test E (done)
  │  all six pins connect? ── no ──> solder the pins, redo Test E
  ▼
T3   Reg 0x42 = 0x12?  ── no ──> FALLBACK LADDER (T1→T2→T4→T5, meter 1-3/A/D)
  ▼                                  the ladder that found Ra-02 #1's fault
T6   LoRa.begin() true? ── no ──> back to T3
  ▼
T7   5/5 beacons SENT? ── no ──> antenna, then 3.3 V under PA load
  ▼
T8 ✅ packet arrives from the other node?
  ▼
U1 ✅ unified_node0.2 receives a [BCN:…] beacon
  ▼
U2 ✅ a [MSG:…] sent from the phone arrives
  ▼
DONE — record it, update both READMEs
```

## Steps

### Part 0 — meter gate ✅ complete

Test E run 2026-10-10 with the module standalone: red probe on the U.FL shell,
black on each header pin.

| Pin | Result |
|---|---|
| 3.3V | `447` ✅ |
| RESET | `755` ✅ |
| NSS | `742` ✅ |
| MOSI | `742` ✅ |
| MISO | `742` ✅ |
| SCK | `742` ✅ |
| GND ×4 | `000` ✅ |

6/6 connected. No `1`, no `000` on a signal pin.

### Part 1 — acceptance ladder

1. **Wiring check before anything else.** One conductor per breadboard hole on
   MOSI D23 · MISO D19 · SCK D18 · NSS D17 · 3V3 · GND. Module level, all 16
   pins seated, antenna on. This is the fault that kept Ra-02 #1 failing even
   after it was soldered — a meter cannot see it.
2. **T3** → `Reg 0x42 = 0x12`.
3. **T6** → `Result : true`.
4. **T7** → 5/5 SENT. ⚠ antenna.

### Part 2 — integration

5. **T8** on both boards. Set a unique `NODE_ID` per board, flash the same file
   to both, open both serial monitors. Wait for an `[RX] ... FROM ANOTHER NODE`
   line on each.

### Part 3 — production firmware

6. Flash `firmware/resqplug_unified_node0.2/` to Node 2. Confirm the boot
   banner (`0x12` + `ONLINE @ 433.00 MHz`).
7. Leave its serial open ~28 s. Node 1 broadcasts `[BCN:POD-C8E720|...]` every
   ~25 s. Its arrival on Node 2 is the full chain.
8. With the phone connected to Node 1, send a message in the app. Node 2 should
   print `[RX:…|DATA:[MSG:…]]`.

### Part 4 — record

9. Fill in `TEST_LIST.md` PART 4.
10. Update this file's status table and the README banner.
11. Update [`../RA02_BRINGUP/README.md`](../RA02_BRINGUP/README.md) to note both
    nodes are now proven, and add the link-test result to kit #1's record —
    because T8 is a two-node test and belongs to both kits.

## Constraints

- **Node 1 is COM3.** Node 2 appears on another COM port when plugged in.
  Never flash one while the other's monitor holds the port.
- **Only one board on USB at a time while flashing** — easier to reason about
  which board you just touched.
- **Antenna before any transmit.** T7, T8 and every beacon from
  `unified_node0.2` go out over the air.
- **Stop at the first FAIL.** Later stages produce misleading output if an
  upstream fault is present.

## What is deliberately out of scope

| | Why |
|---|---|
| Re-running T1/T2/T4/T5 | diagnostic only — they exist for when T3 fails, and are still here if it does |
| Re-running meter Tests 1–3, A, D | they diagnosed power/orientation on Ra-02 #1; nothing suggests the same fault here |
| App-side USB-OTG send path | Bluetooth is the proof path for this phase |
| Firebase / cloud | the app already handles it; not part of radio bring-up |
| DIO0 interrupt | polling works; adding it now would confound the link test |
