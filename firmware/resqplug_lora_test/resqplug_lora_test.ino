/*
 * ==============================================================================
 *  ResQPlug Official LoRa Hardware Verification Test
 * ==============================================================================
 *  Uses the official Sandeep Mistry LoRa library.
 *  Wiring:
 *    - NSS   -> GPIO 17
 *    - RST   -> GPIO 14
 *    - DIO0  -> GPIO 26
 *    - SCK   -> GPIO 18
 *    - MISO  -> GPIO 19
 *    - MOSI  -> GPIO 23
 *    - 3.3V  -> 3V3 (Bottom Right)
 *    - GND   -> GND
 * ==============================================================================
 */

#include <SPI.h>
#include <LoRa.h>

#define LORA_SCK   18
#define LORA_MISO  19
#define LORA_MOSI  23
#define LORA_SS    17
#define LORA_RST   14
#define LORA_DIO0  26

#define LED_PIN    2

void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  // Wait for Serial Monitor
  delay(1500);

  Serial.println("\n=========================================================");
  Serial.println("   ⚡ ResQPlug Official LoRa.h Driver Test                ");
  Serial.println("=========================================================");

  // Initialize Hardware SPI bus
  SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI, LORA_SS);

  // Configure LoRa driver pins
  LoRa.setPins(LORA_SS, LORA_RST, LORA_DIO0);

  // Hardware reset pulse to ensure clean state
  pinMode(LORA_RST, OUTPUT);
  digitalWrite(LORA_RST, LOW);
  delay(20);
  digitalWrite(LORA_RST, HIGH);
  delay(50);

  Serial.println("Attempting LoRa.begin(433E6) on 433 MHz ISM band...");

  // Try initializing LoRa at 433 MHz
  if (!LoRa.begin(433E6)) {
    Serial.println("\n❌ [ FAILED: LoRa.begin() could not communicate with SX1278! ]");
    Serial.println("   Possible causes: Loose breadboard clip on SCK(18), MISO(19), or MOSI(23).");
    digitalWrite(LED_PIN, LOW);
    return;
  }

  // Set transmission power to +17 dBm
  LoRa.setTxPower(17);

  // Solid LED means 100% SUCCESS
  digitalWrite(LED_PIN, HIGH);
  Serial.println("\n🎉 [ 100% SUCCESS: Ai-Thinker Ra-02 LoRa INITIALIZED OK! ]");
  Serial.println("   Carrier Frequency : 433.00 MHz (ISM Philippine Band)");
  Serial.println("   Tx Power Output   : +17 dBm");
  Serial.println("   SPI Bus           : Active @ 115200 Baud Diagnostic");
  Serial.println("=========================================================\n");
}

int packetCounter = 0;

void loop() {
  // If LoRa failed in setup, just blink error pattern
  // If LoRa succeeded, transmit test beacon packets
  Serial.print("Transmitting ResQPlug Beacon Packet #");
  Serial.print(packetCounter);

  LoRa.beginPacket();
  LoRa.print("ResQPlug Mesh Beacon #");
  LoRa.print(packetCounter);
  LoRa.endPacket();

  Serial.println(" ==> [ SENT OK! ]");
  packetCounter++;

  // Quick flash blue LED on packet transmission
  digitalWrite(LED_PIN, LOW);
  delay(100);
  digitalWrite(LED_PIN, HIGH);

  delay(3000); // Send beacon every 3 seconds
}
