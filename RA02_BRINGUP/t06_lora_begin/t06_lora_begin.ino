/*
 * ==============================================================================
 *  ResQPlug RA02_BRINGUP - T6: LoRa Library Initialisation
 * ==============================================================================
 *  PURPOSE
 *    The real acceptance test. T1-T5 prove individual signals; T6 asks the
 *    actual library the app depends on whether it can bring the radio up.
 *
 *    Requires: sandeepmistry/arduino-LoRa library
 *    (Sketch -> Include Library -> Manage Libraries -> search "LoRa")
 *
 *  WHAT IT TESTS
 *    - LoRa.setPins() + LoRa.begin(433E6) returns true
 *    - Chip version read matches what the library expects
 *    - Radio parameters can be configured
 *
 *  PASS: prints "SUCCESS - Ra-02 online" and the LED lights. Go to T7.
 *  FAIL: LoRa.begin() returns false -> library could not detect the chip.
 *        The library prints its own hint; compare with T3/T4/T5 results.
 *
 *  PIN NOTE
 *    PIN_NSS below must match the pin T5 discovered. If T5 said a different
 *    pin, change it here and in ra02_pins.h before running this test.
 * ==============================================================================
 */

#include <SPI.h>
#include <LoRa.h>

// SYNC: ra02_pins.h
#define PIN_SCK        18
#define PIN_MISO       19
#define PIN_MOSI       23
#define PIN_NSS        17   // <-- CHANGE THIS if T5 discovered a different pin
#define PIN_RST        14
#define PIN_DIO0       26
#define PIN_BOARD_LED   2
#define PIN_EXT_LED     4
// END SYNC

#define LORA_FREQUENCY 433E6

void setup() {
  Serial.begin(115200);
  delay(2000);

  pinMode(PIN_BOARD_LED, OUTPUT); digitalWrite(PIN_BOARD_LED, LOW);
  pinMode(PIN_EXT_LED,   OUTPUT); digitalWrite(PIN_EXT_LED,   LOW);

  Serial.println();
  Serial.println("=========================================================");
  Serial.println("  T6: LORA LIBRARY INITIALISATION");
  Serial.println("=========================================================");
  Serial.print("  NSS=");
  Serial.print(PIN_NSS);
  Serial.print("  RST=");
  Serial.print(PIN_RST);
  Serial.print("  DIO0=");
  Serial.print(PIN_DIO0);
  Serial.print("  SCK=");
  Serial.print(PIN_SCK);
  Serial.print("  MISO=");
  Serial.print(PIN_MISO);
  Serial.print("  MOSI=");
  Serial.println(PIN_MOSI);
  Serial.print("  Frequency : ");
  Serial.print(LORA_FREQUENCY / 1E6, 1);
  Serial.println(" MHz (Philippine ISM band)");
  Serial.println("=========================================================");
  Serial.println();

  Serial.println("[1] Initialising SPI and LoRa pins...");
  SPI.begin(PIN_SCK, PIN_MISO, PIN_MOSI, PIN_NSS);
  LoRa.setPins(PIN_NSS, PIN_RST, PIN_DIO0);
  Serial.println("    done.");
  Serial.println();

  Serial.println("[2] LoRa.begin(433E6) ...");
  bool ok = LoRa.begin(LORA_FREQUENCY);
  Serial.print("    Result : ");
  Serial.println(ok ? "true" : "false");
  Serial.println();

  if (!ok) {
    Serial.println("=========================================================");
    Serial.println("  VERDICT T6: FAIL - LoRa.begin() returned false");
    Serial.println("=========================================================");
    Serial.println("  The library could not detect the SX1278.");
    Serial.println();
    Serial.println("  Work through this order (most->least likely):");
    Serial.println("   [ ] 1. Does T3 return 0x12? If not, fix that first -");
    Serial.println("           T6 cannot pass until register reads work.");
    Serial.println("   [ ] 2. Is PIN_NSS above the pin T5 discovered?");
    Serial.println("   [ ] 3. Is RST on LEFT [4] actually swinging? (T2 + meter)");
    Serial.println("   [ ] 4. Antenna attached? A missing antenna does not stop");
    Serial.println("           begin(), but it will burn the PA on transmit.");
    Serial.println("   [ ] 5. Is 3.3V present at LEFT [3] under power? (DT-830B)");
    Serial.println("   [ ] 6. Is the LoRa library installed and the correct one?");
    Serial.println();
    Serial.println("  Reminder from your own log: two chips failing identically");
    Serial.println("  with all 24 SPI permutations failing is near-impossible");
    Serial.println("  for dead silicon. The connection environment is the fault.");
    Serial.println("=========================================================");
    Serial.println();
    return;
  }

  // --- Configure the radio the way ResQPlug needs it ---
  Serial.println("[3] Configuring radio...");
  LoRa.setTxPower(18);                 // +18 dBm, Ra-02 spec maximum
  LoRa.setSpreadingFactor(7);          // default SF7
  LoRa.setSignalBandwidth(125E3);      // 125 kHz
  LoRa.setCodingRate4(5);              // 4/5
  LoRa.setPreambleLength(8);
  LoRa.setSyncWord(0x12);              // ResQPlug mesh sync word
  Serial.println("    TxPower=+18dBm  SF=7  BW=125kHz  CR=4/5  SyncWord=0x12");
  Serial.println("    done.");
  Serial.println();

  Serial.println("[4] Idle receive for 5 seconds (is the RX path alive?)...");
  LoRa.receive();
  unsigned long start = millis();
  int packets = 0;
  while (millis() - start < 5000) {
    int packetSize = LoRa.parsePacket();
    if (packetSize) {
      packets++;
      Serial.print("    Received ");
      Serial.print(packetSize);
      Serial.print(" bytes: ");
      while (LoRa.available()) Serial.print((char)LoRa.read());
      Serial.println();
    }
  }
  LoRa.idle();
  Serial.print("    Packets received in 5s : ");
  Serial.println(packets);
  Serial.println("    (0 is normal if nothing nearby is transmitting)");
  Serial.println();

  Serial.println("=========================================================");
  Serial.println("  VERDICT T6: PASS");
  Serial.println("  SUCCESS - Ra-02 online at 433.00 MHz.");
  Serial.println("  Chip alive, wiring correct, library accepts the module.");
  Serial.println();
  Serial.println("  NEXT: T7 - transmit a beacon and confirm the RF path.");
  Serial.println("  (Antenna MUST be snapped on before T7.)");
  Serial.println("=========================================================");
  Serial.println();

  digitalWrite(PIN_EXT_LED, HIGH);
}

void loop() {
  // Heartbeat: 1 short blink = T6 passed.
  digitalWrite(PIN_BOARD_LED, HIGH); delay(150);
  digitalWrite(PIN_BOARD_LED, LOW);  delay(1850);
}
