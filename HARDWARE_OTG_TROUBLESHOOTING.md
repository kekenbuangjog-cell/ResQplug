# ResQPlug Hardware OTG Issue & Procurement Roadmap

## 1. Executive Summary

This document details the critical hardware barrier encountered with **USB Type-C to Type-C OTG (On-The-Go) Host negotiation** between modern, standards-compliant smartphones (specifically the **Nokia 5.4**) and budget 30-pin **ESP32 development boards** (ESP-WROOM-32), along with the complete procurement list, component specs, and wiring guide to solve the issue.

---

## 2. Problem Statement & Symptoms

### The Phenomenon
1. **Older Phones (Samsung / Oppo):**
   * Older or proprietary-skin Android phones detect the ESP32 USB-to-UART bridge (CP2102/CH340) and supply VBUS power via legacy OTG behavior.
2. **Modern Standards-Compliant Phones (Nokia 5.4 / Pixel / Android One):**
   * When connected via a direct Type-C to Type-C cable, the phone **fails to supply 5V VBUS power**, the ESP32 power LED remains OFF, and Android `UsbManager` never fires `ACTION_USB_DEVICE_ATTACHED`.
   * When using passive USB-A to Type-C adapters, the ESP32 red power LED intermittently flickers or turns on, but the phone drops or refuses to enumerate the USB serial device.

---

## 3. Root Cause Analysis

```
Phone (DFP / Host)                              ESP32 Dongle (UFP / Device)
+-------------------+                           +-------------------------+
|                   |                           |                         |
|   5V VBUS Switch  |-----[ VBUS Line (Open) ]->| ESP32 VIN (No Power!)   |
|                   |                           |                         |
|   CC Controller   |                           |                         |
|     (Source)      |                           |                         |
|        CC1 pin ---+-----[ Floating Cable ]--->| CC1 Pin (Unconnected)   |
|        CC2 pin ---+-----[ Floating Cable ]--->| CC2 Pin (Unconnected)   |
|                   |                           | (Missing 5.1kΩ to GND!) |
|       GND         |-------------------------->| GND                     |
+-------------------+                           +-------------------------+
  * Phone refuses to apply 5V VBUS because it detects no Rd (5.1kΩ) pull-down *
```

1. **USB Type-C Specification Compliance (USB-IF):**
   * In the USB-C standard, a Host (Downstream Facing Port / DFP) will **never output 5V on VBUS** until it detects a valid **5.1 kΩ pull-down resistor ($R_d$) on either CC1 or CC2 to Ground** on the attached accessory (Upstream Facing Port / UFP).
   * Unlike legacy Micro-USB (which relied on an ID pin shorted to ground), USB-C dynamically negotiates power delivery and data roles across the CC lines.
2. **Manufacturing Shortcut on Budget 30-Pin ESP32 Boards:**
   * Budget clone boards omit the two 5.1 kΩ resistors on the CC lines to reduce bill-of-materials and assembly cost, assuming users will only plug the board into a PC USB-A port via an A-to-C cable (where 5V is permanently hot).
3. **Inrush Current Tripping (LoRa + ESP32 Boot):**
   * The ESP32 draws a sudden ~150–250 mA current spike at boot, compounded by the RF decoupling capacitors and Ra-02 transceiver. Without an input decoupling buffer, this voltage dip can trip the phone's battery protection circuit and shut down the OTG port.
4. **Physical Limitation:**
   * The Type-C connector on standard 30-pin boards is enclosed in a surface-mounted metal shell. The CC lines are not broken out to the side header pins, making manual micro-soldering impractical without damaging the board.

---

## 4. Evaluation of Resolution Strategies

| Approach | Feasibility | Defense Viability | Engineering Verdict |
| :--- | :--- | :--- | :--- |
| **Approach A: Type-C Breakout Board (DIP) + 5.1kΩ Resistors + CP2102** | **Immediate (Parts in local shops today)** | High (Working bench prototype) | **Best immediate fix for rapid prototyping and testing.** |
| **Approach B: Upgrade to ESP32-S3 DevKit (Native USB CDC)** | High (Available locally or 1–2 days Shopee) | **Highest (100% USB-IF compliant, professional)** | **Gold Standard for Final Capstone Submission.** |
| **Approach C: Factory DAC/OTG Cable** | Low (Hit-or-miss online labeling) | Low (Fragile, loose pins) | Avoid for defense. |
| **Approach D: Micro-soldering SMD on existing board** | Very Low | Low (Prone to lifting pads) | Avoid. |

### Why Approach B (ESP32-S3) is the Long-Term Winner:
* Built-in hardware USB OTG/CDC engine directly on the silicon (`GPIO 19 D-` / `GPIO 20 D+`).
* Factory-designed Type-C port with integrated 5.1kΩ CC resistors.
* Directly enumerates as native USB CDC-ACM (`0x303A:0x1001`), completely eliminating external bridge ICs (CP2102/CH340).

---

## 5. Complete Procurement & Shopping List

### A. Electronic Components

| Item | Specification | Qty | Purpose |
| :--- | :--- | :--- | :--- |
| **ESP32-S3 DevKit** *(Recommended Target)* | **ESP32-S3-DevKitC-1** (N8R2 or N16R8), Dual Type-C Ports | 1–2 | Native USB OTG with compliant CC lines |
| **USB Type-C Female Breakout Board** | **16-Pin or 24-Pin DIP** (Must expose `CC1`, `CC2`, `VBUS`, `GND`) | 2–3 | Host detection breakout for current ESP32 |
| **Metal Film Resistors** | **5.1 kΩ, 1/4 Watt, 1% tolerance** (Through-Hole) | 10 pcs | CC1 & CC2 pull-down to GND ($R_d$) |
| **CP2102 USB-to-UART Module** | 6-Pin CP2102 (3.3V, 5V, TXD, RXD, GND, DTR) | 1–2 | Reliable USB serial bridge for Android |
| **Electrolytic Capacitors** | **100 µF or 220 µF (16V–25V)** Through-Hole | 5 pcs | Inrush buffer across 5V VBUS and GND |
| **Ceramic Capacitors** | **0.1 µF (104)** Through-Hole | 5 pcs | High-frequency noise filtering |
| **Solderless Breadboard** | MB-102 (830 tie-points) with dual power rails | 1 | Bench assembly and wiring |
| **Jumper Wire Ribbons** | Male-to-Male (20cm) & Male-to-Female (20cm) | 1 set each | Interconnections |
| **USB-C OTG Host Adapter** | Ugreen or Baseus Type-C Male to USB-A Female | 1 | Verified compliant host-mode adapter |

### B. Essential Tools

| Tool | Recommended Model / Type | Usage |
| :--- | :--- | :--- |
| **Digital Multimeter (DMM)** | Uni-T (UT33D+ / UT89X) or Aneng (AN8008) | Measuring 5V/3.3V rails, checking 5.1kΩ resistance, continuity beeper |
| **Precision Wire Stripper** | 20–30 AWG (Pro'sKit, Total, or Stanley) | Clean breadboard jumper trimming |
| **Short USB-A to Type-C Cable** | 0.25m to 0.5m high-speed data cable | Connection between OTG adapter and module |

### C. Search Keywords for Cebu Local Shops & Shopee/Lazada

* `ESP32-S3 DevKitC-1 N8R2 Type C`
* `Type C female breakout 16P DIP`
* `5.1k ohm resistor 1/4w`
* `CP2102 USB to TTL module 6 pin`
* `100uF 25V electrolytic capacitor`
* `Ugreen USB C OTG adapter male to female`
* `MB-102 breadboard 830`

> **Local Cebu Sourcing Notes:**
> * **AC/DC Radio & Electronics Supply** (Manalili / Legaspi St, Downtown Colon, Cebu City): Best for discrete resistors (5.1kΩ), capacitors, multimeters, wire strippers, and jumper wire.
> * **E-Hub / Cebu Tech / Circuitronix**: Check for ESP32-S3 boards and CP2102 modules.
> * **Shopee/Lazada Priority Sellers**: Makerlab Electronics, Circuit-Help, Cytron Philippines (fast delivery to Cebu).

---

## 6. Breadboard Wiring & Pinout Guide

### A. USB-C Host Breakout Circuit (Approach A)

```text
               +----------------------------------------+
               |         NOKIA 5.4 (USB-C HOST)         |
               +----------------------------------------+
                                   |
                     [ Type-C to Type-C Cable ]
                                   |
                                   v
             +--------------------------------------------+
             |    TYPE-C BREAKOUT BOARD (16-PIN FEMALE)   |
             +--------------------------------------------+
             |  [VBUS]      [CC1]     [CC2]      [GND]    |
             +----+-----------+---------+----------+------+
                  |           |         |          |
                  |        [5.1kΩ]   [5.1kΩ]       |
                  |           |         |          |
                  |           +----+----+          |
                  |                |               |
                  |                v               |
                  |             +------+           |
                  +------------>|      |<----------+ (Common GND)
                  |             | BREAD|
                  |  +---[100µF]---+   |
                  |  |  (Buffer)       |
                  v  v                 v
            +--------------+     +--------------+
            |  ESP32 VIN   |     |  ESP32 GND   |
            |  (5V Input)  |     +--------------+
            +--------------+
```

### B. Ai-Thinker Ra-02 (SX1278 433 MHz) Wiring Guide

> [!CAUTION]
> **VCC WARNING:** The Ai-Thinker Ra-02 is strictly a **3.3V logic and power device**. Connecting Ra-02 `VCC` to `5V VBUS` will **permanently burn out the SX1278 transceiver**! Always connect `VCC` to the ESP32 regulated **`3V3`** pin.

| Ra-02 Pin | Signal Type | ESP-WROOM-32 (30-Pin) | ESP32-S3 (Alternative) | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **VCC** | Power | **3V3** (3.3V Regulated) | **3V3** | **Strictly 3.3V!** |
| **GND** | Ground | **GND** | **GND** | Common ground plane |
| **NSS / CS**| SPI Chip Select | **GPIO 17** | **GPIO 10** | Radio slave select. ⚠️ Must **not** be GPIO 5 — that is an ESP32 boot strapping pin. |
| **MOSI** | SPI Master Out | **GPIO 23** | **GPIO 11** | SPI Data Input to Ra-02 |
| **MISO** | SPI Master In | **GPIO 19** | **GPIO 13** | SPI Data Output from Ra-02 |
| **SCK** | SPI Clock | **GPIO 18** | **GPIO 12** | Bus clock line |
| **DIO0** | Hardware IRQ | **GPIO 26** | **GPIO 4** | RxDone / TxDone interrupt signal. ⚠️ Must **not** be GPIO 2 — strapping pin + onboard LED. |
| **RST** | Reset | **GPIO 14** | **GPIO 5** | Active-low hardware reset |

---

## 7. Pre-Flight Verification Checklist

Before plugging the assembled breadboard into your Nokia 5.4:

1. **Unpowered Resistance Check (Multimeter set to $\Omega$):**
   * Probe between `CC1` and `GND` $\rightarrow$ Must read **$5.1\text{ k}\Omega \pm 5\%$**.
   * Probe between `CC2` and `GND` $\rightarrow$ Must read **$5.1\text{ k}\Omega \pm 5\%$**.
2. **Short-Circuit Check (Multimeter set to Continuity $\approx$):**
   * Probe between `VBUS` (5V) and `GND` $\rightarrow$ Must NOT beep. There must be no direct short.
3. **Live Voltage Check:**
   * Plug Nokia 5.4 into the Type-C breakout.
   * Measure voltage between `VBUS` rail and `GND` rail $\rightarrow$ Must read **$+4.85\text{V}$ to $+5.15\text{V}$**.
   * Measure voltage on the ESP32 `3V3` pin $\rightarrow$ Must read **$+3.25\text{V}$ to $+3.35\text{V}$**.
4. **Logcat / App Detection:**
   * Open the ResQPlug Android application.
   * Status should immediately shift to `[ ⚡ ESP32 HARDWARE LINKED ]` with your unique `NODE ID` displayed.

---

## 8. Existing Fallback Solution in Codebase

While resolving the physical OTG connection, the project includes two fully operational fallback solutions:

1. **Test Override Code (`42069`)**:
   * Click `[ ⌨️ TEST OVERRIDE CODE ]` on the splash screen to bypass physical USB checks and test the full mesh simulation UI, triage, and incident dispatching.
2. **Bluetooth SPP Wireless Pod (`BluetoothRadioHelper.kt` + `resqplug_bluetooth_radio.ino`)**:
   * Runs the ESP32 as an autonomous battery-powered radio pod communicating wirelessly with the Nokia 5.4 via classic Bluetooth Serial, bypassing OTG power limits entirely.
