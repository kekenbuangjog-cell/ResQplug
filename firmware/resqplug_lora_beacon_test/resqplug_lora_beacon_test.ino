/*
 * ==============================================================================
 *  ResQPlug: Ultra-Slow Bit-by-Bit MISO Tracer (100ms per clock)
 * ==============================================================================
 *  If the previous tests gave mixed 0x00/0xFF, one wire has intermittent
 *  contact. At 100ms per clock, even a poor wire-wrap will make contact.
 *
 *  This sketch:
 *    1. Verifies pin OUTPUT states by reading them back
 *    2. Holds NSS LOW for 500ms before clocking
 *    3. Clocks address byte (0x42) at 100ms per bit — prints each MOSI bit
 *    4. Clocks data byte — prints MISO at every single clock edge
 *
 *  RST→D14 | NSS→D17 | SCK→D18 | MOSI→D23 | MISO→D19
 * ==============================================================================
 */

#define PIN_NSS    17
#define PIN_SCK    18
#define PIN_MOSI   23
#define PIN_MISO   19
#define PIN_RST    14
#define EXT_LED    4
#define BOARD_LED  2

#define BIT_DELAY_MS  100   // 100ms per half-clock = 5Hz SPI

void setup() {
  Serial.begin(115200);
  delay(2000);

  // --- Configure all pins ---
  pinMode(PIN_NSS,   OUTPUT); digitalWrite(PIN_NSS,   HIGH);
  pinMode(PIN_SCK,   OUTPUT); digitalWrite(PIN_SCK,   LOW);
  pinMode(PIN_MOSI,  OUTPUT); digitalWrite(PIN_MOSI,  LOW);
  pinMode(PIN_MISO,  INPUT);
  pinMode(PIN_RST,   OUTPUT); digitalWrite(PIN_RST,   HIGH);
  pinMode(EXT_LED,   OUTPUT); digitalWrite(EXT_LED,   LOW);
  pinMode(BOARD_LED, OUTPUT); digitalWrite(BOARD_LED, LOW);

  Serial.println("\n\n===========================================================");
  Serial.println("  🐢 ResQPlug: Ultra-Slow 5Hz SPI Tracer (100ms/bit)");
  Serial.println("===========================================================\n");

  // --- Step 1: Verify pin states ---
  Serial.println("[STEP 1] Verifying pin readback:");
  Serial.print("  NSS  (D17) HIGH? → "); Serial.println(digitalRead(PIN_NSS)  ? "✅ HIGH" : "❌ LOW (wiring issue!)");
  Serial.print("  SCK  (D18) LOW?  → "); Serial.println(digitalRead(PIN_SCK)  ? "❌ HIGH (wiring issue!)" : "✅ LOW");
  Serial.print("  RST  (D14) HIGH? → "); Serial.println(digitalRead(PIN_RST)  ? "✅ HIGH" : "❌ LOW (RST stuck LOW = chip in reset!)");
  Serial.print("  MISO (D19) idle  → "); Serial.println(digitalRead(PIN_MISO) ? "HIGH (floating)" : "LOW (chip driving)");

  // --- Step 2: RST pulse ---
  Serial.println("\n[STEP 2] RST pulse (LOW 20ms → HIGH → wait 200ms)...");
  digitalWrite(PIN_RST, LOW);
  delay(20);
  digitalWrite(PIN_RST, HIGH);
  delay(200);
  Serial.print("  MISO after RST   → "); Serial.println(digitalRead(PIN_MISO) ? "HIGH" : "LOW");

  // --- Step 3: Assert NSS LOW, wait, check MISO ---
  Serial.println("\n[STEP 3] Asserting NSS LOW (selecting chip)...");
  digitalWrite(PIN_NSS, LOW);
  delay(500);  // Long wait — even bad wire-wrap will settle
  Serial.print("  MISO with NSS=LOW → "); Serial.println(digitalRead(PIN_MISO) ? "HIGH" : "LOW");

  // --- Step 4: Send address byte 0x42 (read reg) bit by bit ---
  Serial.println("\n[STEP 4] Clocking address 0x42 (read) — 100ms per bit:");
  uint8_t addr = 0x42 & 0x7F; // 0x42 read = 0x42 (MSB already 0)
  Serial.print("  Sending: 0b");
  Serial.println(addr, BIN);

  for (int i = 7; i >= 0; i--) {
    int bitVal = (addr >> i) & 1;
    digitalWrite(PIN_MOSI, bitVal);
    Serial.print("    MOSI="); Serial.print(bitVal);
    digitalWrite(PIN_SCK, HIGH);
    delay(BIT_DELAY_MS);
    Serial.print(" SCK↑ MISO="); Serial.println(digitalRead(PIN_MISO));
    digitalWrite(PIN_SCK, LOW);
    delay(BIT_DELAY_MS);
  }

  // --- Step 5: Clock in 8 data bits from chip ---
  Serial.println("\n[STEP 5] Clocking in data byte (MISO bits from chip):");
  digitalWrite(PIN_MOSI, LOW);
  uint8_t rxByte = 0;
  Serial.print("  RX bits: ");
  for (int i = 7; i >= 0; i--) {
    digitalWrite(PIN_SCK, HIGH);
    delay(BIT_DELAY_MS);
    int bit = digitalRead(PIN_MISO);
    rxByte = (rxByte << 1) | bit;
    Serial.print(bit);
    digitalWrite(PIN_SCK, LOW);
    delay(BIT_DELAY_MS);
  }
  Serial.println();

  // Deassert NSS
  digitalWrite(PIN_NSS, HIGH);

  // --- Result ---
  Serial.print("\n[RESULT] Reg 0x42 = 0x");
  if (rxByte < 0x10) Serial.print("0");
  Serial.println(rxByte, HEX);

  if (rxByte == 0x12) {
    Serial.println("  ✅ PERFECT! SX1278 chip alive and responding!");
    for (int i = 0; i < 5; i++) {
      digitalWrite(EXT_LED, HIGH); delay(200);
      digitalWrite(EXT_LED, LOW);  delay(200);
    }
  } else if (rxByte == 0xFF) {
    Serial.println("  ❌ 0xFF → MISO is floating. Wire not contacting Ra-02 MISO pin.");
    Serial.println("     → Press/squeeze the MISO wire at Ra-02 end and reset board.");
  } else if (rxByte == 0x00) {
    Serial.println("  ⚠️ 0x00 → MISO held LOW. Chip selected but not shifting.");
    Serial.println("     → SCK may not be reaching Ra-02, or chip is in bad state.");
    Serial.println("     → Press/squeeze the SCK wire at Ra-02 end and reset board.");
  } else {
    Serial.println("  🟡 Non-trivial value! Chip IS responding — timing or mode issue.");
  }

  Serial.println("\n===========================================================");
  Serial.println("  Trace complete.");
  Serial.println("===========================================================\n");
}

void loop() {
  digitalWrite(BOARD_LED, HIGH); delay(1000);
  digitalWrite(BOARD_LED, LOW);  delay(1000);
}
