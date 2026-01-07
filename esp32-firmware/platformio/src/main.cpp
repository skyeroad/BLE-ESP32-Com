// PlatformIO entry point that pulls in the shared firmware logic.

#include <BLEDevice.h>   // Ensures PlatformIO's LDF pulls in the BLE library.
#include <Preferences.h> // Ensures PlatformIO's LDF pulls in Preferences.

#include "../../shared/BLEMemoryBridge.cpp"
