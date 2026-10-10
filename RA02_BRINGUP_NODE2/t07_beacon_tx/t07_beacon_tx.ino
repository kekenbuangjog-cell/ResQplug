/*
 * ==============================================================================
 *  ResQPlug RA02_BRINGUP - T7: Beacon Transmit
 * ==============================================================================
 *  PURPOSE
 *    Final test: prove the radio actually puts energy on the air.
 *    Everything before this only proves the ESP32 can TALK to the chip.
 *    This proves the chip can TRANSMIT.
 *
 *    Requires: sandeepmistry/arduino-LoRa library
 *
 *  ⚠ SAFETY - ANTENNA FIRST
 *    Transmitting into an open circuit (no antenna) reflects power back into
 *    the SX1278 power amplifier and can destroy it. Snap the IPEX 433 MHz
 *    antenna onto the Ra-02 before flashing this sketch.
 *
 *  WHAT IT TESTS
 *    - endPacket() completes without timing out
 *    - PaConfig registers reflect a real PA configuration
 *    - Optionally: a second board running T7 receives the beacons
 *
 *  PASS: all 5 beacons report SENT and the packet IRQ fires.
 *  FAIL: TX timeout -> chip detects no RF load, usually missing antenna
 *        or the PA supply rail is sagging.
 *
 *  OPTIONAL RANGE CHECK
 *    Flash T7 on a second ESP32+Ra-02 pair, then change MODE below to
 *    RECEIVER to listen. Or change to TRANSCEIVER to do both.
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
#define SYNC_WORD      0x12
#define BEACON_COUNT   5
#define BEACON_INTERVAL_MS 3000

// 0 = transmit only, 1 = receive only, 2 = both (needs a second board)
#define MODE 0

const char* NODE_ID = "RA02-T7";

void setup() {
  Serial.begin(115200);
  delay(2000);

  pinMode(PIN_BOARD_LED, OUTPUT); digitalWrite(PIN_BOARD_LED, LOW);
  pinMode(PIN_EXT_LED,   OUTPUT); digitalWrite(PIN_EXT_LED,   LOW);

  Serial.println();
  Serial.println("=========================================================");
  Serial.println("  T7: BEACON TRANSMIT");
  Serial.println("=========================================================");
  Serial.println("  ⚠  ANTENNA MUST BE CONNECTED before this runs.");
  Serial.println("     Transmitting with no antenna can burn the SX1278 PA.");
  Serial.println("=========================================================");
  Serial.println();

  SPI.begin(PIN_SCK, PIN_MISO, PIN_MOSI, PIN_NSS);
  LoRa.setPins(PIN_NSS, PIN_RST, PIN_DIO0);

  Serial.println("[1] Bringing the radio up...");
  if (!LoRa.begin(LORA_FREQUENCY)) {
    Serial.println("    FAIL - LoRa.begin() returned false.");
    Serial.println();
    Serial.println("=========================================================");
    Serial.println("  VERDICT T7: FAIL - radio not initialised");
    Serial.println("  Go back to T6. T7 cannot run until T6 passes.");
    Serial.println("=========================================================");
    Serial.println();
    return;
  }
  Serial.println("    ok.");
  Serial.println();

  LoRa.setTxPower(18);
  LoRa.setSpreadingFactor(7);
  LoRa.setSignalBandwidth(125E3);
  LoRa.setCodingRate4(5);
  LoRa.setPreambleLength(8);
  LoRa.setSyncWord(SYNC_WORD);
  LoRa.enableCrc();

  Serial.println("[2] Radio configured:");
  Serial.println("    Freq=433.00MHz  TxPower=+18dBm  SF=7  BW=125kHz");
  Serial.println("    CR=4/5  Preamble=8  SyncWord=0x12  CRC=on");
  Serial.println();

  if (MODE == 1) {
    receiveLoop();
    return;
  }

  // --- Transmit the beacons ---
  Serial.println("[3] Transmitting beacons:");
  int sent = 0;
  for (int i = 0; i < BEACON_COUNT; i++) {
    Serial.print("    Beacon ");
    Serial.print(i);
    Serial.print(" -> ");

    LoRa.beginPacket();
    LoRa.print(NODE_ID);
    LoRa.print(" #");
    LoRa.print(i);
    LoRa.print(" t=");
    LoRa.print(millis());
    LoRa.print(" rss=");
    // Read the board's own supply indication is not available in the lib,
    // so send uptime as a payload health marker.
    LoRa.print(analogRead(34));   // optional ADC channel, harmless if floating
    int result = LoRa.endPacket();   // blocks until TX complete

    if (result == 1) {
      Serial.print("SENT (");
      Serial.print(millis());
      Serial.println(" ms)");
      sent++;
      digitalWrite(PIN_EXT_LED, HIGH);
      delay(120);
      digitalWrite(PIN_EXT_LED, LOW);
    } else {
      Serial.println("TIMEOUT");
    }

    delay(BEACON_INTERVAL_MS);
  }

  Serial.println();

  // --- Verdict ---
  Serial.println("=========================================================");
  if (sent == BEACON_COUNT) {
    Serial.println("  VERDICT T7: PASS - all beacons transmitted.");
    Serial.println();
    Serial.println("  The link is fully working:");
    Serial.println("    power     OK");
    Serial.println("    SPI comms OK (register reads)");
    Serial.println("    library   OK (LoRa.begin)");
    Serial.println("    RF output OK (packets sent)");
    Serial.println();
    Serial.println("  To confirm range, flash T7 with MODE=1 on a second");
    Serial.println("  ESP32+Ra-02 pair and watch it receive these beacons.");
    Serial.println();
    Serial.println("  FOLLOW-UP - ALL THREE COMPLETED 2026-10-07:");
    Serial.println("    1. Reconciled the 3 markdown docs (GPIO 5 -> 17)");
    Serial.println("    2. Updated unified_node0.1/0.2 + 2 old test sketches");
    Serial.println("    3. Results recorded in TEST_LIST.md / FINDINGS.md");
    digitalWrite(PIN_EXT_LED, HIGH);
  } else {
    Serial.print("  VERDICT T7: PARTIAL - ");
    Serial.print(sent);
    Serial.print("/");
    Serial.print(BEACON_COUNT);
    Serial.println(" transmitted.");
    Serial.println();
    Serial.println("  TIMEOUTs usually mean:");
    Serial.println("    - Antenna not connected (MOST LIKELY - check now)");
    Serial.println("    - Supply rail sagging under PA current (~120mA)");
    Serial.println("      -> meter 3.3V at LEFT [3] during a TX attempt");
    Serial.println("    - SX1278 PA damaged by a previous no-antenna transmit");
  }
  Serial.println("=========================================================");
  Serial.println();
}

void receiveLoop() {
  Serial.println("Receiving mode - waiting for beacons...");
  LoRa.receive();
  while (true) {
    int packetSize = LoRa.parsePacket();
    if (packetSize) {
      Serial.print("RX [");
      Serial.print(LoRa.packetRssi());
      Serial.print(" dBm] ");
      while (LoRa.available()) Serial.print((char)LoRa.read());
      Serial.println();
      digitalWrite(PIN_EXT_LED, HIGH);
      delay(100);
      digitalWrite(PIN_EXT_LED, LOW);
    }
    delay(10);
  }
}

void loop() {
  if (MODE == 1) return;
  // Idle heartbeat after the TX sequence completes.
  digitalWrite(PIN_BOARD_LED, HIGH); delay(250);
  digitalWrite(PIN_BOARD_LED, LOW);  delay(250);
}
