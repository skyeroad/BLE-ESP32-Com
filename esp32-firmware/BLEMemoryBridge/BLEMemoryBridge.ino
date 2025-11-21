// Minimal BLE bridge for read/write access to a 32-bit value in ESP32 memory.
// Load this sketch in the Arduino IDE with the ESP32 boards package installed.

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <Preferences.h>

// UUIDs shared with the iOS app; update the app to match if you change these.
const char *SERVICE_UUID = "d973f2b9-2ed7-4d5b-ad07-4d1974f2c925";
const char *VALUE_CHAR_UUID = "d973f2b9-2ed7-4d5b-ad07-4d1974f2c926";

BLECharacteristic *valueCharacteristic = nullptr;
Preferences prefs;
const uint32_t DEFAULT_VALUE = 0x12345678;
uint32_t storedValue = DEFAULT_VALUE;  // Default value exposed over BLE.

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *) override {
    Serial.println("Central connected.");
  }

  void onDisconnect(BLEServer *) override {
    Serial.println("Central disconnected; restarting advertising.");
    BLEDevice::startAdvertising();
  }
};

class ValueCallbacks : public BLECharacteristicCallbacks {
  void onRead(BLECharacteristic *characteristic) override {
    // Keep the characteristic buffer in sync with the stored value.
    characteristic->setValue((uint8_t *)&storedValue, sizeof(storedValue));
  }

  void onWrite(BLECharacteristic *characteristic) override {
    String incomingStr = characteristic->getValue();
    size_t len = incomingStr.length();
    const char *raw = incomingStr.c_str();

    // Accept either a 4-byte little-endian integer or an ASCII decimal string.
    if (len == sizeof(uint32_t)) {
      uint32_t parsed;
      memcpy(&parsed, raw, sizeof(uint32_t));
      storedValue = parsed;
    } else {
      char *end = nullptr;
      uint32_t parsed = strtoul(raw, &end, 10);
      if (end != raw) {
        storedValue = parsed;
      } else {
        // Invalid payload; leave storedValue unchanged.
        return;
      }
    }

    // Update characteristic value and notify any subscribed centrals.
    characteristic->setValue((uint8_t *)&storedValue, sizeof(storedValue));
    prefs.putUInt("value", storedValue);  // Persist across resets.
    characteristic->notify();

    Serial.printf("Value updated to: %u\n", storedValue);
  }
};

void setup() {
  Serial.begin(115200);
  Serial.println("Starting BLE Memory Bridge...");

  prefs.begin("blebridge", false);
  storedValue = prefs.getUInt("value", DEFAULT_VALUE);

  BLEDevice::init("ESP32 Memory Bridge");

  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  BLEService *service = server->createService(SERVICE_UUID);

  valueCharacteristic = service->createCharacteristic(
      VALUE_CHAR_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE |
          BLECharacteristic::PROPERTY_NOTIFY);

  valueCharacteristic->setCallbacks(new ValueCallbacks());
  valueCharacteristic->setValue((uint8_t *)&storedValue, sizeof(storedValue));

  service->start();

  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->setMinPreferred(0x06);  // Improve iOS connection stability.
  advertising->setMinPreferred(0x12);

  BLEDevice::startAdvertising();

  Serial.println("BLE service is up and advertising.");
}

void loop() {
  delay(1000);
}
