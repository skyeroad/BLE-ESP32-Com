# ESP32 BLE Memory Bridge

Shared Arduino/PlatformIO firmware that exposes a single read/write BLE characteristic so the iOS app can read and update a 32-bit value held in ESP32 memory.

## Prerequisites
- Arduino IDE with the ESP32 boards package installed (Tools → Board → Boards Manager → search for `esp32`).
- PlatformIO (VS Code extension or CLI) if you want the PlatformIO build.
- Select your board (e.g., `ESP32 Dev Module`) and the correct serial port before uploading.

## Files
- `shared/BLEMemoryBridge.cpp` — shared firmware logic (advertising name: `ESP32 Memory Bridge`).
- `arduino/BLEMemoryBridge/BLEMemoryBridge.ino` — Arduino IDE entry point that includes the shared code.
- `platformio/` — PlatformIO project that includes the shared code (`esp32dev`, 115200 baud).

## BLE contract
- Service UUID: `d973f2b9-2ed7-4d5b-ad07-4d1974f2c925`
- Characteristic UUID: `d973f2b9-2ed7-4d5b-ad07-4d1974f2c926`
- Properties: read, write, notify
- Stored value: 32-bit unsigned integer (defaults to `0x12345678`, persisted in NVS flash after writes).
- Write payloads accepted:
  - 4-byte little-endian integer (preferred; matches what is sent back on reads).
  - ASCII decimal string fallback (e.g., `"42"`).
- Reads return the current value as 4-byte little-endian data.

## Flashing
### Arduino IDE
1. Open `arduino/BLEMemoryBridge/BLEMemoryBridge.ino` in Arduino IDE.
2. Select your ESP32 board and port under **Tools**.
3. Upload. Open the Serial Monitor at 115200 baud to watch updates when values are written from the app.

### PlatformIO
1. Open `platformio/` in VS Code (PlatformIO extension) or use the CLI.
2. Build: `pio run`
3. Upload: `pio run -t upload`
4. If auto-detect selects the wrong device, specify the port: `pio run -t upload --upload-port /dev/cu.usbserial-0001`
5. Monitor: `pio device monitor -b 115200`

## Troubleshooting
- If upload fails to connect, hold BOOT/IO0 while the upload begins or tap EN/RESET when “Connecting...” appears.

## Using it from the iOS app
- Scan for devices advertising the service UUID above; the peripheral name is `ESP32 Memory Bridge`.
- Connect, discover the service, and read/write the characteristic UUID above.
- After a successful write, the ESP32 updates its in-memory value, echoes it back into the characteristic, and sends a notification so subscribers receive the new value immediately.
- If the central disconnects, the ESP32 restarts advertising automatically so you can reconnect without a manual reset.

## Quick testing without the app
- Use a BLE explorer like nRF Connect or LightBlue.
- Connect, locate the service/characteristic, and:
  - Read to see the default value.
  - Write four bytes (little-endian) or an ASCII decimal string to change it; you should see a notification with the new value.
