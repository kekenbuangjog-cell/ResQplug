/*
 * ==============================================================================
 *  ResQPlug RA02_BRINGUP - T1: Pin State Verification
 * ==============================================================================
 *  PURPOSE
 *    Put every ESP32 pin the Ra-02 uses into a KNOWN state, verify the ESP32
 *    can read them back, then REPEATEDLY toggle two of them so you can prove
 *    with a meter that each wire physically delivers its signal to the Ra-02.
 *
 *  WHAT IT TESTS
 *    1. ESP32 can drive and read back its own pins          (automatic)
 *    2. RST  wire delivers D14 to Ra-02 LEFT  [4]           (meter, phase A)
 *    3. MISO wire delivers D19 to Ra-02 RIGHT [4]           (meter, phase B)
 *
 *  HOW TO RUN IT
 *    Upload, open Serial Monitor at 115200, then park your DT-830B
 *    (dial: DCV 20) on the pin named by the current phase banner.
 *    The sketch alternates between the two phases forever, so you have
 *    as long as you need - no more missing a 0.8 second window.
 *
 *  PHASE A - RST TOGGLE   (red probe on Ra-02 LEFT [4])
 *      meter must swing 0.0 V <-> ~3.2 V
 *      swings   -> D14 wire correct, chip can be reset
 *      stuck 0V -> wire open, chip permanently in reset (would cause 0xFF)
 *      stuck 3V -> wire landed on the 3.3V rail instead of D14
 *
 *  PHASE B - MISO TOGGLE  (red probe on Ra-02 RIGHT [4])
 *      meter must swing 0.0 V <-> ~3.2 V
 *      swings   -> MISO wire is continuous, chip tri-stated as expected
 *      stuck 0V -> MISO WIRE IS OPEN. This alone explains 0xFF on every read.
 *      (an unplugged wire reads 0 V on a meter, so an idle reading proves
 *       nothing - only this toggle can prove continuity)
 *
 *  STATIC LEVELS (check before the toggles start)
 *    LEFT  [3] 3.3V -> ~3.2 V
 *    LEFT  [4] RST  -> ~3.2 V
 *    RIGHT [2] NSS  -> ~3.2 V
 *    RIGHT [3] MOSI -> ~0.0 V
 *    RIGHT [4] MISO -> ~0.0 V  (idle only - see PHASE B above)
 *    RIGHT [5] SCK  -> ~0.0 V
 *
 *  Black probe always goes to any GND.
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

#define TOGGLE_CYCLES   8    // toggles per phase before switching phase
#define TOGGLE_HALF_MS  700  // ms at each level

int fails = 0;

void printBanner(const char *phase, const char *pin, const char *action) {
  Serial.println();
  Serial.println("=========================================================");
  Serial.print("  PHASE ");
  Serial.print(phase);
  Serial.print(" - probe Ra-02 ");
  Serial.println(pin);
  Serial.println("=========================================================");
  Serial.print("  ACTION: ");
  Serial.println(action);
  Serial.println("  Expected: meter swings 0.0 V <-> ~3.2 V");
  Serial.println("---------------------------------------------------------");
}

void setup() {
  Serial.begin(115200);
  delay(2000);

  pinMode(PIN_NSS,   OUTPUT); digitalWrite(PIN_NSS,   HIGH);   // deselected
  pinMode(PIN_RST,   OUTPUT); digitalWrite(PIN_RST,   HIGH);   // not in reset
  pinMode(PIN_SCK,   OUTPUT); digitalWrite(PIN_SCK,   LOW);
  pinMode(PIN_MOSI,  OUTPUT); digitalWrite(PIN_MOSI,  LOW);
  pinMode(PIN_MISO,  INPUT);
  pinMode(PIN_DIO0,  INPUT);
  pinMode(PIN_BOARD_LED, OUTPUT); digitalWrite(PIN_BOARD_LED, LOW);
  pinMode(PIN_EXT_LED,   OUTPUT); digitalWrite(PIN_EXT_LED,   LOW);

  Serial.println();
  Serial.println("=========================================================");
  Serial.println("  T1: PIN STATE VERIFICATION");
  Serial.println("=========================================================");
  Serial.println();
  Serial.println("  Static levels to confirm on the Ra-02 first:");
  Serial.println("    LEFT  [3] 3.3V -> ~3.2 V");
  Serial.println("    LEFT  [4] RST  -> ~3.2 V");
  Serial.println("    RIGHT [2] NSS  -> ~3.2 V");
  Serial.println("    RIGHT [3] MOSI -> ~0.0 V");
  Serial.println("    RIGHT [4] MISO -> ~0.0 V  (idle only - see PHASE B)");
  Serial.println("    RIGHT [5] SCK  -> ~0.0 V");
  Serial.println();
  Serial.println("  Black probe always on GND. DT-830B dial: DCV 20");
  Serial.println();

  // --- Step 1: can the ESP32 read back its own pins? ---
  Serial.println("[1] ESP32 pin readback:");

  if (digitalRead(PIN_NSS) == HIGH) {
    Serial.println("    NSS  (D17) = HIGH  OK - chip deselected");
  } else {
    Serial.println("    NSS  (D17) = LOW   FAIL - pin not driving HIGH (short to GND?)");
    fails++;
  }

  if (digitalRead(PIN_RST) == HIGH) {
    Serial.println("    RST  (D14) = HIGH  OK - chip out of reset");
  } else {
    Serial.println("    RST  (D14) = LOW   FAIL - pin not driving HIGH (short to GND?)");
    fails++;
  }

  if (digitalRead(PIN_SCK) == LOW) {
    Serial.println("    SCK  (D18) = LOW   OK");
  } else {
    Serial.println("    SCK  (D18) = HIGH  FAIL - unexpected state");
    fails++;
  }

  if (digitalRead(PIN_MOSI) == LOW) {
    Serial.println("    MOSI (D23) = LOW   OK");
  } else {
    Serial.println("    MOSI (D23) = HIGH  FAIL - unexpected state");
    fails++;
  }

  Serial.println();
  Serial.println("[2] MISO line (input, chip tri-stated):");
  int miso = digitalRead(PIN_MISO);
  if (miso == LOW) {
    Serial.println("    MISO (D19) = LOW   OK - line not stuck HIGH");
  } else {
    Serial.println("    MISO (D19) = HIGH  WARN - floating high, or a wrong wire");
    Serial.println("                        landed here. PHASE B will settle it.");
  }

  Serial.println();
  Serial.println("=========================================================");
  if (fails == 0) {
    Serial.println("  ESP32 SIDE OK - all pins at expected state.");
    Serial.println("  Now proving the WIRES with the meter, starting below.");
  } else {
    Serial.print  ("  ESP32 SIDE FAIL - ");
    Serial.print  (fails);
    Serial.println(" pin(s) not at expected state.");
    Serial.println("  Fix the ESP32-side fault before trusting the wire tests.");
  }
  Serial.println("=========================================================");
}

void loop() {
  // ---- PHASE A: prove the RST wire ----
  printBanner("A", "LEFT [4] (RST)", "keep the red probe here for this phase");
  for (int i = 1; i <= TOGGLE_CYCLES; i++) {
    digitalWrite(PIN_RST, LOW);
    Serial.print("  ");
    Serial.print(i);
    Serial.println(") RST = LOW   -> meter should read 0.0 V");
    delay(TOGGLE_HALF_MS);

    digitalWrite(PIN_RST, HIGH);
    Serial.print("  ");
    Serial.print(i);
    Serial.println(") RST = HIGH  -> meter should read ~3.2 V");
    delay(TOGGLE_HALF_MS);
  }
  digitalWrite(PIN_RST, HIGH);
  Serial.println("  Phase A done. RST left HIGH (chip out of reset).");

  // ---- PHASE B: prove the MISO wire ----
  printBanner("B", "RIGHT [4] (MISO)", "MOVE the red probe to RIGHT [4] now");
  pinMode(PIN_MISO, OUTPUT);              // take over the line from the chip
  for (int i = 1; i <= TOGGLE_CYCLES; i++) {
    digitalWrite(PIN_MISO, HIGH);
    Serial.print("  ");
    Serial.print(i);
    Serial.println(") MISO = HIGH -> wire connected: meter reads ~3.2 V");
    Serial.println("                          wire OPEN:      meter reads 0.0 V");
    delay(TOGGLE_HALF_MS);

    digitalWrite(PIN_MISO, LOW);
    Serial.print("  ");
    Serial.print(i);
    Serial.println(") MISO = LOW  -> meter reads 0.0 V either way");
    delay(TOGGLE_HALF_MS);
  }
  pinMode(PIN_MISO, INPUT);              // release the line back to the chip
  Serial.println("  Phase B done. MISO released to the chip.");
  Serial.println();
  Serial.println("  >>> Alternating back to Phase A. Watch the banners. <<<");
}
