/*
 * ==========================================================
 *  ResQPlug ESP32 Hardware Identification Firmware v1.0
 * ==========================================================
 *  Project : ResQPlug Emergency Mesh Radio Dongle
 *  Target  : ESP32-WROOM-32 / NodeMCU-32S / ESP32 Dev Module
 *  BaudRate: 115200 bps
 * ==========================================================
 */

// Onboard LED pin (GPIO 2 on most ESP32 DevKit boards)
#define LED_PIN 2

String deviceId = "";
String chipModel = "";
uint32_t cpuFreq = 0;
uint8_t chipCores = 0;

void setup() {
  // Initialize USB Serial communication at 115200 baud
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);

  // Read the ESP32 chip's unique silicon MAC address
  uint64_t chipid = ESP.getEfuseMac();
  char idBuffer[24];
  snprintf(idBuffer, sizeof(idBuffer), "RQP-ESP32-%04X", (uint16_t)(chipid >> 32));
  deviceId = String(idBuffer);

  chipModel = ESP.getChipModel();
  chipCores = ESP.getChipCores();
  cpuFreq = ESP.getCpuFreqMHz();

  // 3-blink startup sequence
  for (int i = 0; i < 3; i++) {
    digitalWrite(LED_PIN, HIGH);
    delay(100);
    digitalWrite(LED_PIN, LOW);
    delay(100);
  }

  // Turn LED solid on to indicate running firmware
  digitalWrite(LED_PIN, HIGH);

  // Print initial identification payload
  printDeviceIdentification();
}

void loop() {
  // Listen for commands from Android phone via USB OTG
  if (Serial.available() > 0) {
    String incoming = Serial.readStringUntil('\n');
    incoming.trim();

    if (incoming.equalsIgnoreCase("IDENTIFY") || incoming.equalsIgnoreCase("PING")) {
      printDeviceIdentification();
    } else if (incoming.equalsIgnoreCase("STATUS")) {
      Serial.print("STATUS:ACTIVE,NODE:");
      Serial.println(deviceId);
    } else if (incoming.startsWith("TX:")) {
      // Future LoRa Radio transmission packet hook
      Serial.print("ACK:RECEIVED_FOR_TX:");
      Serial.println(incoming.substring(3));
    }
  }

  delay(200);
}

void printDeviceIdentification() {
  Serial.println();
  Serial.println("========================================");
  Serial.println("[ ⚡ RESQPLUG ESP32 DONGLE FIRMWARE v1.0 ]");
  Serial.print("[ NODE ID   ]: "); Serial.println(deviceId);
  Serial.print("[ CHIP MODEL]: "); Serial.println(chipModel);
  Serial.print("[ CHIP CORES]: "); Serial.println(chipCores);
  Serial.print("[ CPU FREQ  ]: "); Serial.print(cpuFreq); Serial.println(" MHz");
  Serial.println("[ USB LINK  ]: 115200 BAUD ACTIVE");
  Serial.println("[ RF STATUS ]: READY_FOR_LORA_MODULE");
  Serial.println("========================================");
}
