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
#define ASSIGNED_DONGLE_ID "RQP-POD-01"

// Onboard blue LED (GPIO 2 on standard ESP32 DevKit boards)
const int LED_PIN = 2;

// Activity 7.0 External Feedback LED (GPIO 4 / D4 via 220Ω resistor)
const int EXTERNAL_LED_PIN = 4;
bool externalLedState = false;

// LoRa SPI & Control Pin Definitions (Matching Verified Hardware Wiring)
#define LORA_SCK   18
#define LORA_MISO  19
#define LORA_MOSI  23
#define LORA_SS    5
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

// Forward declarations
void initIdentity();
void initLoRaRadio();
void handleIncomingCommand(String cmd, bool fromBluetooth);
void printIdentityTelemetry(Stream &outPort, const char *transportName);
void flashLed(int times, int delayMs);

void setup() {
  // 1. Initialize Hardware USB-OTG UART at 115200 baud
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
  pinMode(EXTERNAL_LED_PIN, OUTPUT);
  digitalWrite(EXTERNAL_LED_PIN, LOW);

  // 2. Read and initialize hardware identity (eFuse MAC + Script ID)
  initIdentity();

  // 3. Dynamic Bluetooth device name (e.g. "ResQPlug-POD-01")
  btDeviceName = "ResQPlug-" + dongleId;
  SerialBT.begin(btDeviceName.c_str());

  // 4. Initialize Live Ai-Thinker Ra-02 LoRa Radio Subsystem
  initLoRaRadio();

  // 5. Quick 3-pulse boot LED sequence
  flashLed(3, 80);

  // 6. Output boot banner to USB Serial
  Serial.println();
  Serial.println("=========================================================");
  Serial.println("   ⚡ ResQPlug Unified Dual-Transport + LIVE LORA v3.3    ");
  Serial.println("=========================================================");
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
  // 1. Update Connection LED Status
  // If Bluetooth smartphone is actively connected, keep LED solid ON.
  // Otherwise, pulse slowly (1 Hz) to indicate standing by for phone linking.
  if (SerialBT.hasClient()) {
    digitalWrite(LED_PIN, HIGH);
  } else {
    digitalWrite(LED_PIN, ((millis() / 500) % 2) ? HIGH : LOW);
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

  // 4. Check for incoming LoRa Radio Packets over the air (RX)
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
      flashLed(2, 40);
    }
  }

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
    String ack;

    // Transmit over real 433 MHz LoRa Radio if online
    if (isLoraOnline) {
      LoRa.beginPacket();
      LoRa.print(payload);
      LoRa.endPacket();

      ack = "[ACK:TX_LORA_SENT:" + payload + "]";
      flashLed(1, 80);
    } else {
      ack = "[ACK:TX_QUEUED_SIMULATED:" + payload + "]";
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
 */
void initLoRaRadio() {
  // Start Hardware SPI bus
  SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI, LORA_SS);

  // Set LoRa driver pins
  LoRa.setPins(LORA_SS, LORA_RST, LORA_DIO0);

  // Hardware reset pulse
  pinMode(LORA_RST, OUTPUT);
  digitalWrite(LORA_RST, LOW);
  delay(20);
  digitalWrite(LORA_RST, HIGH);
  delay(50);

  // Initialize at 433 MHz (Philippine Emergency Band)
  if (LoRa.begin(433E6)) {
    LoRa.setTxPower(17);
    isLoraOnline = true;
    Serial.println("[LORA]: SX1278 Radio Initialized Successfully at 433.00 MHz (+17 dBm)!");
  } else {
    isLoraOnline = false;
    Serial.println("[LORA]: ⚠️ Radio Initialization Failed! Running in Fallback Mode.");
  }
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
    char idBuffer[24];
    snprintf(idBuffer, sizeof(idBuffer), "RQP-ESP32-%02X%02X%02X",
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
