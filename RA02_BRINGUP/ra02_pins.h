// ==============================================================================
//  ResQPlug - Ra-02 (SX1278) Pin Definitions - CANONICAL SOURCE
// ==============================================================================
//  Every sketch in RA02_BRINGUP/ carries a COPY of this block marked with
//  "// SYNC: ra02_pins.h". If you change a pin here, change it there too.
//
//  Physical wiring (see LORA_WIRING_AND_DIY_HOOKUP_GUIDE.md):
//
//          LEFT HEADER              RIGHT HEADER
//        ┌────────────┐          ┌────────────┐
//   row1 │ GND        │          │ GND        │ (skip)
//   row2 │ GND  (skip)│          │ NSS  ──────┼──> PIN_NSS
//   row3 │ 3.3V ──────┼──> 3V3   │ MOSI ──────┼──> PIN_MOSI
//   row4 │ RST  ──────┼──> PIN_RST  │ MISO ────┼──> PIN_MISO
//   row5 │ DIO0 ──────┼──> PIN_DIO0 │ SCK  ────┼──> PIN_SCK
//   row6 │ DIO1       │          │ DIO5       │
//   row7 │ DIO2       │          │ DIO4       │
//   row8 │ DIO3       │          │ GND        │
//        └────────────┘          └────────────┘
// ==============================================================================

#ifndef RA02_PINS_H
#define RA02_PINS_H

// --- SPI bus ---
#define PIN_SCK    18   // Right [5]  ESP32 GPIO18
#define PIN_MISO   19   // Right [4]  ESP32 GPIO19
#define PIN_MOSI   23   // Right [3]  ESP32 GPIO23
#define PIN_NSS    17   // Right [2]  ESP32 GPIO17  (NOT 5 - GPIO5 is a boot strapping pin)

// --- Control ---
#define PIN_RST    14   // Left  [4]  ESP32 GPIO14
#define PIN_DIO0   26   // Left  [5]  ESP32 GPIO26

// --- Indicators (safe, unrelated to LoRa) ---
#define PIN_BOARD_LED   2   // Onboard blue LED on most ESP32 DevKits
#define PIN_EXT_LED     4   // External LED via 220 ohm resistor

// --- Radio ---
#define LORA_FREQUENCY   433E6   // Philippine ISM band. Ra-02 is a 433 MHz module.

#endif // RA02_PINS_H
