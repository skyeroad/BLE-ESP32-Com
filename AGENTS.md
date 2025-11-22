# Repository Guidelines

## Project Structure & Module Organization
- `BLE-ESPCom/` — SwiftUI iOS app. Open `BLE-ESPCom.xcodeproj` in Xcode; main sources in `BLE-ESPCom/BLE-ESPCom/` (e.g., `ContentView.swift`, `BLEManager.swift`, `BLE_ESPComApp.swift`). Assets live in `Assets.xcassets/`.
- `esp32-firmware/` — Arduino sketch for the ESP32 BLE peripheral. Primary file: `BLEMemoryBridge/BLEMemoryBridge.ino` plus `README.md`.

## Build, Test, and Development Commands
- iOS: open `BLE-ESPCom/BLE-ESPCom.xcodeproj` and run on a physical device (BLE not available in the simulator). Use Xcode’s Run (⌘R) to build/launch.
- ESP32: open `esp32-firmware/BLEMemoryBridge/BLEMemoryBridge.ino` in Arduino IDE. Select your ESP32 board/port, then **Upload**. Monitor via Serial at 115200 baud.

## Coding Style & Naming Conventions
- Swift: 4-space indentation, SwiftUI-first architecture, avoid force unwraps. Keep UUIDs in sync between app and firmware (service `d973f2b9-2ed7-4d5b-ad07-4d1974f2c925`, characteristic `...926`).
- Firmware: Arduino C++ with minimal dependencies. Keep configuration near the top of `BLEMemoryBridge.ino`; prefer clear globals and small callback classes.
- Filenames: PascalCase for Swift types/files; concise snake-like or camel case for firmware globals (e.g., `storedValue`, `VALUE_CHAR_UUID`).

## Testing Guidelines
- No automated test suite configured. For changes, manually verify:
  - iOS: scan/connect/read/write cycle with a real ESP32; confirm notifications after writes.
  - Firmware: reboot persistence (stored value survives power cycle), and automatic re-advertise on disconnect.
  - Optional: use LightBlue/nRF Connect to sanity-check BLE service/characteristic behavior.

## Commit & Pull Request Guidelines
- Commits: keep messages imperative and scoped (e.g., `Add BLE manager and SwiftUI UI`, `Persist ESP32 value to NVS`). Group related changes together.
- Pull requests: include a short summary, screenshots for UI changes, and notes on manual verification (device used, steps run). Reference related issues when applicable. Call out any UUID or protocol changes explicitly so mobile/firmware stay aligned.

## Android App (parity with iOS)
- Project: `BLE-ESP32ComAndroid/` (Android Studio). MinSdk 33 (Android 13). Manifest declares BLE feature plus `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`; prompts at runtime.
- Main screen: status dot/state, scan/disconnect buttons, last value text, input + Write, Read button. Layouts: `app/src/main/res/layout/activity_main.xml`, `content_main.xml`, `drawable/status_dot.xml`.
- Logic: `app/src/main/java/com/skyeroad/ble_esp32com/MainActivity.kt` — uses `BluetoothLeScanner` with service UUID filter, connects via `BluetoothGatt`, discovers characteristic, enables notifications (CCCD), reads initial value, writes UInt32 little-endian from input (decimal or 0x...). UUIDs match firmware/iOS.
- Build/run: use a physical device (e.g., Pixel 6a on Android 13+), accept Bluetooth permissions, then Scan → Connect → Read/Write. BLE unavailable on emulator.
