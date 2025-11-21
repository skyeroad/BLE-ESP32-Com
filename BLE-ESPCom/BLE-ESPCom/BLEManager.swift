import Foundation
import CoreBluetooth
import Combine

/// Simple CoreBluetooth central that connects to the ESP32 BLE Memory Bridge.
final class BLEManager: NSObject, ObservableObject {
    enum ConnectionState: String {
        case idle = "Idle"
        case scanning = "Scanning"
        case connecting = "Connecting"
        case connected = "Connected"
        case disconnected = "Disconnected"
        case failed = "Failed"
    }

    // BLE UUIDs must match the ESP32 firmware.
    private let serviceUUID = CBUUID(string: "d973f2b9-2ed7-4d5b-ad07-4d1974f2c925")
    private let characteristicUUID = CBUUID(string: "d973f2b9-2ed7-4d5b-ad07-4d1974f2c926")

    @Published private(set) var state: ConnectionState = .idle
    @Published private(set) var statusMessage: String = "Waiting for Bluetooth..."
    @Published private(set) var lastValue: UInt32?

    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var valueCharacteristic: CBCharacteristic?

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: nil)
    }

    func startScanning() {
        guard central.state == .poweredOn else {
            statusMessage = "Bluetooth not ready."
            return
        }
        state = .scanning
        statusMessage = "Scanning for ESP32..."
        central.scanForPeripherals(withServices: [serviceUUID], options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
    }

    func disconnect() {
        if let peripheral {
            central.cancelPeripheralConnection(peripheral)
        }
        reset()
        state = .disconnected
        statusMessage = "Disconnected."
    }

    func readValue() {
        guard let peripheral, let valueCharacteristic else {
            statusMessage = "Not connected."
            return
        }
        peripheral.readValue(for: valueCharacteristic)
    }

    func writeValue(from text: String) {
        guard let peripheral, let valueCharacteristic else {
            statusMessage = "Not connected."
            return
        }

        // Accept decimal input; fall back to hex if prefixed with 0x/0X.
        let sanitized = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let value: UInt32?
        if sanitized.lowercased().hasPrefix("0x") {
            value = UInt32(sanitized.dropFirst(2), radix: 16)
        } else {
            value = UInt32(sanitized)
        }

        guard var toSend = value else {
            statusMessage = "Invalid number."
            return
        }

        var little = toSend.littleEndian
        let data = Data(bytes: &little, count: MemoryLayout<UInt32>.size)
        peripheral.writeValue(data, for: valueCharacteristic, type: .withResponse)
        statusMessage = "Writing \(toSend)..."
    }

    private func reset() {
        peripheral = nil
        valueCharacteristic = nil
        lastValue = nil
    }
}

// MARK: - CBCentralManagerDelegate

extension BLEManager: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            statusMessage = "Bluetooth ready. Tap Scan to connect."
        case .poweredOff:
            statusMessage = "Bluetooth is off."
        case .unauthorized:
            statusMessage = "Bluetooth unauthorized."
        case .unsupported:
            statusMessage = "Bluetooth unsupported."
        default:
            statusMessage = "Bluetooth state: \(central.state.rawValue)"
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        // Connect to the first matching peripheral.
        self.peripheral = peripheral
        self.state = .connecting
        self.statusMessage = "Connecting to \(peripheral.name ?? "ESP32")..."
        peripheral.delegate = self
        central.stopScan()
        central.connect(peripheral, options: nil)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        state = .connected
        statusMessage = "Connected. Discovering services..."
        peripheral.discoverServices([serviceUUID])
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        state = .failed
        statusMessage = "Failed to connect: \(error?.localizedDescription ?? "unknown error")"
        reset()
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        state = .disconnected
        statusMessage = "Disconnected."
        reset()
    }
}

// MARK: - CBPeripheralDelegate

extension BLEManager: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error {
            statusMessage = "Service discovery failed: \(error.localizedDescription)"
            return
        }
        guard let services = peripheral.services else { return }
        for service in services where service.uuid == serviceUUID {
            peripheral.discoverCharacteristics([characteristicUUID], for: service)
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let error {
            statusMessage = "Characteristic discovery failed: \(error.localizedDescription)"
            return
        }
        guard let characteristics = service.characteristics else { return }
        for characteristic in characteristics where characteristic.uuid == characteristicUUID {
            valueCharacteristic = characteristic
            peripheral.setNotifyValue(true, for: characteristic)
            peripheral.readValue(for: characteristic)
            statusMessage = "Ready."
            return
        }
        statusMessage = "Characteristic not found."
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error {
            statusMessage = "Read failed: \(error.localizedDescription)"
            return
        }
        guard let data = characteristic.value else { return }
        if data.count >= MemoryLayout<UInt32>.size {
            let value = data.withUnsafeBytes { $0.load(as: UInt32.self) }
            lastValue = UInt32(littleEndian: value)
            statusMessage = "Value: \(lastValue!)"
        } else if let stringValue = String(data: data, encoding: .utf8) {
            statusMessage = "Received string: \(stringValue)"
        } else {
            statusMessage = "Received \(data.count) bytes."
        }
    }
}
