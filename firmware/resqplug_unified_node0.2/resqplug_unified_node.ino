/*
 * ==============================================================================
 *  ResQPlug Unified Dual-Transport & Live LoRa Transceiver Firmware v3.3
 * ==============================================================================
 *  Hardware  : ESP32-WROOM-32 / NodeMCU-32S (30-pin Type-C)
 *  Transports: 
 *    1. USB-OTG Serial (115200 Baud CDC/UART)
 *    2. Bluetooth Classic SPP (RFCOMM dynamic broadcast)
 *    3. Semtech SX1278 LoRa Radio (Ai-Thinker Ra-02 @ 433.00 MHz ISM Band)
 * ==============================================================================
 */

#include <SPI.h>
#include <LoRa.h>
#include "BluetoothSerial.h"
#include <esp_system.h>

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error "Bluetooth is not enabled! Please enable it in the Arduino ESP32 board options."
#endif

// ==============================================================================
// 1. DONGLE IDENTITY CONFIGURATION (Hybrid ID Mode)
// ==============================================================================
// Leave empty ("") to automatically derive ID from factory silicon eFuse MAC.
// Or define a human-readable identifier for demos (e.g., "RQP-POD-01", "RQP-POD-02").
#define ASSIGNED_DONGLE_ID ""

// Onboard blue LED (GPIO 2 on standard ESP32 DevKit boards)
const int LED_PIN = 2;

// Activity 7.0 External Feedback LED (GPIO 4 / D4 via 220Ω resistor)
const int EXTERNAL_LED_PIN = 4;
bool externalLedState = false;

// LoRa SPI & Control Pin Definitions (Matching Verified Hardware Wiring)
#define LORA_SCK   18
#define LORA_MISO  19
#define LORA_MOSI  23
#define LORA_SS    17
#define LORA_RST   14
#define LORA_DIO0  26

// Bluetooth Serial object
BluetoothSerial SerialBT;

// Global Device Identity & Hardware Telemetry
String dongleId = "";
String siliconMac = "";
String chipModel = "";
uint32_t cpuFreqMHz = 0;
uint8_t chipCores = 0;
String btDeviceName = "";
bool isLoraOnline = false;

// Autonomous LoRa Mesh Beacon & Relay Subsystem
unsigned long lastBeaconMillis = 0;
unsigned long nextBeaconInterval = 25000;
unsigned long lastLoraRetryMillis = 0;
#define SEEN_CACHE_SIZE 32
String seenPacketIds[SEEN_CACHE_SIZE];
int seenCacheIndex = 0;

// Real offline transmission queue. Holds up to TX_QUEUE_SIZE messages while the
// radio is down; background auto-recovery in loop() flushes them when it returns.
#define TX_QUEUE_SIZE 8
String txQueue[TX_QUEUE_SIZE];
int txQueueHead = 0;
int txQueueCount = 0;

// Deferred mesh relay. The old code blocked in delay() here, which stopped the
// node listening to the air. Now the packet is parked and sent from loop().
String pendingRelayPacket = "";
unsigned long pendingRelayAt = 0;

// Forward declarations
void initIdentity();
void initLoRaRadio();
void handleIncomingCommand(String cmd, bool fromBluetooth);
void printIdentityTelemetry(Stream &outPort, const char *transportName);
void flashLed(int times, int delayMs);
void broadcastPresenceBeacon();
void handleMeshRelay(const String &packet);
String extractPacketId(const String &packet);
void markPacketSeen(const String &id);
bool hasSeenPacket(const String &id);
bool enqueueTx(const String &payload);
bool flushTxQueue();
void servicePendingRelay();

void setup() {
  // 1. Initialize Hardware USB-OTG UART at 115200 baud
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
  pinMode(EXTERNAL_LED_PIN, OUTPUT);
  digitalWrite(EXTERNAL_LED_PIN, LOW);

  // Allow 3.3V rail, serial link, and Ra-02 crystal oscillator to settle (matching resqplug_lora_test.ino)
  delay(1500);

  Serial.println("\n=========================================================");
  Serial.println("   ⚡ ResQPlug Unified Dual-Transport + LIVE LORA v3.3    ");
  Serial.println("=========================================================");

  // 2. Read and initialize hardware identity (eFuse MAC + Script ID)
  initIdentity();

  // 3. Initialize Live Ai-Thinker Ra-02 LoRa Radio Subsystem FIRST (clean SPI bus before Bluetooth surge)
  initLoRaRadio();

  // 4. Settle time between LoRa initialization and Bluetooth RF startup
  delay(500);

  // 5. Dynamic Bluetooth device name (e.g. "ResQPlug-POD-01")
  btDeviceName = "ResQPlug-" + dongleId;
  SerialBT.begin(btDeviceName.c_str());

  // 6. Quick 3-pulse boot LED sequence
  flashLed(3, 80);

  // 7. Output boot summary to USB Serial
  Serial.print("   [ DONGLE ID   ]: "); Serial.println(dongleId);
  Serial.print("   [ SILICON MAC ]: "); Serial.println(siliconMac);
  Serial.print("   [ CHIP MODEL  ]: "); Serial.println(chipModel);
  Serial.print("   [ CPU FREQ    ]: "); Serial.print(cpuFreqMHz); Serial.println(" MHz");
  Serial.print("   [ BLUETOOTH   ]: "); Serial.print(btDeviceName); Serial.println(" (SPP READY)");
  Serial.println("   [ USB-OTG     ]: 115200 Baud (CDC/UART READY)");
  Serial.print("   [ LORA RADIO  ]: "); 
  Serial.println(isLoraOnline ? "ONLINE @ 433.00 MHz (+17 dBm PA)" : "OFFLINE / FAILED");
  Serial.println("=========================================================\n");
}

void loop() {
  // 1. Update Connection & LoRa Health LED Status
  if (!isLoraOnline) {
    // LoRa OFFLINE Warning Pattern: Rapid double-blink (two 60ms flashes every 1000ms)
    unsigned long cycle = millis() % 1000;
    bool isDoubleBlink = (cycle < 60) || (cycle >= 140 && cycle < 200);
    digitalWrite(LED_PIN, isDoubleBlink ? HIGH : LOW);
  } else if (SerialBT.hasClient()) {
    // LoRa ONLINE & Phone Bluetooth Linked: Solid ON
    digitalWrite(LED_PIN, HIGH);
  } else {
    // LoRa ONLINE & Waiting for Phone Link: Slow 1 Hz pulse
    digitalWrite(LED_PIN, ((millis() / 500) % 2) ? HIGH : LOW);
  }

  // 2. Background Auto-Recovery for LoRa Radio (retry every 5s if offline)
  if (!isLoraOnline && (millis() - lastLoraRetryMillis >= 5000)) {
    lastLoraRetryMillis = millis();
    initLoRaRadio();
    if (isLoraOnline) {
      Serial.println("[LORA]: 🎉 Recovered & Online via Background Auto-Recovery!");
      if (SerialBT.hasClient()) {
        SerialBT.println("[LORA_STATUS:ONLINE_433MHZ]");
      }
    }
  }

  // 2b. Send any message held while the radio was offline (one per pass, so a
  // full queue never stalls the main loop)
  if (isLoraOnline && txQueueCount > 0) {
    flushTxQueue();
  }

  // 2. Check for incoming data via USB-OTG Serial
  if (Serial.available() > 0) {
    String incoming = Serial.readStringUntil('\n');
    incoming.trim();
    if (incoming.length() > 0) {
      handleIncomingCommand(incoming, false);
    }
  }

  // 3. Check for incoming data via Bluetooth Serial (SPP)
  if (SerialBT.available() > 0) {
    String incoming = SerialBT.readStringUntil('\n');
    incoming.trim();
    if (incoming.length() > 0) {
      handleIncomingCommand(incoming, true);
    }
  }

  // 4. Autonomous LoRa Mesh Presence Beacon (every 25s with anti-collision jitter)
  if (isLoraOnline && (millis() - lastBeaconMillis >= nextBeaconInterval)) {
    lastBeaconMillis = millis();
    nextBeaconInterval = 25000 + random(-3000, 3000);
    broadcastPresenceBeacon();
  }

  // 5. Check for incoming LoRa Radio Packets over the air (RX)
  if (isLoraOnline) {
    int packetSize = LoRa.parsePacket();
    if (packetSize) {
      String incomingRadioPacket = "";
      while (LoRa.available()) {
        incomingRadioPacket += (char)LoRa.read();
      }
      int rssi = LoRa.packetRssi();
      float snr = LoRa.packetSnr();

      // Format incoming packet telemetry for Android App
      String rxPayload = "[RX:RSSI:" + String(rssi) + "|SNR:" + String(snr, 1) + "|DATA:" + incomingRadioPacket + "]";

      // Forward received radio message to both USB and Bluetooth phone interfaces
      Serial.println(rxPayload);
      if (SerialBT.hasClient()) {
        SerialBT.println(rxPayload);
      }

      // Quick flash LED to indicate RF packet received
      flashLed(1, 40);

      // Autonomous Store-and-Forward Mesh Relay
      handleMeshRelay(incomingRadioPacket);
    }
  }

  // 6. Transmit any relay whose 100 - 300 ms backoff has now elapsed
  servicePendingRelay();

  delay(5);
}

/**
 * Parses and responds to phone commands received over either transport.
 */
void handleIncomingCommand(String cmd, bool fromBluetooth) {
  // Flash LED rapidly during command processing
  digitalWrite(LED_PIN, !digitalRead(LED_PIN));

  if (cmd.equalsIgnoreCase("IDENTIFY") || cmd.equalsIgnoreCase("PING")) {
    // When phone links and identifies, light external LED if currently off to indicate link
    if (!externalLedState) {
      externalLedState = true;
      digitalWrite(EXTERNAL_LED_PIN, HIGH);
    }
    // Send structured hardware identity packet back to querying port
    if (fromBluetooth) {
      printIdentityTelemetry(SerialBT, "BLUETOOTH");
      Serial.print("[LOG: BT REQUESTED IDENTIFY] -> Sent to ");
      Serial.println(dongleId);
    } else {
      printIdentityTelemetry(Serial, "USB_OTG");
    }
  } else if (cmd.equalsIgnoreCase("LED:ON") || cmd.equalsIgnoreCase("LED_ON") || cmd.equalsIgnoreCase("ON")) {
    externalLedState = true;
    digitalWrite(EXTERNAL_LED_PIN, HIGH);
    String resp = "[ACTUAL_LED:ON|LORA:" + String(isLoraOnline ? "ONLINE_433MHZ" : "OFFLINE") + "|DONGLE:" + dongleId + "]";
    if (fromBluetooth) {
      SerialBT.println(resp);
    } else {
      Serial.println(resp);
    }
  } else if (cmd.equalsIgnoreCase("LED:OFF") || cmd.equalsIgnoreCase("LED_OFF") || cmd.equalsIgnoreCase("OFF")) {
    externalLedState = false;
    digitalWrite(EXTERNAL_LED_PIN, LOW);
    String resp = "[ACTUAL_LED:OFF|LORA:" + String(isLoraOnline ? "ONLINE_433MHZ" : "OFFLINE") + "|DONGLE:" + dongleId + "]";
    if (fromBluetooth) {
      SerialBT.println(resp);
    } else {
      Serial.println(resp);
    }
  } else if (cmd.equalsIgnoreCase("LED:STATUS") || cmd.equalsIgnoreCase("LED_STATUS")) {
    String resp = "[ACTUAL_LED:" + String(externalLedState ? "ON" : "OFF") + "|LORA:" + String(isLoraOnline ? "ONLINE_433MHZ" : "OFFLINE") + "|DONGLE:" + dongleId + "]";
    if (fromBluetooth) {
      SerialBT.println(resp);
    } else {
      Serial.println(resp);
    }
  } else if (cmd.equalsIgnoreCase("STATUS")) {
    String statusPayload = "[STATUS:ACTIVE|DONGLE:" + dongleId +
                           "|BT_LINKED:" + (SerialBT.hasClient() ? "YES" : "NO") +
                           "|USB_ACTIVE:YES|LORA:" + (isLoraOnline ? "ONLINE_433MHZ" : "OFFLINE") +
                           "|ACTUAL_LED:" + (externalLedState ? "ON" : "OFF") + "]";
    if (fromBluetooth) {
      SerialBT.println(statusPayload);
    } else {
      Serial.println(statusPayload);
    }
  } else if (cmd.startsWith("TX:")) {
    // Message transmission packet from Android smartphone
    String payload = cmd.substring(3);
    String pktId = extractPacketId(payload);
    if (pktId.length() > 0) {
      markPacketSeen(pktId);
    }
    String ack;

    // Transmit over real 433 MHz LoRa Radio if online
    if (isLoraOnline) {
      LoRa.beginPacket();
      LoRa.print(payload);
      LoRa.endPacket();

      ack = "[ACK:TX_LORA_SENT:" + payload + "]";
      flashLed(1, 80);
    } else if (enqueueTx(payload)) {
      // Radio offline — message is genuinely held until auto-recovery brings it back
      ack = "[ACK:TX_QUEUED:" + payload + "]";
      Serial.println("[TX_QUEUE]: held offline (" + String(txQueueCount) + "/" + String(TX_QUEUE_SIZE) + ")");
    } else {
      // Queue full — oldest entry dropped to make room
      ack = "[ACK:TX_QUEUE_FULL:" + payload + "]";
      Serial.println("[TX_QUEUE]: full, oldest dropped");
    }

    // Echo confirmation to both interfaces
    if (fromBluetooth) {
      SerialBT.println(ack);
      Serial.print("[BT -> LORA]: ");
      Serial.println(payload);
    } else {
      Serial.println(ack);
      if (SerialBT.hasClient()) {
        SerialBT.println("[USB -> LORA]: " + payload);
      }
    }
  } else {
    // Generic passthrough echo
    if (fromBluetooth) {
      Serial.print("[BT_RAW]: ");
      Serial.println(cmd);
      SerialBT.println("[ACK: " + cmd + "]");
    } else {
      Serial.print("[USB_RAW]: ");
      Serial.println(cmd);
      if (SerialBT.hasClient()) {
        SerialBT.println("[USB_RAW]: " + cmd);
      }
    }
  }
}

/**
 * Initializes the Ai-Thinker Ra-02 LoRa Radio module over SPI.
 * Uses dual-mode probe: Standard SPI -> Software CS (-1) fallback with register logging.
 */
void initLoRaRadio() {
  Serial.println("[LORA]: Probing SX1278 on SPI Bus (SCK:18, MISO:19, MOSI:23, SS:17, RST:14)...");

  // Mode 1: Standard SPI pins
  SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI, LORA_SS);
  LoRa.setPins(LORA_SS, LORA_RST, LORA_DIO0);

  // Hardware reset pulse
  pinMode(LORA_RST, OUTPUT);
  digitalWrite(LORA_RST, LOW);
  delay(20);
  digitalWrite(LORA_RST, HIGH);
  delay(50);

  // Direct SPI read of Version Register 0x42
  digitalWrite(LORA_SS, LOW);
  delayMicroseconds(10);
  SPI.transfer(0x42 & 0x7F);
  uint8_t ver1 = SPI.transfer(0x00);
  digitalWrite(LORA_SS, HIGH);
  Serial.print("[LORA]: Probe 1 (Standard CS) Reg 0x42 = 0x");
  if (ver1 < 0x10) Serial.print("0");
  Serial.println(ver1, HEX);

  if (LoRa.begin(433E6)) {
    LoRa.setTxPower(17);
    isLoraOnline = true;
    Serial.println("[LORA]: 🎉 SX1278 Radio Initialized Successfully at 433.00 MHz (+17 dBm)!");
    return;
  }

  // Mode 2: Software CS (-1 allows LoRa.h to fully control GPIO 17 without hardware CS lock)
  Serial.println("[LORA]: Probe 1 failed. Trying Probe 2 (Software CS on GPIO 17)...");
  SPI.end();
  delay(50);
  SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI, -1);
  pinMode(LORA_SS, OUTPUT);
  digitalWrite(LORA_SS, HIGH);
  LoRa.setPins(LORA_SS, LORA_RST, LORA_DIO0);

  pinMode(LORA_RST, OUTPUT);
  digitalWrite(LORA_RST, LOW);
  delay(20);
  digitalWrite(LORA_RST, HIGH);
  delay(50);

  digitalWrite(LORA_SS, LOW);
  delayMicroseconds(10);
  SPI.transfer(0x42 & 0x7F);
  uint8_t ver2 = SPI.transfer(0x00);
  digitalWrite(LORA_SS, HIGH);
  Serial.print("[LORA]: Probe 2 (Software CS) Reg 0x42 = 0x");
  if (ver2 < 0x10) Serial.print("0");
  Serial.println(ver2, HEX);

  if (LoRa.begin(433E6)) {
    LoRa.setTxPower(17);
    isLoraOnline = true;
    Serial.println("[LORA]: 🎉 SX1278 Radio Initialized Successfully with Software CS at 433.00 MHz!");
    return;
  }

  isLoraOnline = false;
  Serial.println("[LORA]: ⚠️ Radio Initialization Failed! Running in Fallback Mode.");
}

/**
 * Sends structured telemetry payload for Android app ingestion and Firebase logging.
 */
void printIdentityTelemetry(Stream &outPort, const char *transportName) {
  outPort.print("[RESQPLUG_ID:");
  outPort.print(dongleId);
  outPort.print("|MAC:");
  outPort.print(siliconMac);
  outPort.print("|CHIP:");
  outPort.print(chipModel);
  outPort.print("|CORES:");
  outPort.print(chipCores);
  outPort.print("|CPU:");
  outPort.print(cpuFreqMHz);
  outPort.print("MHz|FW:v3.3-LORA-LIVE|LORA:");
  outPort.print(isLoraOnline ? "ONLINE_433MHZ" : "OFFLINE");
  outPort.print("|ACTUAL_LED:");
  outPort.print(externalLedState ? "ON" : "OFF");
  outPort.print("|TRANSPORT:");
  outPort.print(transportName);
  outPort.println("|STATUS:LINKED]");
}

/**
 * Initializes the device identity using hybrid configuration.
 */
void initIdentity() {
  uint64_t chipid = ESP.getEfuseMac();

  // Format full factory silicon MAC address
  char macBuffer[20];
  snprintf(macBuffer, sizeof(macBuffer), "%02X:%02X:%02X:%02X:%02X:%02X",
           (uint8_t)(chipid >> 40),
           (uint8_t)(chipid >> 32),
           (uint8_t)(chipid >> 24),
           (uint8_t)(chipid >> 16),
           (uint8_t)(chipid >> 8),
           (uint8_t)chipid);
  siliconMac = String(macBuffer);

  // Read ESP32 silicon properties
  chipModel = ESP.getChipModel();
  chipCores = ESP.getChipCores();
  cpuFreqMHz = ESP.getCpuFreqMHz();

  // If ASSIGNED_DONGLE_ID is populated, use it; otherwise use silicon MAC hash
  String assigned = String(ASSIGNED_DONGLE_ID);
  assigned.trim();
  if (assigned.length() > 0) {
    dongleId = assigned;
  } else {
    char idBuffer[20];
    snprintf(idBuffer, sizeof(idBuffer), "POD-%02X%02X%02X",
             (uint8_t)(chipid >> 16), (uint8_t)(chipid >> 8), (uint8_t)chipid);
    dongleId = String(idBuffer);
  }
}

void flashLed(int times, int delayMs) {
  for (int i = 0; i < times; i++) {
    digitalWrite(LED_PIN, HIGH);
    delay(delayMs);
    digitalWrite(LED_PIN, LOW);
    delay(delayMs);
  }
}

void markPacketSeen(const String &id) {
  if (id.length() == 0) return;
  seenPacketIds[seenCacheIndex] = id;
  seenCacheIndex = (seenCacheIndex + 1) % SEEN_CACHE_SIZE;
}

bool hasSeenPacket(const String &id) {
  if (id.length() == 0) return false;
  for (int i = 0; i < SEEN_CACHE_SIZE; i++) {
    if (seenPacketIds[i].length() > 0 && seenPacketIds[i] == id) {
      return true;
    }
  }
  return false;
}

String extractPacketId(const String &packet) {
  int startIdx = packet.indexOf(':');
  if (startIdx < 0) return "";
  int endIdx = packet.indexOf('|', startIdx);
  if (endIdx < 0) {
    endIdx = packet.indexOf(']', startIdx);
  }
  if (endIdx > startIdx) {
    return packet.substring(startIdx + 1, endIdx);
  }
  return "";
}

void broadcastPresenceBeacon() {
  if (!isLoraOnline) return;
  String bcnPacket = "[BCN:" + dongleId + "|" + btDeviceName + "|CITIZEN|1]";
  LoRa.beginPacket();
  LoRa.print(bcnPacket);
  LoRa.endPacket();

  Serial.println("[BEACON_TX]: " + bcnPacket);
  if (SerialBT.hasClient()) {
    SerialBT.println("[BEACON_TX]: " + bcnPacket);
  }
}

void handleMeshRelay(const String &packet) {
  bool isMsg = packet.startsWith("[MSG:");
  bool isSos = packet.startsWith("[SOS:");
  if (!isMsg && !isSos) return;

  String pktId = extractPacketId(packet);
  if (pktId.length() == 0) {
    Serial.println("[RELAY_SKIP]: " + packet + " (no packet id)");
    return;
  }

  if (hasSeenPacket(pktId)) {
    return; // Already seen / relayed, suppress duplicates
  }
  markPacketSeen(pktId);

  int lastBracket = packet.lastIndexOf(']');
  String inner = (lastBracket > 0) ? packet.substring(1, lastBracket) : packet;

  int pipeIndices[8];
  int pipeCount = 0;
  for (int i = 0; i < inner.length() && pipeCount < 8; i++) {
    if (inner.charAt(i) == '|') {
      pipeIndices[pipeCount++] = i;
    }
  }

  int hopVal = 1;
  int hopStart = -1;
  int hopEnd = -1;

  if (isMsg && pipeCount >= 5) {
    hopStart = pipeIndices[3] + 1;
    hopEnd = pipeIndices[4];
  } else if (isSos && pipeCount >= 4) {
    hopStart = pipeIndices[2] + 1;
    hopEnd = pipeIndices[3];
  }

  if (hopStart < 0 || hopEnd <= hopStart) {
    Serial.println("[RELAY_SKIP]: " + packet + " (no parseable hop field)");
    return;
  }

  hopVal = inner.substring(hopStart, hopEnd).toInt();
  if (hopVal < 1) hopVal = 1;

  if (hopVal >= 3) {
    Serial.println("[RELAY_SKIP]: " + packet + " (hop " + String(hopVal) + " >= max 3)");
    return;
  }

  int newHop = hopVal + 1;
  String relayed = "[" + inner.substring(0, hopStart) + String(newHop) + inner.substring(hopEnd) + "]";

  // Random backoff jitter (100 - 300 ms) to avoid collision with the transmitting
  // node. Deferred rather than blocking, so this node keeps listening to the air
  // while it waits its turn to retransmit.
  pendingRelayPacket = relayed;
  pendingRelayAt = millis() + (unsigned long)random(100, 300);
  Serial.println("[RELAY_QUEUED]: " + relayed);
}

/**
 * Parks a relaying node between 100-300 ms before it retransmits. Called from
 * loop() so the node is never deaf to parsePacket() while waiting.
 */
void servicePendingRelay() {
  if (pendingRelayPacket.length() == 0) return;
  if (!isLoraOnline) return;
  if ((long)(millis() - pendingRelayAt) < 0) return; // backoff not elapsed yet

  String relayed = pendingRelayPacket;
  pendingRelayPacket = "";

  LoRa.beginPacket();
  LoRa.print(relayed);
  LoRa.endPacket();

  flashLed(2, 50);
  Serial.println("[RELAY_TX]: " + relayed);
  if (SerialBT.hasClient()) {
    SerialBT.println("[RELAY_TX]: " + relayed);
  }
}

/**
 * Holds a message while the radio is offline. Returns false if the queue is
 * already full (the caller then reports the drop back to the phone).
 */
bool enqueueTx(const String &payload) {
  bool wasFull = (txQueueCount >= TX_QUEUE_SIZE);
  if (wasFull) {
    // Full — overwrite the oldest slot to make room
    txQueue[txQueueHead] = "";
  } else {
    txQueueCount++;
  }
  txQueue[txQueueHead] = payload;
  txQueueHead = (txQueueHead + 1) % TX_QUEUE_SIZE;
  return !wasFull;
}

/**
 * Sends the oldest queued message once the radio is back. One entry per call so
 * a full queue never blocks the main loop for long.
 */
bool flushTxQueue() {
  if (!isLoraOnline) return false;
  if (txQueueCount <= 0) return false;

  int tail = (txQueueHead - txQueueCount + TX_QUEUE_SIZE) % TX_QUEUE_SIZE;
  String payload = txQueue[tail];
  txQueue[tail] = "";
  txQueueCount--;

  LoRa.beginPacket();
  LoRa.print(payload);
  LoRa.endPacket();
  flashLed(1, 80);

  Serial.println("[TX_QUEUE_FLUSH]: " + payload);
  if (SerialBT.hasClient()) {
    SerialBT.println("[ACK:TX_FLUSHED:" + payload + "]");
  }
  return true;
}

