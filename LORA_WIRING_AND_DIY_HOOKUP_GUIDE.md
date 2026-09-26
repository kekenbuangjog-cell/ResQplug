# ResQPlug: Ai-Thinker Ra-02 LoRa & ESP32 DIY Wiring Guide
*(Zero-Solder / Direct Wire-Wrap Method Using 22 AWG Stranded Wire)*

## 1. Overview & Strategy

This guide provides the exact procedure to connect your **Ai-Thinker Ra-02 LoRa module (SX1278 433 MHz)** directly to your **ESP32 DevKit** using:
* **22 AWG Flexible Stranded Wire** (5 Colors: Red, Black, Yellow, Blue, Green)
* **Scissors**
* **Electrical Tape**

### Why We Connect Directly (Bypassing the Breadboard):
Standard 30-pin ESP32 boards are 0.9–1.0 inches wide and do not leave open pin holes on standard breadboards. By connecting the wires **directly from the ESP32 header pins to the Ra-02 header pins**, you eliminate the breadboard width issue completely, with zero risk of bent pins.

---

## 2. Pinout & Color-Coded Connection Map

Out of the 16 total pins on the Ra-02 breakout board, **only 8 wires are needed**. The remaining pins (`DIO1`–`DIO5`, extra `GND`s) are left completely disconnected.

```text
                     [ ANTENNA IPEX (Snap Antenna Here First!) ]
 
       (LEFT HEADER)                                 (RIGHT HEADER)
  --------------------------                    --------------------------
  [1] GND   ---> ESP32 GND                      [1] GND  (Skip)
  [2] GND   (Skip)                              [2] NSS  ---> ESP32 GPIO 5
  [3] 3.3V  ---> ESP32 3V3                      [3] MOSI ---> ESP32 GPIO 23
  [4] RST   ---> ESP32 GPIO 14                  [4] MISO ---> ESP32 GPIO 19
  [5] DIO0  ---> ESP32 GPIO 26                  [5] SCK  ---> ESP32 GPIO 18
  [6] DIO1  (Skip)                              [6] DIO5 (Skip)
  [7] DIO2  (Skip)                              [7] DIO4 (Skip)
  [8] DIO3  (Skip)                              [8] GND  (Skip)
 
                    [ C2 ]  [ C1 ]  (Decoupling Capacitors)
```

### Complete 8-Wire Wiring Schedule

| Wire Color | Ra-02 Pin | Target ESP32 Pin | Function | Critical Safety Note |
| :---: | :--- | :--- | :--- | :--- |
| 🔴 **Red** | **Left [3] (3.3V)** | **`3V3`** | Power Rail | ⚠️ **STRICTLY 3.3V! NEVER CONNECT TO 5V OR VIN!** |
| ⚫ **Black** | **Left [1] (GND)** | **`GND`** | Ground Reference | Common ground |
| 🟡 **Yellow #1** | **Left [4] (RST)** | **`GPIO 14`** (D14) | Radio Reset | Active-low hardware reset |
| 🟡 **Yellow #2** | **Left [5] (DIO0)** | **`GPIO 26`** (D26) | Packet Interrupt | Triggers on packet RX / TX completion |
| 🔵 **Blue #1** | **Right [2] (NSS)** | **`GPIO 5`** (D5) | SPI Chip Select | Bus device selector |
| 🟢 **Green #1** | **Right [3] (MOSI)**| **`GPIO 23`** (D23)| SPI Data In | Master Out $\rightarrow$ Slave In |
| 🟢 **Green #2** | **Right [4] (MISO)**| **`GPIO 19`** (D19)| SPI Data Out | Master In $\leftarrow$ Slave Out |
| 🔵 **Blue #2** | **Right [5] (SCK)** | **`GPIO 18`** (D18)| SPI Clock | Serial clock synchronization |

---

## 3. Step-by-Step DIY Assembly Procedure

### Step 1: Cut & Strip the Wires
1. Cut **8 pieces** of wire, each approximately **12 cm to 15 cm long**:
   * 1x Red, 1x Black, 2x Yellow, 2x Blue, 2x Green.
2. At each end of every wire, strip **1.5 cm (about 1/2 inch)** of insulation:
   * Squeeze your scissors **lightly** around the wire outer skin (do not cut the inner copper hairs).
   * Rotate the wire slightly to score the plastic.
   * Grip the plastic tip with your fingernails and pull it off.
3. **Twist the copper threads immediately:**
   * Tightly roll the exposed copper clockwise between your thumb and index finger until it forms a tight, single solid cord with zero stray copper hairs.

---

### Step 2: The Spiral Wire-Wrap & Tape Technique

```text
               [ Metal Header Pin ]
                     │      │
                     │   ┌──┴──┐
                     │   │ O O │  <-- Coil the twisted copper 3 to 4 turns
                     │   │ O O │      tightly around the metal pin!
                     │   └──┬──┘
                            │
                    [ Insulated Wire ]
```

1. Take the twisted copper tip and **coil it around the target metal pin 3 to 4 times** like a spiral spring.
2. Gently pull the insulated wire to ensure the copper coils bite tightly against the pin.
3. Cut a thin strip of electrical tape (about **5 mm wide** by **3 cm long**).
4. Wrap the tape firmly around the coiled copper on the pin:
   * This compresses the copper tight against the metal pin so it cannot pull loose.
   * This insulates the joint so neighboring pins cannot short-circuit.
5. Repeat on the other end to connect to the corresponding ESP32 pin.

---

## 4. Critical Pre-Flight Safety Checklist

Before plugging your ESP32 into USB or your phone, verify these 3 checkpoints:

1. **Antenna Attached:**
   * Snap the **IPEX external antenna** firmly into the round golden connector on the Ra-02 board.
   * *Warning:* Operating the +18 dBm Power Amplifier with no antenna will reflect energy and burn out the transmitter!
2. **Strict 3.3V Check:**
   * Verify the 🔴 **Red wire** connects to **`3V3`** on the ESP32. If connected to `VIN` or `5V`, the SX1278 will be destroyed permanently.
3. **No Short Circuits:**
   * Look between adjacent pins on both boards. Ensure electrical tape fully covers the exposed copper so no two adjacent pins touch.

---

## 5. Software Verification Code (Arduino IDE)

Once all 8 wires are connected, you can verify that the ESP32 can talk to the Ra-02 over SPI with this quick test sketch:

```cpp
#include <SPI.h>
#include <LoRa.h>

// Ra-02 Pin Definitions matching our wiring
#define LORA_SCK   18
#define LORA_MISO  19
#define LORA_MOSI  23
#define LORA_SS    5
#define LORA_RST   14
#define LORA_DIO0  26

void setup() {
  Serial.begin(115200);
  while (!Serial);

  Serial.println("\n[ ResQPlug Ra-02 LoRa Diagnostic ]");

  // Initialize SPI pins
  SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI, LORA_SS);
  LoRa.setPins(LORA_SS, LORA_RST, LORA_DIO0);

  // Initialize at 433 MHz (Philippine ISM Band)
  if (!LoRa.begin(433E6)) {
    Serial.println("❌ ERROR: Starting LoRa failed! Check wiring (MISO/MOSI/SCK/NSS/3.3V).");
    while (1);
  }

  // Set transmission power to +18 dBm (Ra-02 spec)
  LoRa.setTxPower(18);
  Serial.println("✅ SUCCESS: Ra-02 LoRa Module Initialized at 433.00 MHz!");
}

void loop() {
  // Heartbeat beacon test
  Serial.print("Sending LoRa Beacon Packet...");
  LoRa.beginPacket();
  LoRa.print("ResQPlug Mesh Beacon #");
  LoRa.print(millis());
  LoRa.endPacket();
  Serial.println(" SENT!");

  delay(3000);
}
```
