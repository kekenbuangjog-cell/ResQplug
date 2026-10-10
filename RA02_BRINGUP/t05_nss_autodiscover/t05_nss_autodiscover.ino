/*
 * ==============================================================================
 *  ResQPlug RA02_BRINGUP - T5: NSS Auto-Discovery
 * ==============================================================================
 *  PURPOSE
 *    End the D5-vs-D17 argument permanently by MEASURING instead of guessing.
 *
 *    Your repo used to define NSS six different ways. All of them were
 *    reconciled to GPIO 17 on 2026-10-07, after T3/T6/T7 passed on D17:
 *      LORA_WIRING_AND_DIY_HOOKUP_GUIDE.md line 26  -> GPIO 5  -> 17  (fixed)
 *      LORA_WIRING_AND_DIY_HOOKUP_GUIDE.md line 45  -> GPIO 17         (always right)
 *      LORA_WIRING_AND_DIY_HOOKUP_GUIDE.md code    -> 5       -> 17  (fixed)
 *      HARDWARE_OTG_TROUBLESHOOTING.md      line 151 -> GPIO 5  -> 17  (fixed)
 *      resqplug_unified_node0.1 / 0.2                -> 5       -> 17  (fixed)
 *      LORA_RA02_ESP32_TROUBLESHOOTING_LOG.md        -> 17             (always right)
 *
 *    T5 is now REDUNDANT - it existed to settle the D5-vs-D17 argument by
 *    measurement, and T3 + T6 + T7 have all passed on D17.
 *    This sketch tries every plausible NSS GPIO in one run and prints which
 *    one makes the chip answer 0x12. No interpretation needed.
 *
 *  WHAT IT TESTS
 *    - Which physical ESP32 pin your yellow NSS wire is actually on
 *    - Whether the wire is on a pin that isn't being driven at all
 *
 *  IMPORTANT: only ONE candidate can be right, and the wire is physically
 *  plugged into one hole. The sketch drives every candidate HIGH by default,
 *  so no two are asserted at once during the idle phase.
 *
 *  PASS: prints "-> NSS GPIO nn RESPONDED with 0x12". Rewire to that pin
 *        (or confirm your wire is already there) and go to T6.
 *  FAIL: no candidate responds. NSS is not the problem - go back to T2 (RST)
 *        and T3, then check power at LEFT [3].
 *
 *  NOTE: GPIO5 and GPIO12 are boot strapping pins. Using them for NSS can
 *  stop the ESP32 booting if the module pulls them at the wrong level. If
 *  only GPIO5 works, that still means you should move NSS to a safe pin and
 *  find why the safe pins fail.
 * ==============================================================================
 */

#include <SPI.h>

// SYNC: ra02_pins.h
#define PIN_SCK        18
#define PIN_MISO       19
#define PIN_MOSI       23
#define PIN_RST        14
#define PIN_DIO0       26
#define PIN_BOARD_LED   2
#define PIN_EXT_LED     4
// PIN_NSS intentionally NOT defined here - we are discovering it.
// END SYNC

// Every candidate NSS pin worth testing, in priority order.
// 17, 16, 15, 27, 25 are all safe non-strapping outputs.
// 5 and 12 are strapping pins - tested last, flagged if they win.
const int CANDIDATES[] = { 17, 16, 15, 27, 25, 5, 12 };
const int CANDIDATE_COUNT = sizeof(CANDIDATES) / sizeof(CANDIDATES[0]);

uint8_t readWithNss(int nssPin, uint8_t addr) {
  digitalWrite(nssPin, LOW);
  delayMicroseconds(50);
  SPI.beginTransaction(SPISettings(250000, MSBFIRST, SPI_MODE0));
  SPI.transfer(addr & 0x7F);
  uint8_t v = SPI.transfer(0x00);
  SPI.endTransaction();
  digitalWrite(nssPin, HIGH);
  delayMicroseconds(50);
  return v;
}

void printHex8(uint8_t v) {
  if (v < 0x10) Serial.print("0");
  Serial.print(v, HEX);
}

bool isStrappingPin(int p) {
  return (p == 5 || p == 12 || p == 15 || p == 0 || p == 2);
}

void setup() {
  Serial.begin(115200);
  delay(2000);

  // Drive every candidate HIGH first so we never assert two NSS at once.
  for (int i = 0; i < CANDIDATE_COUNT; i++) {
    pinMode(CANDIDATES[i], OUTPUT);
    digitalWrite(CANDIDATES[i], HIGH);
  }
  pinMode(PIN_SCK,   OUTPUT); digitalWrite(PIN_SCK,   LOW);
  pinMode(PIN_MOSI,  OUTPUT); digitalWrite(PIN_MOSI,  LOW);
  pinMode(PIN_MISO,  INPUT);
  pinMode(PIN_RST,   OUTPUT); digitalWrite(PIN_RST,   HIGH);
  pinMode(PIN_DIO0,  INPUT);
  pinMode(PIN_BOARD_LED, OUTPUT); digitalWrite(PIN_BOARD_LED, LOW);
  pinMode(PIN_EXT_LED,   OUTPUT); digitalWrite(PIN_EXT_LED,   LOW);

  SPI.begin(PIN_SCK, PIN_MISO, PIN_MOSI, CANDIDATES[0]);

  Serial.println();
  Serial.println("=========================================================");
  Serial.println("  T5: NSS AUTO-DISCOVERY");
  Serial.println("=========================================================");
  Serial.println("  Fixed pins : SCK 18 | MISO 19 | MOSI 23 | RST 14");
  Serial.println("  Scanning   : 17, 16, 15, 27, 25, 5, 12");
  Serial.println("  Target     : register 0x42 == 0x12");
  Serial.println("=========================================================");
  Serial.println();

  Serial.println("[0] Reset pulse...");
  digitalWrite(PIN_RST, LOW);
  delay(20);
  digitalWrite(PIN_RST, HIGH);
  delay(200);
  Serial.println("    done.");
  Serial.println();

  Serial.println("[1] Scanning candidate NSS pins (3 reads each):");
  int found = -1;

  for (int i = 0; i < CANDIDATE_COUNT; i++) {
    int pin = CANDIDATES[i];
    uint8_t r1 = readWithNss(pin, 0x42);
    delay(30);
    uint8_t r2 = readWithNss(pin, 0x42);
    delay(30);
    uint8_t r3 = readWithNss(pin, 0x42);

    int matches = 0;
    if (r1 == 0x12) matches++;
    if (r2 == 0x12) matches++;
    if (r3 == 0x12) matches++;

    Serial.print("    GPIO ");
    if (pin < 10) Serial.print(" ");
    Serial.print(pin);
    Serial.print(" -> 0x");
    printHex8(r1);
    Serial.print(" 0x");
    printHex8(r2);
    Serial.print(" 0x");
    printHex8(r3);
    if (isStrappingPin(pin)) Serial.print("  (strapping pin)");
    if (matches == 3) {
      Serial.print("   <=== RESPONSIVE");
      if (found < 0) found = i;
    } else if (matches > 0) {
      Serial.print("   <-- partial");
    }
    Serial.println();

    delay(60);
  }

  Serial.println();

  // --- Also report what a NON-driven candidate looks like ---
  Serial.println("[2] Sanity check: all candidates HIGH (chip should be deselected):");
  {
    // Release NSS candidates we just pulsed, re-read MISO with nothing selected
    for (int i = 0; i < CANDIDATE_COUNT; i++) digitalWrite(CANDIDATES[i], HIGH);
    delay(50);
    Serial.print("    MISO = ");
    Serial.println(digitalRead(PIN_MISO) ? "HIGH (floating)" : "LOW");
  }
  Serial.println();

  // --- Verdict ---
  Serial.println("=========================================================");
  Serial.println("  VERDICT T5");
  Serial.println("=========================================================");

  if (found >= 0) {
    int pin = CANDIDATES[found];
    Serial.print("  [PASS] NSS GPIO ");
    Serial.print(pin);
    Serial.println(" RESPONDED with 0x12 (3/3 reads).");
    Serial.println();
    if (isStrappingPin(pin)) {
      Serial.println("  WARNING: that is a BOOT STRAPPING pin.");
      Serial.println("  The chip answers, but the ESP32 may fail to boot if the");
      Serial.println("  module holds it at the wrong level at power-up.");
      Serial.println("  ACTION: keep NSS there for now to prove the link, but");
      Serial.println("          treat moving to GPIO17 as required follow-up.");
    } else {
      Serial.println("  ACTION: this is your real NSS pin. Confirm the physical");
      Serial.println("          wire lands on that ESP32 hole, then update:");
      Serial.println("            - ra02_pins.h and every sketch's SYNC block");
      Serial.println("            - LORA_WIRING_AND_DIY_HOOKUP_GUIDE.md lines 26,115");
      Serial.println("            - HARDWARE_OTG_TROUBLESHOOTING.md line 151");
      Serial.println("            - resqplug_unified_node0.1/0.2 line 40");
    }
    Serial.println("  NEXT: T6 (LoRa library init).");
    digitalWrite(PIN_EXT_LED, HIGH);
  } else {
    Serial.println("  [FAIL] No candidate NSS pin made the chip answer.");
    Serial.println();
    Serial.println("  That means NSS is NOT your problem. Work down this list:");
    Serial.println("    1. T2 - is RST actually releasing? (meter on LEFT [4])");
    Serial.println("    2. Power - meter on LEFT [3] must read ~3.2V");
    Serial.println("    3. MISO wire - meter on RIGHT [4], wiggle it during T3");
    Serial.println("    4. Common ground - LEFT [1] and RIGHT [1] both to GND");
    Serial.println();
    Serial.println("  Two independent chips failing identically points at the");
    Serial.println("  connection environment, not dead silicon.");
  }
  Serial.println("=========================================================");
  Serial.println();
}

void loop() {
  digitalWrite(PIN_BOARD_LED, HIGH); delay(500);
  digitalWrite(PIN_BOARD_LED, LOW);  delay(500);
}
