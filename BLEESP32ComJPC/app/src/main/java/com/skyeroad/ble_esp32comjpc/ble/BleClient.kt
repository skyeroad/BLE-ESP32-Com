package com.skyeroad.ble_esp32comjpc.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleClient(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var gatt: BluetoothGatt? = null
    private var valueCharacteristic: BluetoothGattCharacteristic? = null
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false

    private val serviceUuid = UUID.fromString("d973f2b9-2ed7-4d5b-ad07-4d1974f2c925")
    private val characteristicUuid = UUID.fromString("d973f2b9-2ed7-4d5b-ad07-4d1974f2c926")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val _uiState = MutableStateFlow(
        BleUiState(
            connectionState = "Idle",
            statusMessage = "Waiting for Bluetooth...",
            lastValue = "No value yet",
            statusColor = StatusColor.Gray,
            inputText = "",
            isConnected = false
        )
    )
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun startScan() {
        if (scanning) return
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            updateStatus("BLE not available")
            return
        }

        updateConnectionState("Scanning", StatusColor.Yellow, isConnected = false)
        updateStatus("Scanning for ESP32...")
        scanning = true

        val filters = listOf(ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(serviceUuid)).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(filters, settings, scanCallback)
        handler.postDelayed({ stopScan() }, 10_000)
    }

    fun stop() {
        disconnect()
    }

    fun disconnect() {
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        valueCharacteristic = null
        updateConnectionState("Disconnected", StatusColor.Gray, isConnected = false)
        updateStatus("Disconnected")
    }

    fun readValue() {
        val c = valueCharacteristic ?: run {
            updateStatus("Not connected")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt?.readCharacteristic(c) ?: BluetoothStatusCodes.ERROR_UNKNOWN
            if (status != BluetoothStatusCodes.SUCCESS) {
                updateStatus("Read failed to start ($status)")
            }
        } else {
            @Suppress("DEPRECATION")
            val ok = gatt?.readCharacteristic(c) ?: false
            if (!ok) updateStatus("Read failed to start")
        }
    }

    fun writeValueFromInput() {
        val text = _uiState.value.inputText.trim()
        val value = parseInput(text) ?: run {
            updateStatus("Invalid number")
            return
        }
        val c = valueCharacteristic ?: run {
            updateStatus("Not connected")
            return
        }
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = gatt?.writeCharacteristic(
                c,
                buffer.array(),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) ?: BluetoothStatusCodes.ERROR_UNKNOWN
            if (status == BluetoothStatusCodes.SUCCESS) {
                updateStatus("Writing $value...")
            } else {
                updateStatus("Write failed to start ($status)")
            }
        } else {
            @Suppress("DEPRECATION")
            val ok = gatt?.writeCharacteristic(c) ?: false
            if (ok) updateStatus("Writing $value...") else updateStatus("Write failed to start")
        }
    }

    fun permissionsDenied() {
        updateStatus("Bluetooth permissions are required")
        updateConnectionState("Permissions denied", StatusColor.Red, isConnected = false)
    }

    private fun stopScan() {
        if (!scanning) return
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        scanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScan()
            updateStatus("Connecting to ${result.device.name ?: "ESP32"}...")
            connect(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            updateConnectionState("Failed", StatusColor.Red, isConnected = false)
            updateStatus("Scan failed: $errorCode")
        }
    }

    private fun connect(device: BluetoothDevice) {
        updateConnectionState("Connecting", StatusColor.Yellow, isConnected = false)
        valueCharacteristic = null
        gatt?.close()
        gatt = device.connectGatt(appContext, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post {
                    updateConnectionState("Failed", StatusColor.Red, isConnected = false)
                    updateStatus("Connection error: $status")
                }
                gatt.close()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    handler.post {
                        updateConnectionState("Connected", StatusColor.Green, isConnected = true)
                        updateStatus("Discovering services...")
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    handler.post {
                        updateConnectionState("Disconnected", StatusColor.Gray, isConnected = false)
                        updateStatus("Disconnected")
                        setLastValue(null)
                    }
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post { updateStatus("Service discovery failed: $status") }
                return
            }
            val service = gatt.getService(serviceUuid)
            val characteristic = service?.getCharacteristic(characteristicUuid)
            if (service == null || characteristic == null) {
                handler.post { updateStatus("Characteristic not found") }
                return
            }
            valueCharacteristic = characteristic
            enableNotifications(gatt, characteristic)
            gatt.readCharacteristic(characteristic)
            handler.post { updateStatus("Ready") }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == characteristicUuid) {
                val parsed = parseUint32(value)
                handler.post { setLastValue(parsed) }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == characteristicUuid) {
                val parsed = parseUint32(value)
                handler.post {
                    setLastValue(parsed)
                    updateStatus("Notification: $parsed")
                }
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(cccdUuid)
        descriptor?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val status = gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                if (status != BluetoothStatusCodes.SUCCESS) {
                    updateStatus("Enable notify failed ($status)")
                }
            } else {
                @Suppress("DEPRECATION")
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                if (!gatt.writeDescriptor(it)) {
                    updateStatus("Enable notify failed")
                }
            }
        }
    }

    private fun parseInput(text: String): Int? {
        if (text.isEmpty()) return null
        return try {
            if (text.startsWith("0x", true)) {
                text.drop(2).toLong(16).toInt()
            } else {
                text.toLong().toInt()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseUint32(data: ByteArray?): Long? {
        if (data == null || data.size < 4) return null
        val value = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int
        return value.toLong() and 0xFFFFFFFFL
    }

    private fun updateConnectionState(state: String, color: StatusColor, isConnected: Boolean) {
        _uiState.value = _uiState.value.copy(
            connectionState = state,
            statusColor = color,
            isConnected = isConnected
        )
    }

    private fun updateStatus(message: String) {
        _uiState.value = _uiState.value.copy(statusMessage = message)
    }

    private fun setLastValue(value: Long?) {
        val text = value?.let { "Last value: $it" } ?: "No value yet"
        _uiState.value = _uiState.value.copy(lastValue = text)
    }
}

data class BleUiState(
    val connectionState: String,
    val statusMessage: String,
    val lastValue: String,
    val statusColor: StatusColor,
    val inputText: String,
    val isConnected: Boolean = false
)

enum class StatusColor { Green, Yellow, Red, Gray }
