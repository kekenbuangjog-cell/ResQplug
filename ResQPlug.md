# Capstone Project Title Proposal

## Project Title
**ResQPlug: A USB-OTG LoRa Mesh Communication Protocol with Priority Message Queuing for Off-Grid Disaster Response**

---

## 1. Project Overview & Abstract

**ResQPlug** is a low-cost, off-grid emergency communication system designed to establish reliable, decentralized text messaging during major natural disasters, typhoons, and electrical blackouts when conventional cellular towers and internet infrastructures fail.

Rather than relying on vulnerable commercial telecommunications networks, ResQPlug converts standard Android smartphones into long-range, two-way radio transceivers using a compact **USB-OTG LoRa (Long Range) hardware dongle**. The dongle connects directly to the smartphone's USB-C charging port, drawing minimal operating power straight from the mobile device. This eliminates the need for external battery packs, bulky enclosures, or error-prone wireless pairing (such as Bluetooth) during high-stress emergency operations.

Through a dedicated, lightweight Android application, local community members, evacuees, and barangay emergency responders can transmit critical text updates and distress signals across an **ad-hoc decentralized mesh network**. Packets dynamically hop across intermediary nodes to reach distant recipients across kilometers. To prevent narrow radio bandwidth channels from congesting, the system implements an intelligent **Priority Message Queuing Algorithm** that automatically prioritizes life-threatening SOS distress signals and medical emergencies over non-critical status updates.

---

## 2. Statement of the Problem

### General Problem
During severe natural disasters (e.g., Category 5 typhoons, flash floods, seismic events), centralized telecommunication towers and power grids suffer catastrophic outages, leaving affected citizens and first responders in total information blackouts without means to coordinate rescue or medical aid.

### Specific Problems
1. **Infrastructure Vulnerability:** Commercial cellular (4G/5G) and fiber internet networks are single-point-of-failure systems vulnerable to physical line severance, tower collapse, and power grid failures.
2. **Bandwidth Congestion in Low-Power Radio:** Standard long-range radio bands (such as 433 MHz / 915 MHz LoRa) operate at narrow data transmission rates (often under 2 kbps). Unregulated broadcast floods cause packet collision and drop critical emergency alerts.
3. **Complex Pairing & High Battery Drain:** Existing commercial or open-source radio prototypes frequently rely on Bluetooth pairing or standalone battery packs, increasing configuration failure rates and rapidly draining device batteries during extended multi-day blackouts.

---

## 3. Project Objectives

### General Objective
To design, construct, and evaluate **ResQPlug**, a low-cost USB-OTG LoRa communication dongle and companion Android application featuring dynamic mesh routing, priority message queuing, and offline geographic mapping for off-grid disaster response.

### Specific Objectives
1. **Hardware Interface Development:**
   * Construct an ultra-low-cost, plug-and-play transceiver dongle utilizing an **ESP32 microcontroller** paired with an **SX1276/SX1262 LoRa radio module**, powered directly via **USB-C OTG (On-The-Go)**.
2. **Serial Bridge & Driver Integration:**
   * Develop a native USB Serial communication service within the Android application to enable zero-configuration auto-detection and continuous packet streaming upon physical connection.
3. **Priority Queuing & Mesh Routing Protocol:**
   * Formulate and implement a **Priority Message Queuing Algorithm** (High: SOS/Medical; Medium: Evacuation/Relief logistics; Low: Status updates) combined with an ad-hoc Store-and-Forward packet relay protocol.

---

## 4. System Architecture

```text
+-------------------------------------------------------------+
|                     Android Smartphone                      |
|                                                             |
|  [ User Interface / Message Display ]                       |
|                              |                              |
|  [ Priority Queuing Engine (SOS > Logistics > Status) ]     |
|                              |                              |
|  [ Android USB-OTG Serial Driver Service ]                  |
+------------------------------|------------------------------+
                               | (USB-C OTG Direct Cable)
                               v
+-------------------------------------------------------------+
|                  ResQPlug Hardware Dongle                   |
|                                                             |
|  [ ESP32 MCU (Baud Rate: 115200 / Power: 5V OTG) ]          |
|                              |                              |
|  [ LoRa Transceiver Module (SX1276 / 433 MHz / 915 MHz) ]   |
|                              |                              |
|  [ Omni-Directional Sub-GHz Antenna ]                       |
+------------------------------|------------------------------+
                               |
                               | (Wireless RF Signal - Up to 5 km)
                               v
+-------------------------------------------------------------+
|              Neighboring Nodes / Relay Mesh                 |
|             (Hop 1 -> Hop 2 -> Destination Node)            |
+-------------------------------------------------------------+
```
