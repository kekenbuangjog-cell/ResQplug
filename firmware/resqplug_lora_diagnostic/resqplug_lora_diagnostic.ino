/*
 * ==============================================================================
 *  ResQPlug Ai-Thinker Ra-02 (SX1278) Hardware Diagnostic Sketch
 * ==============================================================================
 *  Target  : ESP32-WROOM-32 / NodeMCU-32S
 *  Wiring  :
 *    - Ra-02 Left [1] GND   -> ESP32 GND
 *    - Ra-02 Left [3] 3.3V  -> ESP32 3V3 (Strictly 3.3V!)
 *    - Ra-02 Left [4] RST   -> ESP32 GPIO 14
 *    - Ra-02 Left [5] DIO0  -> ESP32 GPIO 26
 *    - Ra-02 Right [2] NSS  -> ESP32 GPIO 17
 *    - Ra-02 Right [3] MOSI -> ESP32 GPIO 23
 *    - Ra-02 Right [4] MISO -> ESP32 GPIO 19
 *    - Ra-02 Right [5] SCK  -> ESP32 GPIO 18
 * ==============================================================================
 */

#include <SPI.h>

// ESP32 SPI Pin definitions matching our wiring
#define PIN_SCK   18
#define PIN_MISO  19
#define PIN_MOSI  23
#define PIN_NSS   17
#define PIN_RST   14
#define PIN_DIO0  26

// Onboard Blue Status LED (GPIO 2 on standard ESP32 boards)
#define LED_PIN    2

// SX1278 Registers
#define REG_OP_MODE     0x01
#define REG_FRF_MSB     0x06
#define REG_VERSION     0x42
#define REG_SYNC_WORD   0x39

// 1 MHz SPI clock (Mode 0, MSBFIRST)
SPISettings loraSpiSettings(1000000, MSBFIRST, SPI_MODE0);

uint8_t readRegister(uint8_t address) {
  SPI.beginTransaction(loraSpiSettings);
  digitalWrite(PIN_NSS, LOW);
  delayMicroseconds(10);
  SPI.transfer(address & 0x7F); // Bit 7 = 0 for read
  uint8_t value = SPI.transfer(0x00);
  delayMicroseconds(10);
  digitalWrite(PIN_NSS, HIGH);
  SPI.endTransaction();
  return value;
}

void writeRegister(uint8_t address, uint8_t value) {
  SPI.beginTransaction(loraSpiSettings);
  digitalWrite(PIN_NSS, LOW);
  delayMicroseconds(10);
  SPI.transfer(address | 0x80); // Bit 7 = 1 for write
  SPI.transfer(value);
  delayMicroseconds(10);
  digitalWrite(PIN_NSS, HIGH);
  SPI.endTransaction();
}

void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);

  // Initialize and hold NSS HIGH (unselected)
  pinMode(PIN_NSS, OUTPUT);
  digitalWrite(PIN_NSS, HIGH);

  // Hardware Reset pulse: hold LOW for 10ms, then drive HIGH and keep it HIGH
  pinMode(PIN_RST, OUTPUT);
  digitalWrite(PIN_RST, LOW);
  delay(10);
  digitalWrite(PIN_RST, HIGH);
  delay(50); // 50ms crystal stabilization

  // Start Hardware SPI once without hardware CS takeover (-1 allows manual digitalWrite on GPIO 17)
  SPI.begin(PIN_SCK, PIN_MISO, PIN_MOSI, -1);

  // Ensure NSS is set as output under our software control
  pinMode(PIN_NSS, OUTPUT);
  digitalWrite(PIN_NSS, HIGH);

  Serial.println("\n=========================================================");
  Serial.println("   ⚡ ResQPlug Ra-02 Clean SPI Diagnostic v3.0           ");
  Serial.println("=========================================================");
  Serial.println("   Checking SX1278 Registers at 1 MHz (Mode 0)...        ");
  Serial.println("=========================================================\n");
}

void loop() {
  digitalWrite(LED_PIN, HIGH);

  // --- Probe 1: Standard Pins (MOSI=23, MISO=19) ---
  SPI.end();
  SPI.begin(18, 19, 23, -1);
  pinMode(PIN_NSS, OUTPUT);
  digitalWrite(PIN_NSS, HIGH);
  delay(10);

  uint8_t ver1 = readRegister(REG_VERSION);
  uint8_t op1 = readRegister(REG_OP_MODE);

  // Write test byte 0x55 to SyncWord register (0x39), then read back
  writeRegister(REG_SYNC_WORD, 0x55);
  uint8_t sync1 = readRegister(REG_SYNC_WORD);

  // --- Probe 2: Swapped Pins (MOSI=19, MISO=23) ---
  SPI.end();
  SPI.begin(18, 23, 19, -1);
  pinMode(PIN_NSS, OUTPUT);
  digitalWrite(PIN_NSS, HIGH);
  delay(10);

  uint8_t ver2 = readRegister(REG_VERSION);
  writeRegister(REG_SYNC_WORD, 0x55);
  uint8_t sync2 = readRegister(REG_SYNC_WORD);

  Serial.println("---------------------------------------------------------");
  Serial.print("[Standard: MOSI=23, MISO=19] Ver: 0x");
  if (ver1 < 0x10) Serial.print("0"); Serial.print(ver1, HEX);
  Serial.print(" | OpMode: 0x");
  if (op1 < 0x10) Serial.print("0"); Serial.print(op1, HEX);
  Serial.print(" | SyncTest: 0x");
  if (sync1 < 0x10) Serial.print("0"); Serial.println(sync1, HEX);

  Serial.print("[Swapped:  MOSI=19, MISO=23] Ver: 0x");
  if (ver2 < 0x10) Serial.print("0"); Serial.print(ver2, HEX);
  Serial.print(" | SyncTest: 0x");
  if (sync2 < 0x10) Serial.print("0"); Serial.println(sync2, HEX);

  if (ver1 == 0x12 || sync1 == 0x55) {
    Serial.println("==>  🎉 [ 100% SUCCESS: Ra-02 WORKING ON STANDARD PINS! ]");
  } else if (ver2 == 0x12 || sync2 == 0x55) {
    Serial.println("==>  🎉 [ 100% SUCCESS: Ra-02 WORKING ON SWAPPED PINS! ]");
  } else {
    Serial.println("==>  ⚠️ Got 0x00. Check: Is Yellow RST wire plugged into Left Pin 11 (D14)?");
  }

  digitalWrite(LED_PIN, LOW);
  delay(2000);
}
