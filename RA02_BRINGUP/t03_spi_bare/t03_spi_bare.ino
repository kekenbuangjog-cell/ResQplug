/*
 * ==============================================================================
 *  ResQPlug RA02_BRINGUP - T3: Bare-Minimum Bit-Bang SPI Read
 * ==============================================================================
 *  PURPOSE
 *    Read register 0x42 ( silicon version ) with the FEWEST possible failure
 *    points. This test deliberately:
 *
 *      - uses raw digitalWrite()/digitalRead() instead of any SPI library,
 *        so no library, driver, or hardware-SPI config can be blamed
 *      - runs at ~200 Hz (2.5 ms per bit) so a poor wire-wrap still makes
 *        contact on every clock
 *      - can tie MOSI LOW to remove it as a variable (see MOSI_LOW below).
 *        With MOSI_LOW 1 the command byte becomes 0x00 = read of register
 *        0x00 (FIFO) instead of 0x42, so the chip still drives MISO with a
 *        real value, but 0x12 is no longer the expected answer.
 *        DEFAULT IS 0 (real 0x42 reads) — flip to 1 only when you want to
 *        rule MOSI out as the fault.
 *
 *  WHY 0x42 MATTERS
 *    The SX1278 silicon version register (0x42) must return 0x12 for the
 *    sandeepmistry LoRa library to accept the module. Anything else and
 *    LoRa.begin() fails.
 *
 *  DT-830B CHECK WHILE THIS RUNS (DCV 20)
 *    Ra-02 RIGHT [2] NSS : should sit at ~3.2V idle, dip to ~0V during a read
 *    Ra-02 RIGHT [5] SCK : should wiggle (meter shows a mid/low value)
 *
 *  PASS: prints 0x12 -> chip is ALIVE and talking. Go to T6.
 *  FAIL: prints 0xFF -> nobody driving MISO. See verdict block.
 *  FAIL: prints 0x00 -> chip selected but not shifting. See verdict block.
 * ==============================================================================
 */

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

#define BIT_DELAY_US  20000  // 20ms per half-clock -> ~16Hz SPI.
                             // Deliberately SLOW so a DT-830B can see it:
                             // NSS stays low ~1s per read, SCK/MOSI/MISO wiggle
                             // at a rate the meter can sample (2-3/sec).
                             // Also acts as a test: if slow SPI fixes the values,
                             // the fault is timing; if not, it is wiring.

// Drive MOSI to a fixed level for the whole transaction.
// MOSI_LOW = 0  -> MOSI driven with the real command byte (DEFAULT, reads 0x42)
// MOSI_LOW = 1  -> MOSI held LOW, MOSI removed as a variable (reads reg 0x00)
#define MOSI_LOW  0

void spiDelay() { delayMicroseconds(BIT_DELAY_US); }

uint8_t spiTransferBit(int outBit) {
  digitalWrite(PIN_MOSI, outBit ? HIGH : LOW);
  spiDelay();
  digitalWrite(PIN_SCK, HIGH);      // Mode 0: sample on rising edge
  spiDelay();
  int inBit = digitalRead(PIN_MISO);
  digitalWrite(PIN_SCK, LOW);
  spiDelay();
  return inBit ? 1 : 0;
}

// Read one register: NSS low, send 0x80|addr, clock 8 data bits, NSS high
uint8_t readRegister(uint8_t addr) {
  digitalWrite(PIN_NSS, LOW);
  delayMicroseconds(100);

  uint8_t cmd = (addr & 0x7F) | 0x80;   // MSB set = read

  uint8_t in = 0;
  for (int i = 7; i >= 0; i--) {
    int outBit = MOSI_LOW ? 0 : ((cmd >> i) & 1);
    in = (in << 1) | spiTransferBit(outBit);
  }
  for (int i = 7; i >= 0; i--) {
    (void)i;   // clock only — MOSI carries no data in this phase
    in = (in << 1) | spiTransferBit(0);
  }

  digitalWrite(PIN_NSS, HIGH);
  delayMicroseconds(100);
  return in;
}

void printHex8(uint8_t v) {
  if (v < 0x10) Serial.print("0");
  Serial.print(v, HEX);
}

void setup() {
  Serial.begin(115200);
  delay(2000);

  pinMode(PIN_NSS,   OUTPUT); digitalWrite(PIN_NSS,   HIGH);
  pinMode(PIN_SCK,   OUTPUT); digitalWrite(PIN_SCK,   LOW);
  pinMode(PIN_MOSI,  OUTPUT); digitalWrite(PIN_MOSI,  LOW);
  pinMode(PIN_MISO,  INPUT);
  pinMode(PIN_RST,   OUTPUT); digitalWrite(PIN_RST,   HIGH);
  pinMode(PIN_DIO0,  INPUT);
  pinMode(PIN_BOARD_LED, OUTPUT); digitalWrite(PIN_BOARD_LED, LOW);

  Serial.println();
  Serial.println("=========================================================");
  Serial.println("  T3: BARE-MINIMUM BIT-BANG SPI READ");
  Serial.println("=========================================================");
  Serial.println("  Method : raw GPIO, no libraries, ~16 Hz (meter-visible)");
  if (MOSI_LOW) {
    Serial.println("  MOSI   : held LOW (removed as a variable)");
  } else {
    Serial.println("  MOSI   : real command byte (set MOSI_LOW 1 to isolate it)");
  }
  Serial.println("  NSS    : D17   RST : D14   SCK : D18   MISO : D19");
  Serial.println("  Target : register 0x42 must return 0x12");
  Serial.println("=========================================================");
  Serial.println();
  Serial.println("  METER THIS RUN (DCV 20, black on GND, probe Ra-02 right side):");
  Serial.println("    RIGHT [2] NSS  -> must DIP to ~0V during each read");
  Serial.println("    RIGHT [5] SCK  -> must WIGGLE (meter will not sit still)");
  Serial.println("    RIGHT [3] MOSI -> must WIGGLE");
  Serial.println("    RIGHT [4] MISO -> must WIGGLE (wiggle = chip IS driving = alive)");
  Serial.println("    A FLAT 0.00V on MISO while reads run means the chip is not");
  Serial.println("    answering at all -> suspect power, NSS pin, or orientation.");
  Serial.println();
  Serial.println("  EACH READ IS PRINTED THREE WAYS (pull-up/pull-down fight test):");
  Serial.println("    PD / float / PU all agree  -> MISO is solid, chip is driving");
  Serial.println("    PD=0x00 and PU=0xFF        -> MISO FLOATING, chip NOT answering");
  Serial.println("=========================================================");
  Serial.println();

  // --- Reset the chip first so we start from a known state ---
  Serial.println("[0] Reset pulse (RST LOW 20ms -> HIGH -> 200ms settle)...");
  digitalWrite(PIN_RST, LOW);
  delay(20);
  digitalWrite(PIN_RST, HIGH);
  delay(200);
  Serial.println("    done.");
  Serial.println();

  // --- Read MISO idle before any transaction ---
  Serial.println("[1] MISO idle (NSS HIGH, nothing selected):");
  Serial.print("    MISO = ");
  Serial.println(digitalRead(PIN_MISO) ? "HIGH (floating)" : "LOW");
  Serial.println("    Either reading is acceptable here - the chip tri-states");
  Serial.println("    MISO when NSS is HIGH, so the line floats.");
  Serial.println();

  // --- The actual read ---
  Serial.println("[2] Reading register 0x42 (silicon version)...");
  delay(100);
  uint8_t v42 = readRegister(0x42);
  Serial.print("    Reg 0x42 = 0x");
  printHex8(v42);
  Serial.println();
  Serial.println();

  // --- Read two more registers to see if the pattern is consistent ---
  Serial.println("[3] Two more registers, for pattern comparison:");
  delay(50);
  uint8_t v01 = readRegister(0x01);   // OpMode
  Serial.print("    Reg 0x01 = 0x");
  printHex8(v01);
  Serial.println();
  delay(50);
  uint8_t v09 = readRegister(0x09);   // PaConfig
  Serial.print("    Reg 0x09 = 0x");
  printHex8(v09);
  Serial.println();
  Serial.println();

  // --- Repeat the 0x42 read 5 times: consistency is the real signal ---
  Serial.println("[4] Reg 0x42 read 5x (consistency check):");
  uint8_t results[5];
  bool allSame = true;
  for (int i = 0; i < 5; i++) {
    results[i] = readRegister(0x42);
    Serial.print("    #");
    Serial.print(i + 1);
    Serial.print(" = 0x");
    printHex8(results[i]);
    Serial.println();
    if (results[i] != results[0]) allSame = false;
    delay(80);
  }
  Serial.print("    Consistent : ");
  Serial.println(allSame ? "YES" : "NO  <-- INCONSISTENT = intermittent contact");
  Serial.println();

  // --- Verdict ---
  Serial.println("=========================================================");
  Serial.println("  VERDICT T3");
  if (MOSI_LOW) {
    Serial.println("  (MOSI_LOW = 1: MOSI held LOW, so reg 0x00 was addressed,");
    Serial.println("   not 0x42. Look for ANY stable non-trivial value.)");
  } else {
    Serial.println("  (MOSI_LOW = 0: real reads of 0x42, target is 0x12.)");
  }
  Serial.println("=========================================================");

  if (v42 == 0x12) {
    Serial.println("  [PASS] 0x12 - CHIP IS ALIVE AND RESPONDING.");
    Serial.println("         RST, NSS, SCK, MISO and power are all good.");
    Serial.println("         NEXT: run T4 (hardware SPI), then T6.");
    digitalWrite(PIN_EXT_LED, HIGH);
  } else if (!allSame) {
    Serial.println("  [FAIL] INCONSISTENT values across 5 reads.");
    Serial.println("         This is the fingerprint of INTERMITTENT CONTACT,");
    Serial.println("         not a dead chip. A dead chip returns the same");
    Serial.println("         wrong value every time.");
    Serial.println("         NEXT: squeeze/reseat the MISO, SCK and NSS wires");
    Serial.println("               at the Ra-02 end one at a time, re-running");
    Serial.println("               T3 after each. Which wire changes the result?");
  } else if (v42 == 0xFF && !MOSI_LOW) {
    Serial.println("  [FAIL] 0xFF - nobody is driving MISO.");
    Serial.println("         Causes, in order of likelihood:");
    Serial.println("           1. NSS not reaching RIGHT [2] - chip never selected");
    Serial.println("           2. MISO wire not reaching RIGHT [4]");
    Serial.println("           3. RST still low (verify with T2 + meter)");
    Serial.println("           4. Module has no power (meter LEFT [3] = 3.2V?)");
    Serial.println("         NEXT: probe NSS on Ra-02 RIGHT [2] during this run;");
    Serial.println("               it must dip toward 0V. If it stays at 3.2V,");
    Serial.println("               the NSS wire is open or on the wrong pin.");
  } else if (v42 == 0x00 && !MOSI_LOW) {
    Serial.println("  [FAIL] 0x00 - MISO held LOW.");
    Serial.println("         The chip is likely selected (NSS working) but not");
    Serial.println("         clocking data out. Suspect:");
    Serial.println("           1. SCK wire not reaching RIGHT [5]");
    Serial.println("           2. MISO shorted to GND on the breadboard");
    Serial.println("           3. Chip held in reset (run T2 with a meter)");
    Serial.println("         NEXT: probe SCK (Ra-02 RIGHT [5]) while T3 runs;");
    Serial.println("               it must wiggle. Meter should not sit still.");
  } else if (MOSI_LOW && v42 == 0xFF) {
    Serial.println("  [INCONCLUSIVE] 0xFF with MOSI tied LOW.");
    Serial.println("         Cannot tell whether the chip is answering or");
    Serial.println("         the line is floating. Set MOSI_LOW 0 and re-run");
    Serial.println("         to perform the real 0x42 read.");
  } else if (MOSI_LOW && v42 == 0x00) {
    Serial.println("  [INCONCLUSIVE] 0x00 with MOSI tied LOW.");
    Serial.println("         Line is held low or the chip is writing rather");
    Serial.println("         than answering. Set MOSI_LOW 0 and re-run for");
    Serial.println("         the real 0x42 read.");
  } else {
    Serial.println("  [PARTIAL] Non-trivial value - the chip IS responding,");
    Serial.println("            but the data is wrong.");
    Serial.println("            That points to SPI mode/timing or a swapped");
    Serial.println("            MOSI/MISO pair rather than a dead chip.");
    Serial.println("            NEXT: run T4 and compare MOSI/MISO swap results.");
  }

  Serial.println("=========================================================");
  Serial.println();
}

unsigned int readCount = 0;

// Read 0x42 three times, once per MISO input mode.
//   INPUT_PULLDOWN : internal ~50k to GND
//   INPUT          : high-Z, nothing fighting the line
//   INPUT_PULLUP   : internal ~45k to 3.3V
//
// The SX1278 MISO driver is strong (milliamps) and WINS against these weak
// pulls, so a chip that is answering returns the SAME value in all three modes.
// A line nobody is driving is at the mercy of the pulls, so it returns the
// signature  0x00 / noise / 0xFF   ->  chip is NOT answering.
uint8_t readWithMode(uint8_t mode, uint8_t addr) {
  pinMode(PIN_MISO, mode);
  uint8_t v = readRegister(addr);
  pinMode(PIN_MISO, INPUT);
  return v;
}

void loop() {
  digitalWrite(PIN_BOARD_LED, HIGH);
  readCount++;

  uint8_t vHi  = readWithMode(INPUT_PULLDOWN, 0x42);  // pulled to GND
  uint8_t vFl  = readWithMode(INPUT,           0x42);  // floating
  uint8_t vLo  = readWithMode(INPUT_PULLUP,    0x42);  // pulled to 3.3V

  Serial.print("    [");
  if (readCount < 10) Serial.print(" ");
  Serial.print(readCount);
  Serial.print("]  PD=0x"); printHex8(vHi);
  Serial.print("   float=0x"); printHex8(vFl);
  Serial.print("   PU=0x");   printHex8(vLo);
  if (vFl == 0x12 || vLo == 0x12 || vHi == 0x12) Serial.print("   <== EXPECTED");
  Serial.println();

  bool floating = (vHi == 0x00 && vLo == 0xFF);
  bool driving  = (vHi == vLo && vHi != 0x00 && vLo != 0xFF);

  if (driving) {
    if (vFl == 0x12) {
      Serial.println("           [PASS] chip driving and correct -> go to T4/T6.");
    } else {
      Serial.println("           CHIP IS DRIVING - all three modes agree, so MISO");
      Serial.println("           is solid. The wrong value means SCK/MOSI contact or");
      Serial.println("           a swapped MOSI/MISO pin, NOT a dead chip.");
    }
  } else if (floating) {
    Serial.println("           MISO IS FLOATING - nobody is driving it (0x00 / 0xFF");
    Serial.println("           bookends prove the pulls won). The chip is not");
    Serial.println("           answering: no power, no NSS, wrong module pin, or dead.");
  } else {
    Serial.println("           MARGINAL - mixes driving and floating. That is the");
    Serial.println("           signature of a bad contact on MISO or NSS.");
  }

  if (readCount % 5 == 0) Serial.println();

  digitalWrite(PIN_BOARD_LED, LOW);
  delay(1500);
}
