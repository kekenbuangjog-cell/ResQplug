/*
  ResQPlug ESP32 Wireless Transceiver Firmware v3.0
  Hardware: MLE05196 Type-C 30-Pin ESP32 Dev Board (ESP-WROOM-32)
  Protocol: Classic Bluetooth Serial (SPP)
  Default Baud Rate: 115200 bps
*/

#include "BluetoothSerial.h"

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run make menuconfig and enable it
#endif

BluetoothSerial SerialBT;

// Built-in blue LED on ESP32 DevKit (GPIO 2)
const int LED_PIN = 2;

void setup() {
  // Initialize hardware UART for debugging / LoRa module
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  // Initialize Bluetooth Serial with device name
  // This name will appear on Android when scanning for Bluetooth devices
  SerialBT.begin("ResQPlug-Radio-01");

  Serial.println("\n==================================================");
  Serial.println("   ResQPlug ESP32 Bluetooth Transceiver v3.0      ");
  Serial.println("   Device Name: ResQPlug-Radio-01                 ");
  Serial.println("   Status     : WAITING FOR SMARTPHONE PAIRING    ");
  Serial.println("==================================================\n");
}

void loop() {
  // Check if smartphone app is actively connected to Bluetooth
  if (SerialBT.hasClient()) {
    // Solid blue LED indicates active smartphone connection
    digitalWrite(LED_PIN, HIGH);
  } else {
    // Slow blink (1 Hz) indicates waiting for connection
    digitalWrite(LED_PIN, (millis() / 500) % 2);
  }

  // Forward incoming data from Smartphone over Bluetooth to Serial (or LoRa radio)
  if (SerialBT.available()) {
    String incomingMsg = SerialBT.readStringUntil('\n');
    incomingMsg.trim();
    if (incomingMsg.length() > 0) {
      Serial.print("[BT -> MESH]: ");
      Serial.println(incomingMsg);

      // Echo confirmation back to phone
      SerialBT.println("[ACK: " + incomingMsg + "]");
    }
  }

  // Forward incoming data from Hardware Serial to Smartphone over Bluetooth
  if (Serial.available()) {
    char c = Serial.read();
    SerialBT.write(c);
  }

  delay(20);
}
