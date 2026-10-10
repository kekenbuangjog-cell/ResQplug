/*
 * ==============================================================================
 *  ResQPlug RA02_BRINGUP - T2: Reset Release
 * ==============================================================================
 *  PURPOSE
 *    A chip held in reset does nothing - it never answers on MISO, so every
 *    SPI read returns 0xFF. Your troubleshooting log Step 3 found RST was
 *    floating, wired it to D14, and Step 4 said "still 0xFF" - but RST was
 *    never re-measured with a multimeter afterwards. This test closes that gap.
 *
 *  WHAT IT TESTS
 *    - RST is actually driven HIGH by D14
 *    - The Ra-02 RST pin actually sees that voltage (probe it)
 *    - Whether releasing reset changes anything on the MISO line
 *
 *  MEASURE WHILE THIS RUNS (DT-830B on DCV 20)
 *    Red on Ra-02 LEFT [4] RST, black on any GND:
 *      during the LOW phase   -> 0.0 V
 *      during the HIGH phase  -> ~3.2 V
 *      If it NEVER moves from 0V -> the RST wire is open. That is your bug.
 *
 *  PASS: Ra-02 RST pin swings 0.0V <-> ~3.2V in step with the Serial output.
 *  FAIL: Ra-02 RST pin stays at 0V while Serial says HIGH
 *        -> D14 wire not reaching LEFT [4]. Reseat, re-run T2.
 *  FAIL: Ra-02 RST pin reads ~3.2V but T3 still returns 0xFF
 *        -> RST is fine, move on to T3/T4.
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
  Serial.println("  T2: RESET RELEASE");
  Serial.println("=========================================================");
  Serial.println();
  Serial.println("  Put your DT-830B (DCV 20) on Ra-02 LEFT [4] RST now.");
  Serial.println("  Black probe -> any GND. Watch the voltage track this log.");
  Serial.println();

  // --- Baseline ---
  Serial.println("[1] Baseline (RST driven HIGH):");
  digitalWrite(PIN_RST, HIGH);
  delay(500);
  Serial.print("    ESP32 D14 reads ");
  Serial.print(digitalRead(PIN_RST) ? "HIGH" : "LOW");
  Serial.println("  |  meter on Ra-02 RST should show ~3.2V");
  Serial.println("    [ ] meter matches  [ ] meter stuck at 0V -> WIRE OPEN");
  delay(1500);

  // --- Pulse LOW then release ---
  Serial.println();
  Serial.println("[2] Pulsing RST LOW for 20ms, then releasing...");

  digitalWrite(PIN_RST, LOW);
  Serial.println("    >>> RST = LOW   (meter should drop to 0.0V)");
  delay(20);
  digitalWrite(PIN_RST, HIGH);
  Serial.println("    >>> RST = HIGH  (meter should jump to ~3.2V)");
  delay(300);

  int mBefore = digitalRead(PIN_MISO);
  Serial.print("    MISO before settle = ");
  Serial.println(mBefore ? "HIGH" : "LOW");

  // --- Second pulse with a longer hold ---
  digitalWrite(PIN_RST, LOW);
  delay(100);
  digitalWrite(PIN_RST, HIGH);
  delay(500);

  int mAfter = digitalRead(PIN_MISO);
  Serial.print("    MISO after release  = ");
  Serial.println(mAfter ? "HIGH" : "LOW");

  // --- Repeat so you have plenty of time to read the meter ---
  Serial.println();
  Serial.println("[3] 6 more slow pulses - keep the probe on the pin:");
  for (int i = 1; i <= 6; i++) {
    digitalWrite(PIN_RST, LOW);
    Serial.print("    ");
    Serial.print(i);
    Serial.println(") RST=LOW   -> meter 0.0V ?");
    delay(900);
    digitalWrite(PIN_RST, HIGH);
    Serial.print("    ");
    Serial.print(i);
    Serial.println(") RST=HIGH  -> meter ~3.2V ?");
    delay(900);
  }
  digitalWrite(PIN_RST, HIGH);

  // --- DIO0 sanity: should idle LOW on a healthy idle chip ---
  Serial.println();
  Serial.println("[4] DIO0 (Ra-02 LEFT [5]) idle state:");
  Serial.print("    DIO0 = ");
  Serial.println(digitalRead(PIN_DIO0) ? "HIGH" : "LOW");

  Serial.println();
  Serial.println("=========================================================");
  Serial.println("  VERDICT T2 - match what your meter did:");
  Serial.println();
  Serial.println("  [ ] A) Meter swung 0.0V <-> 3.2V with the log");
  Serial.println("       -> RST PATH IS GOOD. Go to T3.");
  Serial.println();
  Serial.println("  [ ] B) Meter stayed at 0.0V the whole time");
  Serial.println("       -> RST WIRE IS OPEN (D14 not reaching LEFT [4]).");
  Serial.println("          Reseat the D14 wire, re-run T2. Do NOT go to T3;");
  Serial.println("          a chip in permanent reset always returns 0xFF.");
  Serial.println();
  Serial.println("  [ ] C) Meter sat at ~3.2V the whole time (never dropped)");
  Serial.println("       -> RST wire connects to a permanent 3.3V rail, not D14.");
  Serial.println("          Wrong hole. Move it to GPIO14, re-run T2.");
  Serial.println("=========================================================");
  Serial.println();
}

void loop() {
  digitalWrite(PIN_BOARD_LED, HIGH); delay(500);
  digitalWrite(PIN_BOARD_LED, LOW);  delay(500);
}
