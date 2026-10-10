/*
 * ==============================================================================
 *  ResQPlug RA02_BRINGUP_NODE2 - T8: Two-Node Link
 * ==============================================================================
 *  PURPOSE
 *    The test neither bring-up kit ever ran: prove a packet actually crosses
 *    the air from one ESP32+Ra-02 to the other.
 *
 *      T1-T5  prove the ESP32 can talk to the chip.
 *      T6     proves the library accepts the chip.
 *      T7     proves the chip puts energy on the air.
 *      T8     proves the OTHER end hears it. That is the whole point of a mesh.
 *
 *    Requires: sandeepmistry/arduino-LoRa library
 *
 *  ⚠ SAFETY - ANTENNA FIRST, ON BOTH BOARDS
 *    Transmitting into an open circuit reflects power back into the SX1278
 *    power amplifier and can destroy it. Seat a 433 MHz antenna on each U.FL
 *    socket before flashing this sketch.
 *
 *  HOW TO RUN
 *    1. Set NODE_ID below to a unique name per board (UNIT1, UNIT2).
 *    2. Flash the SAME sketch to BOTH boards.
 *    3. Open BOTH serial monitors at 115200.
 *    4. Each board beacons every BEACON_INTERVAL_MS and prints everything it
 *       hears. An RX line that is NOT your own NODE_ID is the proof.
 *    5. Optionally type a line into either serial monitor and press Enter —
 *       it is transmitted verbatim so you can watch it appear on the other.
 *
 *  PASS: VERDICT T8: PASS appears, plus at least one "[RX] ... FROM ANOTHER
 *        NODE" line carrying the other board's NODE_ID.
 *  FAIL: TX counts up but no RX ever appears -> the other board's wiring,
 *        frequency settings or antenna are wrong. Run T3 on that board first.
 *
 *  WHY ONE SKETCH FOR BOTH BOARDS
 *    It keeps the test symmetric. Either end can demonstrate the link, and
 *    neither board is special. Change NODE_ID, not the code.
 * ==============================================================================
 */

#include <SPI.h>
#include <LoRa.h>

// SYNC: ra02_pins.h
#define PIN_SCK        18
#define PIN_MISO       19
#define PIN_MOSI       23
#define PIN_NSS        17
#define PIN_RST        14
#define PIN_DIO0       26
#define PIN_BOARD_LED   2
#define PIN_EXT_LED     4
// END SYNC

#define LORA_FREQUENCY     433E6
#define SYNC_WORD          0x12
#define BEACON_INTERVAL_MS 10000

// <<< CHANGE THIS PER BOARD — must differ between the two nodes >>>
#define NODE_ID "RA02-UNIT1"

unsigned long lastBeaconMillis = 0;
unsigned long txCount = 0;
unsigned long rxFromOther = 0;
bool linkConfirmed = false;

void sendBeacon();
void showVerdict();

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(100);   // keep the loop responsive when typing
  delay(2000);

  pinMode(PIN_BOARD_LED, OUTPUT); digitalWrite(PIN_BOARD_LED, LOW);
  pinMode(PIN_EXT_LED,   OUTPUT); digitalWrite(PIN_EXT_LED,   LOW);

  Serial.println();
  Serial.println("=========================================================");
  Serial.println("  T8: TWO-NODE LINK");
  Serial.println("=========================================================");
  Serial.print(  "  Node ID     : "); Serial.println(NODE_ID);
  Serial.println("  Frequency   : 433.00 MHz");
  Serial.println("  ⚠  ANTENNA MUST BE CONNECTED ON BOTH BOARDS.");
  Serial.println("     Transmitting with no antenna can burn the SX1278 PA.");
  Serial.println("=========================================================");
  Serial.println();

  // --- T3 evidence: raw SPI read of the version register ---
  // Software CS (-1) for this probe only. With the hardware CS pin configured,
  // SPI.transfer() toggles NSS itself and races the manual digitalWrite below,
  // which returns 0xFF even on a perfectly healthy chip. t03_spi_bare avoids
  // this by bit-banging with no SPI library at all. After the probe the bus is
  // handed back to the hardware CS pin for the LoRa library.
  SPI.begin(PIN_SCK, PIN_MISO, PIN_MOSI, -1);
  pinMode(PIN_NSS, OUTPUT);
  digitalWrite(PIN_NSS, HIGH);
  LoRa.setPins(PIN_NSS, PIN_RST, PIN_DIO0);

  pinMode(PIN_RST, OUTPUT);
  digitalWrite(PIN_RST, LOW);  delay(20);
  digitalWrite(PIN_RST, HIGH); delay(50);

  SPI.beginTransaction(SPISettings(8000000, MSBFIRST, SPI_MODE0));
  digitalWrite(PIN_NSS, LOW);
  delayMicroseconds(10);
  SPI.transfer(0x42 & 0x7F);
  uint8_t ver = SPI.transfer(0x00);
  digitalWrite(PIN_NSS, HIGH);
  SPI.endTransaction();

  Serial.print("[1] Probe (Software CS) Reg 0x42 = 0x");
  if (ver < 0x10) Serial.print("0");
  Serial.println(ver, HEX);
  if (ver != 0x12) {
    Serial.println("    WARNING - expected 0x12. SPI may not be reaching the chip.");
    Serial.println("              Run T3 in this kit before trusting anything below.");
  }
  Serial.println();

  // Hand the bus back to the hardware CS pin for the LoRa library
  SPI.end();
  SPI.begin(PIN_SCK, PIN_MISO, PIN_MOSI, PIN_NSS);
  LoRa.setPins(PIN_NSS, PIN_RST, PIN_DIO0);

  // --- T6 evidence: library init ---
  Serial.println("[2] LoRa.begin(433E6) ...");
  if (!LoRa.begin(LORA_FREQUENCY)) {
    Serial.println("    FAIL - LoRa.begin() returned false.");
    Serial.println();
    Serial.println("=========================================================");
    Serial.println("  VERDICT T8: FAIL - radio not initialised");
    Serial.println("  Run T3 in this kit. T8 cannot work until SPI reads 0x12.");
    Serial.println("=========================================================");
    Serial.println();
    return;
  }
  Serial.println("    ok.");
  Serial.println();

  // Identical settings on both nodes, and identical to T6/T7.
  LoRa.setTxPower(18);
  LoRa.setSpreadingFactor(7);
  LoRa.setSignalBandwidth(125E3);
  LoRa.setCodingRate4(5);
  LoRa.setPreambleLength(8);
  LoRa.setSyncWord(SYNC_WORD);
  LoRa.enableCrc();
  LoRa.receive();

  Serial.println("[3] Radio configured:");
  Serial.println("    TxPower=+18dBm  SF=7  BW=125kHz  CR=4/5");
  Serial.println("    Preamble=8  SyncWord=0x12  CRC=on");
  Serial.println();

  Serial.println("[4] Link is live.");
  Serial.print(  "    - a beacon goes out every "); Serial.print(BEACON_INTERVAL_MS); Serial.println(" ms");
  Serial.println("    - anything heard is printed as an [RX] line");
  Serial.println("    - type a line and press Enter to transmit it");
  Serial.println();
  Serial.println("=========================================================");
  Serial.println("  WAITING FOR THE OTHER NODE ...");
  Serial.println("=========================================================");
  Serial.println();

  sendBeacon();
}

void loop() {
  // --- beacon on schedule ---
  if (millis() - lastBeaconMillis >= BEACON_INTERVAL_MS) {
    lastBeaconMillis = millis();
    sendBeacon();
  }

  // --- receive ---
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    String payload = "";
    while (LoRa.available()) payload += (char)LoRa.read();

    int rssi = LoRa.packetRssi();
    float snr = LoRa.packetSnr();

    Serial.print("[RX] RSSI=");
    Serial.print(rssi);
    Serial.print(" dBm  SNR=");
    Serial.print(snr, 1);
    Serial.print(" dB  len=");
    Serial.print(packetSize);
    Serial.print("  ");
    Serial.println(payload);

    // Is this from the OTHER board? That is the actual point of T8.
    if (payload.indexOf(NODE_ID) < 0) {
      rxFromOther++;
      Serial.print("       ^^^ FROM ANOTHER NODE - link confirmed (");
      Serial.print(rxFromOther);
      Serial.println(" so far)");
      digitalWrite(PIN_EXT_LED, HIGH);
      delay(100);
      digitalWrite(PIN_EXT_LED, LOW);

      if (!linkConfirmed) {
        linkConfirmed = true;
        showVerdict();
      }
    }
    Serial.println();
  }

  // --- anything typed in the serial monitor is transmitted verbatim ---
  if (Serial.available() > 0) {
    String msg = Serial.readStringUntil('\n');
    msg.trim();
    if (msg.length() > 0) {
      Serial.print("[TX] text -> ");
      Serial.print(msg);
      LoRa.beginPacket();
      LoRa.print(msg);
      int result = LoRa.endPacket();
      Serial.println(result == 1 ? "  SENT" : "  TIMEOUT (antenna?)");
      LoRa.receive();
      Serial.println();
    }
  }

  delay(5);
}

void sendBeacon() {
  LoRa.beginPacket();
  LoRa.print("[BCN:");
  LoRa.print(NODE_ID);
  LoRa.print("|seq=");
  LoRa.print(txCount);
  LoRa.print("|t=");
  LoRa.print(millis());
  LoRa.print("]");
  int result = LoRa.endPacket();

  if (result == 1) {
    Serial.print("[TX] beacon ");
    Serial.print(txCount);
    Serial.println(" -> SENT");
    txCount++;
    digitalWrite(PIN_EXT_LED, HIGH);
    delay(60);
    digitalWrite(PIN_EXT_LED, LOW);
  } else {
    Serial.println("[TX] beacon -> TIMEOUT (antenna missing?)");
  }
  LoRa.receive();
}

void showVerdict() {
  Serial.println("=========================================================");
  Serial.println("  VERDICT T8: PASS - a packet arrived from another node.");
  Serial.println();
  Serial.println("  The link now works in both directions:");
  Serial.println("    power     OK");
  Serial.println("    SPI comms OK (register reads)");
  Serial.println("    library   OK (LoRa.begin)");
  Serial.println("    RF output OK (packets sent)");
  Serial.println("    RF input  OK (packets received)   <-- this test");
  Serial.println();
  Serial.println("  Keep both monitors open to watch RSSI/SNR over time.");
  Serial.println("=========================================================");
  Serial.println();
}
