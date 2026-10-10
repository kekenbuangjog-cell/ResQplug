# ELDROID Activity 7.0: Android + Firebase + ESP32 External LED Control System
*(Complete MVP Architecture, Shopping List, Circuit Schematics, and Code Guide)*

---

## 1. Project Profile & Objective

* **Activity Name:** Android + Firebase + ESP32 External LED Control System
* **Architecture:** Strictly **Model-View-Presenter (MVP)**
* **Deliverable:** Working hardware prototype + Android app + Firebase backend with real-time bidirectional feedback.
* **Core Communication Flow:**
  $$\text{Android App (Command: ON/OFF)} \longrightarrow \text{Firebase} \longrightarrow \text{ESP32} \longrightarrow \text{External LED}$$
  $$\text{ESP32 (Actual Status: ON/OFF + Heartbeat)} \longrightarrow \text{Firebase} \longrightarrow \text{Android App}$$

---

## 2. Complete Component & Shopping Checklist

### A. Items You Already Have on Your Desk
1. **ESP32 Development Board** (30-pin ESP-WROOM-32 with Type-C).
2. **Double Breadboard** (Two MB-102 breadboards snapped together).
3. **USB Type-C Data Cable** (for power and flashing).
4. *(Your Ra-02 LoRa module can stay on the breadboard—this activity will NOT interfere with it).*

### B. What You Need to Buy (Total Budget: ~₱100 – ₱150 locally)
| Item | Exact Specification | Qty | Purpose | Where to Buy in Cebu |
| :--- | :--- | :---: | :--- | :--- |
| **Male-to-Male (M-M) Dupont Jumper Wires** | 20cm, 40-pin ribbon cable | 1 pack | Painless plug-and-play wiring for both this LED activity and your LoRa setup | **AC/DC Radio Supply** (Colon) / E-Hub / Shopee |
| **5mm External LEDs** | Standard 5mm (Red, Green, or Blue) | 5 pcs | The physical output indicator for the demo | AC/DC Radio Supply / local hobby store |
| **Current-Limiting Resistor** | **220 Ω or 330 Ω**, 1/4 Watt (Through-Hole) | 5–10 pcs | Prevents burning out the LED and ESP32 GPIO pin | AC/DC Radio Supply *(Color code: Red-Red-Brown for 220Ω, or Orange-Orange-Brown for 330Ω)* |

---

## 3. Hardware Circuit & Wiring Schematic

### Safe GPIO Pin Selection
* We choose **`GPIO 4`** (labeled `D4` or `4` on your ESP32).
* **Why GPIO 4?**
  * It is a safe general-purpose output pin.
  * It is **not** a strapping pin (unlike GPIO 0, 2, 5, 12, 15 which can prevent ESP32 from booting).
  * It **does not conflict** with any of your LoRa pins (`GPIO 17, 14, 18, 19, 23, 26`).

### Circuit Diagram

```text
    ESP32 Board                             Breadboard
   +------------+
   |            |
   |   GPIO 4   |----------------+
   |            |                |
   |            |                v
   |            |        [ 220Ω or 330Ω Resistor ]
   |            |                |
   |            |                v
   |            |        [ Anode (+) : Long Leg of LED ]
   |            |        [ LED Bulb ]
   |            |        [ Cathode (-): Short Flat Leg ]
   |            |                |
   |    GND     |<---------------+
   +------------+
```

### Understanding the Legs of the LED:
* **Anode (+):** The **longer leg** connects to the **resistor** coming from **GPIO 4**.
* **Cathode (-):** The **shorter leg** (with the flat edge on the bulb) connects to **ESP32 GND**.

> **Ohm's Law Justification for Your Teacher:**
> The ESP32 GPIO outputs $V_{out} = 3.3\text{V}$. A standard Red LED forward voltage is $V_f \approx 2.0\text{V}$.
> With a $220\ \Omega$ resistor:
> $$I = \frac{V_{out} - V_f}{R} = \frac{3.3\text{V} - 2.0\text{V}}{220\ \Omega} \approx 5.9\text{ mA}$$
> This is well within the ESP32 GPIO safe limit ($12\text{ mA}$ max) and safe for the LED.

---

## 4. Firebase Database Schema

We use Firebase Firestore (or Realtime Database). The document path is:
`activities/esp32_led_control`

```json
{
  "command": "OFF",              // Written by Android: "ON" or "OFF"
  "command_requested_at": 1726910000,
  "actual_status": "OFF",        // Reported back by ESP32: "ON" or "OFF"
  "device_status": "online",     // "online" or "offline"
  "last_seen": 1726910005,       // ESP32 Heartbeat Unix timestamp (seconds)
  "updated_by": "student@cit.edu"// Authenticated user email
}
```

---

## 5. Software Architecture: Model-View-Presenter (MVP)

The deliverable strictly forbids placing business and Firebase logic inside Activities or Fragments.

```
       [ USER ]
          │
          ▼ (Clicks Button)
   ┌──────────────┐          Calls Presenter         ┌───────────────────┐
   │     VIEW     │ ───────────────────────────────► │     PRESENTER     │
   │ (LedActivity)│                                  │   (LedPresenter)  │
   │              │ ◄─────────────────────────────── │                   │
   └──────────────┘          Updates UI              └─────────┬─────────┘
                                                               │
                                             Reads/Writes Data │
                                                               ▼
                                                     ┌───────────────────┐
                                                     │  MODEL/REPOSITORY │
                                                     │  (LedRepository)  │
                                                     └─────────┬─────────┘
                                                               │
                                                               ▼
                                                      [ FIREBASE CLOUD ]
```

### Component Breakdown
1. **Contract (`LedContract.kt`):** Interfaces defining what the View, Presenter, and Model can do.
2. **Model (`LedRepository.kt`):**
   * Authenticates user via Firebase Authentication (`FirebaseAuth`).
   * Writes `"ON"` or `"OFF"` command to Firebase.
   * Listens in real-time to changes in `actual_status` and `last_seen`.
3. **Presenter (`LedPresenter.kt`):**
   * Receives `onTurnOnClicked()` and `onTurnOffClicked()` from View.
   * Compares `last_seen` timestamp against current time: if $> 15\text{ seconds}$ old $\rightarrow$ informs View that ESP32 is **OFFLINE/UNAVAILABLE**.
   * Coordinates loading states, success messages, and errors.
4. **View (`LedActivity.kt`):**
   * Displays buttons (ON/OFF), actual status badge, device online/offline banner, and user email.
   * Pure UI—zero Firebase references.

---

## 6. Complete ESP32 Arduino Firmware

Create this file in Arduino IDE: `firmware/esp32_firebase_led/esp32_firebase_led.ino`

```cpp
/*
 * ==============================================================================
 *  ELDROID Activity 7.0: ESP32 Firebase External LED Controller
 * ==============================================================================
 *  Pin: GPIO 4 -> 220Ω/330Ω Resistor -> LED Anode (+) -> Cathode (-) -> GND
 * ==============================================================================
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

// --- Wi-Fi Credentials ---
const char* WIFI_SSID = "YOUR_WIFI_NAME";
const char* WIFI_PASS = "YOUR_WIFI_PASSWORD";

// --- Firebase Firestore REST URL ---
// Format: https://firestore.googleapis.com/v1/projects/[PROJECT_ID]/databases/(default)/documents/activities/esp32_led_control
const char* FIRESTORE_URL = "https://firestore.googleapis.com/v1/projects/resqplug-6aa8c/databases/(default)/documents/activities/esp32_led_control";

const int EXTERNAL_LED_PIN = 4; // Safe output pin
String currentLedState = "OFF";
unsigned long lastHeartbeatMillis = 0;
const unsigned long HEARTBEAT_INTERVAL = 10000; // 10 seconds

void setup() {
  Serial.begin(115200);
  pinMode(EXTERNAL_LED_PIN, OUTPUT);
  digitalWrite(EXTERNAL_LED_PIN, LOW);

  connectToWiFi();
}

void loop() {
  // Reconnect Wi-Fi automatically if lost
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("⚠️ Wi-Fi lost! Attempting auto-reconnect...");
    connectToWiFi();
    return;
  }

  // Poll Firebase for commands every 2 seconds
  fetchFirebaseCommand();

  // Send Heartbeat every 10 seconds
  if (millis() - lastHeartbeatMillis >= HEARTBEAT_INTERVAL) {
    lastHeartbeatMillis = millis();
    sendHeartbeatToFirebase();
  }

  delay(2000);
}

void connectToWiFi() {
  Serial.print("Connecting to Wi-Fi: ");
  Serial.println(WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(500);
    Serial.print(".");
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n✅ Wi-Fi Connected! IP: " + WiFi.localIP().toString());
    sendHeartbeatToFirebase();
  } else {
    Serial.println("\n❌ Wi-Fi Connection Failed. Will retry in loop.");
  }
}

void fetchFirebaseCommand() {
  HTTPClient http;
  http.begin(FIRESTORE_URL);
  int httpCode = http.GET();

  if (httpCode == 200) {
    String payload = http.getString();
    StaticJsonDocument<1024> doc;
    DeserializationError error = deserializeJson(doc, payload);

    if (!error) {
      const char* cmd = doc["fields"]["command"]["stringValue"];
      if (cmd != nullptr) {
        String commandStr = String(cmd);
        if (commandStr != currentLedState) {
          executeCommand(commandStr);
        }
      }
    }
  }
  http.end();
}

void executeCommand(String command) {
  if (command == "ON") {
    digitalWrite(EXTERNAL_LED_PIN, HIGH);
    currentLedState = "ON";
    Serial.println("💡 External LED turned ON");
  } else {
    digitalWrite(EXTERNAL_LED_PIN, LOW);
    currentLedState = "OFF";
    Serial.println("⚫ External LED turned OFF");
  }
  reportActualStatusToFirebase(currentLedState);
}

void reportActualStatusToFirebase(String state) {
  HTTPClient http;
  http.begin(FIRESTORE_URL + String("?updateMask.fieldPaths=actual_status"));
  http.addHeader("Content-Type", "application/json");

  String jsonBody = "{\"fields\":{\"actual_status\":{\"stringValue\":\"" + state + "\"}}}";
  http.sendRequest("PATCH", jsonBody);
  http.end();
}

void sendHeartbeatToFirebase() {
  HTTPClient http;
  http.begin(FIRESTORE_URL + String("?updateMask.fieldPaths=device_status&updateMask.fieldPaths=last_seen"));
  http.addHeader("Content-Type", "application/json");

  long currentEpoch = (long)(millis() / 1000);
  String jsonBody = "{\"fields\":{\"device_status\":{\"stringValue\":\"online\"},\"last_seen\":{\"integerValue\":\"" + String(currentEpoch) + "\"}}}";
  http.sendRequest("PATCH", jsonBody);
  http.end();
}
```

---

## 7. Teacher Demonstration & Defense Checklist

When your group demonstrates this activity, you must be prepared to demonstrate and explain these 8 points:

| # | Demonstration Requirement | How to Demonstrate it |
| :---: | :--- | :--- |
| **1** | **User Authentication** | Log into the Android app with an email/password. Show that unauthenticated users cannot access controls. |
| **2** | **Turn LED ON** | Tap **`[ TURN ON ]`** in app $\rightarrow$ App says "Command: ON" $\rightarrow$ External LED on breadboard lights up $\rightarrow$ App updates "Actual Status: ON". |
| **3** | **Turn LED OFF** | Tap **`[ TURN OFF ]`** in app $\rightarrow$ External LED turns off $\rightarrow$ App updates "Actual Status: OFF". |
| **4** | **Show Firebase Console** | Keep Firebase Console open on a laptop screen side-by-side. Point out how `command` and `actual_status` change in real time. |
| **5** | **Wi-Fi Disconnect Scenario** | Unplug the Wi-Fi router or hotspot $\rightarrow$ Wait 15 seconds $\rightarrow$ App detects no heartbeat and displays **`[ ⚠️ ESP32: OFFLINE / UNAVAILABLE ]`**. |
| **6** | **Auto-Reconnection** | Turn Wi-Fi hotspot back on $\rightarrow$ ESP32 reconnects automatically $\rightarrow$ App switches back to **`[ 🟢 ESP32: ONLINE ]`**. |
| **7** | **Explain Circuit** | Point to `GPIO 4`, the $220\ \Omega$ current-limiting resistor, the long leg (Anode), short leg (Cathode), and `GND`. State Ohm's law. |
| **8** | **Explain MVP Architecture** | Explain that `LedActivity` (View) contains NO Firebase code. It only communicates through `LedPresenter`, which handles operations via `LedRepository` (Model). |
