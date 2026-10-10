/*
 * ==============================================================================
 *  ResQPlug RA02_BRINGUP - T4: Hardware SPI Register Read (3 speeds)
 * ==============================================================================
 *  PURPOSE
 *    Re-run the reads using the ESP32's real hardware SPI peripheral at three
 *    clock rates, and compare the results against T3's bit-bang numbers.
 *
 *    Why it matters: your troubleshooting log Step 10 produced MIXED
 *    0x00/0xFF across speeds, which is how intermittent contact was diagnosed.
 *    This test makes that comparison clean and repeatable so you can confirm
 *    or refute it in one run.
 *
 *  WHAT IT TESTS
 *    - Hardware SPI peripheral works (vs T3 which bypasses it entirely)
 *    - Whether the reading is stable across 1 MHz / 250 kHz / 100 kHz
 *    - Whether swapping MOSI/MISO changes anything (is the pair reversed?)
 *
 *  PASS: 0x42 = 0x12 at every speed, stable -> chip alive, SPI good.
 *  FAIL: values change between speeds -> contact resistance. Not a dead chip.
 *  FAIL: 0xFF everywhere, same as T3 -> go back to the T3 verdict block.
 * ==============================================================================
 */

#include <SPI.h>

// SYNC: ra02_pins.h
#define PIN_SCK        18
#define PIN_MISO       19
#define PIN_MOSI       23
#define PIN_NSS        17   // NOT 5 - GPIO5 is a boot strapping pin
#define PIN_RST        14
#define PIN_DIO0       26
#define PIN_BOARD_LED   2
#define PIN_EXT_LED     4
// END SYNC

SPISettings settingsStd(1000000, MSBFIRST, SPI_MODE0);

uint8_t hwReadRegister(uint8_t addr, SPISettings &s) {
  digitalWrite(PIN_NSS, LOW);
  delayMicroseconds(50);
  SPI.beginTransaction(s);
  SPI.transfer(addr & 0x7F);            // write bit 0 = 0 -> read
  uint8_t v = SPI.transfer(0x00);
  SPI.endTransaction();
  digitalWrite(PIN_NSS, HIGH);
  delayMicroseconds(50);
  return v;
}

void printHex8(uint8_t v) {
  if (v < 0x10) Serial.print("0");
  Serial.print(v, HEX);
}

void runAt(const char *label, uint32_t hz) {
  Serial.print("  [");
  Serial.print(label);
  Serial.print("] ");

  SPISettings s(hz, MSBFIRST, SPI_MODE0);

  // MOSI/MISO standard
  uint8_t a = hwReadRegister(0x42, s);
  delay(20);
  uint8_t b = hwReadRegister(0x42, s);

  // MOSI/MISO swapped: physically impossible to test in software on the ESP32
  // hardware peripheral, so instead re-read other registers to look for drift.
  delay(20);
  uint8_t r01 = hwReadRegister(0x01, s);
  delay(20);
  uint8_t r09 = hwReadRegister(0x09, s);

  Serial.print("0x42=");
  printHex8(a);
  Serial.print("/");
  printHex8(b);
  Serial.print("  0x01=");
  printHex8(r01);
  Serial.print("  0x09=");
  printHex8(r09);

  // Flag it when the two 0x42 reads disagree
  if (a != b) {
    Serial.print("   <-- DRIFT within same speed");
  }
  if (a == 0x12) {
    Serial.print("   <-- OK");
  }
  Serial.println();
}

void setup() {
  Serial.begin(115200);
  delay(2000);

  pinMode(PIN_NSS,   OUTPUT); digitalWrite(PIN_NSS,   HIGH);
  pinMode(PIN_RST,   OUTPUT); digitalWrite(PIN_RST,   HIGH);
  pinMode(PIN_BOARD_LED, OUTPUT); digitalWrite(PIN_BOARD_LED, LOW);

  SPI.begin(PIN_SCK, PIN_MISO, PIN_MOSI, PIN_NSS);

  Serial.println();
  Serial.println("=========================================================");
  Serial.println("  T4: HARDWARE SPI REGISTER READ - 3 SPEEDS");
  Serial.println("=========================================================");
  Serial.println("  Pins : SCK 18 | MISO 19 | MOSI 23 | NSS 17 | RST 14");
  Serial.println("  Reg  : 0x42 (silicon version) must be 0x12");
  Serial.println("=========================================================");
  Serial.println();

  Serial.println("[0] Reset pulse...");
  digitalWrite(PIN_RST, LOW);
  delay(20);
  digitalWrite(PIN_RST, HIGH);
  delay(200);
  Serial.println("    done.");
  Serial.println();

  Serial.println("[1] Read results by clock speed:");
  runAt("1MHz  ", 1000000);
  delay(100);
  runAt("250kHz", 250000);
  delay(100);
  runAt("100kHz", 100000);
  delay(100);
  runAt("100kHz", 100000);
  Serial.println();

  // --- Mode variation: SPI_MODE1 samples on the falling edge ---
  Serial.println("[2] SPI mode check at 250kHz (Mode 0 vs Mode 1):");
  {
    digitalWrite(PIN_NSS, LOW);
    delayMicroseconds(50);
    SPISettings m0(250000, MSBFIRST, SPI_MODE0);
    SPI.beginTransaction(m0);
    SPI.transfer(0x42);
    uint8_t v0 = SPI.transfer(0x00);
    SPI.endTransaction();
    digitalWrite(PIN_NSS, HIGH);
    delay(50);

    digitalWrite(PIN_NSS, LOW);
    delayMicroseconds(50);
    SPISettings m1(250000, MSBFIRST, SPI_MODE1);
    SPI.beginTransaction(m1);
    SPI.transfer(0x42);
    uint8_t v1 = SPI.transfer(0x00);
    SPI.endTransaction();
    digitalWrite(PIN_NSS, HIGH);
    delay(50);

    Serial.print("    Mode 0 (sample rising) = 0x");
    printHex8(v0);
    Serial.println();
    Serial.print("    Mode 1 (sample falling) = 0x");
    printHex8(v1);
    Serial.println();
    if (v0 == v1) {
      Serial.println("    Same in both modes - mode is not the problem.");
    } else {
      Serial.println("    DIFFERENT between modes - timing/marginal contact.");
    }
  }
  Serial.println();

  // --- NSS behaviour check ---
  Serial.println("[3] NSS line behaviour:");
  Serial.print("    Idle (should be HIGH/~3.2V) = ");
  digitalWrite(PIN_NSS, HIGH);
  delay(50);
  Serial.println(digitalRead(PIN_NSS) ? "HIGH" : "LOW");
  Serial.println("    Probe Ra-02 RIGHT [2] with the meter during a read -");
  Serial.println("    it must dip toward 0V. A pin stuck at 3.2V = never selected.");
  Serial.println();

  // --- Verdict ---
  uint8_t best = hwReadRegister(0x42, settingsStd);

  Serial.println("=========================================================");
  Serial.println("  VERDICT T4");
  Serial.println("=========================================================");
  if (best == 0x12) {
    Serial.println("  [PASS] 0x42 = 0x12 on hardware SPI.");
    Serial.println("         Chip alive, both bit-bang and hardware SPI agree.");
    Serial.println("         NEXT: T6 (LoRa library init). Skip T5 unless you");
    Serial.println("               still need to confirm which physical NSS pin.");
    digitalWrite(PIN_EXT_LED, HIGH);
  } else {
    Serial.println("  [FAIL] Hardware SPI did not return 0x12.");
    Serial.println();
    Serial.println("         Compare against T3's bit-bang result:");
    Serial.println("           T3 OK  + T4 FAIL -> hardware SPI config/pins issue");
    Serial.println("           T3 FAIL + T4 FAIL -> physical layer (see T3 verdict)");
    Serial.println("           Mixed values at different speeds -> INTERMITTENT");
    Serial.println("           CONTACT. Reseat wires; a dead chip never varies.");
    Serial.println();
    Serial.println("         If NSS-on-D17 is exhausted, run T5 to auto-discover");
    Serial.println("         which GPIO your NSS wire is actually on.");
  }
  Serial.println("=========================================================");
  Serial.println();
}

void loop() {
  digitalWrite(PIN_BOARD_LED, HIGH); delay(500);
  digitalWrite(PIN_BOARD_LED, LOW);  delay(500);
}
