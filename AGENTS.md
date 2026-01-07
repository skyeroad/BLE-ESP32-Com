# Repository Guidelines

## Project Structure & Module Organization
- `BLE-ESPCom/` — SwiftUI iOS app. Open `BLE-ESPCom.xcodeproj` in Xcode; main sources in `BLE-ESPCom/BLE-ESPCom/` (e.g., `ContentView.swift`, `BLEManager.swift`, `BLE_ESPComApp.swift`). Assets live in `Assets.xcassets/`.
- `esp32-firmware/` — ESP32 BLE firmware with shared sources for Arduino IDE + PlatformIO. Shared logic in `shared/BLEMemoryBridge.cpp`; entry points in `arduino/` and `platformio/`.
- `BLEESP32ComJPC/` — Jetpack Compose Android app (Material 3). Uses Compose BOM + Kotlin Compose plugin; entry at `app/src/main/java/com/skyeroad/ble_esp32comjpc/MainActivity.kt` with BLE logic in `ble/BleClient.kt` and `BleViewModel.kt`. Requires physical device (BLE), minSdk 33, targetSdk 36.

## Build, Test, and Development Commands
- iOS: open `BLE-ESPCom/BLE-ESPCom.xcodeproj` and run on a physical device (BLE not available in the simulator). Use Xcode’s Run (⌘R) to build/launch.
- ESP32 (Arduino IDE): open `esp32-firmware/arduino/BLEMemoryBridge/BLEMemoryBridge.ino`. Select your ESP32 board/port, then **Upload**. Monitor via Serial at 115200 baud.
- ESP32 (PlatformIO): from `esp32-firmware/platformio/`, run `pio run` then `pio run -t upload`. If auto-detect picks the wrong device, pass `--upload-port /dev/cu.usbserial-0001`. Monitor via `pio device monitor -b 115200`.
- Upload troubleshooting: if PlatformIO can't connect, hold BOOT/IO0 during upload or tap EN/RESET when “Connecting...” appears.
- Android (Compose): `./gradlew :app:assembleDebug` from `BLEESP32ComJPC/`. Use a physical device; BLE not available on emulator. BLE permissions requested at runtime.

## Coding Style & Naming Conventions
- Swift: 4-space indentation, SwiftUI-first architecture, avoid force unwraps. Keep UUIDs in sync between app and firmware (service `d973f2b9-2ed7-4d5b-ad07-4d1974f2c925`, characteristic `...926`).
- Firmware: Arduino C++ with minimal dependencies. Keep configuration near the top of `BLEMemoryBridge.ino`; prefer clear globals and small callback classes.
- Android Compose: Kotlin 2.0.21, AGP 8.13.1, Compose BOM. Compose-first UI (no XML); prefer state hoisted via ViewModel/StateFlow. Use Material 3 theme (iOS-like palette).
- Filenames: PascalCase for Swift types/files; concise snake-like or camel case for firmware globals (e.g., `storedValue`, `VALUE_CHAR_UUID`).

## Testing Guidelines
- No automated test suite configured. For changes, manually verify:
  - iOS: scan/connect/read/write cycle with a real ESP32; confirm notifications after writes.
  - Firmware: reboot persistence (stored value survives power cycle), and automatic re-advertise on disconnect.
  - Optional: use LightBlue/nRF Connect to sanity-check BLE service/characteristic behavior.
- BLE multi-connection: ESP32 firmware currently allows one central at a time; multiple centrals would require increasing NimBLE connection limits and validating memory/perf. Mobile apps already handle single-connection flow.

## Commit & Pull Request Guidelines
- Commits: keep messages imperative and scoped (e.g., `Add BLE manager and SwiftUI UI`, `Persist ESP32 value to NVS`). Group related changes together.
- Pull requests: include a short summary, screenshots for UI changes, and notes on manual verification (device used, steps run). Reference related issues when applicable. Call out any UUID or protocol changes explicitly so mobile/firmware stay aligned.

## Android App (parity with iOS)
- Project: `BLE-ESP32ComAndroid/` (Android Studio). MinSdk 33 (Android 13). Manifest declares BLE feature plus `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`; prompts at runtime.
- Main screen: status dot/state, scan/disconnect buttons, last value text, input + Write, Read button. Layouts: `app/src/main/res/layout/activity_main.xml`, `content_main.xml`, `drawable/status_dot.xml`.
- Logic: `app/src/main/java/com/skyeroad/ble_esp32com/MainActivity.kt` — uses `BluetoothLeScanner` with service UUID filter, connects via `BluetoothGatt`, discovers characteristic, enables notifications (CCCD), reads initial value, writes UInt32 little-endian from input (decimal or 0x...). UUIDs match firmware/iOS.
- Build/run: use a physical device (e.g., Pixel 6a on Android 13+), accept Bluetooth permissions, then Scan → Connect → Read/Write. BLE unavailable on emulator.
- UI/layout: `CoordinatorLayout` and `AppBarLayout` use `android:fitsSystemWindows="true"` to sit below system bars; content include has `app:layout_behavior="@string/appbar_scrolling_view_behavior"` so toolbar/title sit above the buttons.
- Android gitignore: root `.gitignore` ignores `BLE-ESP32ComAndroid/.gradle`, build outputs, `.idea`, `local.properties`, IDE metadata, and APK/AAB artifacts.
