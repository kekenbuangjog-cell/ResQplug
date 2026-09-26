/*
 * ==============================================================================
 *  ELDROID Activity 7.0: Milestone 1 - Local External LED Blink Test
 * ==============================================================================
 *  Circuit:
 *    ESP32 GPIO 4 -> 220Ω Resistor -> LED Anode (+) -> Cathode (-) -> GND
 * ==============================================================================
 */

const int EXTERNAL_LED_PIN = 4; // Safe output GPIO 4

void setup() {
  Serial.begin(115200);
  pinMode(EXTERNAL_LED_PIN, OUTPUT);

  Serial.println("\n=========================================================");
  Serial.println("   ⚡ ELDROID Activity 7.0: Local External LED Blink Test ");
  Serial.println("=========================================================");
  Serial.println("   Testing GPIO 4 output...");
  Serial.println("=========================================================\n");
}

void loop() {
  // Turn External LED ON
  digitalWrite(EXTERNAL_LED_PIN, HIGH);
  Serial.println("💡 External LED: [ ON ]");
  delay(1000); // 1 second ON

  // Turn External LED OFF
  digitalWrite(EXTERNAL_LED_PIN, LOW);
  Serial.println("⚫ External LED: [ OFF ]");
  delay(1000); // 1 second OFF
}
