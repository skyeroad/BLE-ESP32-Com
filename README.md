# BLE ESP32 Comm

A minimal pairing of:
- A SwiftUI iOS app that scans for an ESP32 peripheral, reads/writes a 32-bit value over BLE, and listens for notifications.
- An ESP32 Arduino sketch that exposes a single read/write/notify characteristic with a persisted 32-bit value.

## Project Structure
- `BLE-ESPCom/` — iOS app. Open `BLE-ESPCom/BLE-ESPCom.xcodeproj` in Xcode. Sources live in `BLE-ESPCom/BLE-ESPCom/` (`ContentView.swift`, `BLEManager.swift`, `BLE_ESPComApp.swift`). Assets in `Assets.xcassets/`.
- `BLE-ESP32ComAndroid/` — Android app. Open in Android Studio. Layouts in `app/src/main/res/layout/` (`activity_main.xml`, `content_main.xml`, `drawable/status_dot.xml`); logic in `app/src/main/java/com/skyeroad/ble_esp32com/MainActivity.kt`.
- `esp32-firmware/` — Arduino firmware. Main sketch: `BLEMemoryBridge/BLEMemoryBridge.ino`. Additional notes in `esp32-firmware/README.md`.

## BLE Contract (shared IDs)
- Service UUID: `d973f2b9-2ed7-4d5b-ad07-4d1974f2c925`
- Characteristic UUID: `d973f2b9-2ed7-4d5b-ad07-4d1974f2c926`
- Data: 32-bit unsigned integer, little-endian. Notifications sent after writes.

## Running the iOS App
1. Open `BLE-ESPCom/BLE-ESPCom.xcodeproj` in Xcode.
2. Build/run on a physical device (BLE is not available in the simulator) — ⌘R.
3. Tap **Scan** to find the ESP32 (`ESP32 Memory Bridge`), connect, then **Read** or enter a value and **Write**.

## Running the Android App
1. Open `BLE-ESP32ComAndroid/` in Android Studio.
2. Use a physical device (BLE not available on the emulator), MinSdk 33. Accept Bluetooth permissions on first run.
3. On the main screen: status dot and connection text sit under the toolbar; buttons are below the title. Use **Scan** to find the ESP32 (filtered by service UUID), **Disconnect** to drop, **Read** to fetch the value, or enter a decimal/`0x` value and tap **Write**. Notifications update the last value text.

## Flashing the ESP32
1. Open `esp32-firmware/BLEMemoryBridge/BLEMemoryBridge.ino` in Arduino IDE.
2. Select your ESP32 board and port, then **Upload**.
3. Defaults to value `0x12345678`, persisted to NVS; re-advertises automatically after disconnects.

## Notes
- Keep UUIDs in sync if you change them in either side.
- Use a BLE explorer (nRF Connect, LightBlue) for quick validation.
