package com.skyeroad.ble_esp32com

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.skyeroad.ble_esp32com.databinding.ActivityMainBinding
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var gatt: BluetoothGatt? = null
    private var valueCharacteristic: BluetoothGattCharacteristic? = null

    private val handler = Handler(Looper.getMainLooper())

    private val serviceUuid = UUID.fromString("d973f2b9-2ed7-4d5b-ad07-4d1974f2c925")
    private val characteristicUuid = UUID.fromString("d973f2b9-2ed7-4d5b-ad07-4d1974f2c926")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var scanning = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.all { it.value }
        if (granted) {
            startScan()
        } else {
            toast("Bluetooth permissions are required")
            updateStatus("Permissions denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter

        // Apply system bar insets so content is not hidden behind status/navigation bars.
        val contentRoot = binding.contentMain.root
        val baseLeft = contentRoot.paddingLeft
        val baseTop = contentRoot.paddingTop
        val baseRight = contentRoot.paddingRight
        val baseBottom = contentRoot.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                baseLeft + bars.left,
                baseTop + bars.top,
                baseRight + bars.right,
                baseBottom + bars.bottom
            )
            insets
        }

        binding.contentMain.scanButton.setOnClickListener { ensurePermissionsAndScan() }
        binding.contentMain.disconnectButton.setOnClickListener { disconnectGatt() }
        binding.contentMain.readButton.setOnClickListener { readValue() }
        binding.contentMain.writeButton.setOnClickListener { writeValueFromInput() }

        updateConnectionState("Idle", StatusColor.GRAY)
        updateStatus("Waiting for Bluetooth...")
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectGatt()
    }

    private fun ensurePermissionsAndScan() {
        val needsScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
        val needsConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (needsScan || needsConnect) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            startScan()
        }
    }

    private fun startScan() {
        if (scanning) return
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            toast("BLE not available")
            return
        }
        updateConnectionState("Scanning", StatusColor.YELLOW)
        updateStatus("Scanning for ESP32...")
        scanning = true

        val filters = listOf(ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(serviceUuid)).build())
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(filters, settings, scanCallback)
        handler.postDelayed({ stopScan() }, 10_000)
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
            updateConnectionState("Failed", StatusColor.RED)
            updateStatus("Scan failed: $errorCode")
        }
    }

    private fun connect(device: BluetoothDevice) {
        updateConnectionState("Connecting", StatusColor.YELLOW)
        valueCharacteristic = null
        gatt?.close()
        gatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    updateConnectionState("Failed", StatusColor.RED)
                    updateStatus("Connection error: $status")
                }
                gatt.close()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    runOnUiThread {
                        updateConnectionState("Connected", StatusColor.GREEN)
                        updateStatus("Discovering services...")
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    runOnUiThread {
                        updateConnectionState("Disconnected", StatusColor.GRAY)
                        updateStatus("Disconnected")
                        binding.contentMain.lastValue.text = "No value yet"
                    }
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread { updateStatus("Service discovery failed: $status") }
                return
            }
            val service = gatt.getService(serviceUuid)
            val characteristic = service?.getCharacteristic(characteristicUuid)
            if (service == null || characteristic == null) {
                runOnUiThread { updateStatus("Characteristic not found") }
                return
            }
            valueCharacteristic = characteristic
            enableNotifications(gatt, characteristic)
            gatt.readCharacteristic(characteristic)
            runOnUiThread { updateStatus("Ready") }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == characteristicUuid) {
                val value = parseUint32(characteristic.value)
                runOnUiThread { setLastValue(value) }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == characteristicUuid) {
                val value = parseUint32(characteristic.value)
                runOnUiThread {
                    setLastValue(value)
                    updateStatus("Notification: $value")
                }
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(cccdUuid)
        descriptor?.let {
            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(it)
        }
    }

    private fun readValue() {
        val c = valueCharacteristic ?: run {
            updateStatus("Not connected")
            return
        }
        gatt?.readCharacteristic(c)
    }

    private fun writeValueFromInput() {
        val text = binding.contentMain.inputValue.text.toString().trim()
        val value = parseInput(text) ?: run {
            updateStatus("Invalid number")
            return
        }
        val c = valueCharacteristic ?: run {
            updateStatus("Not connected")
            return
        }
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
        c.value = buffer.array()
        val ok = gatt?.writeCharacteristic(c) ?: false
        if (!ok) updateStatus("Write failed to start") else updateStatus("Writing $value...")
    }

    private fun parseInput(text: String): Int? {
        if (text.isEmpty()) return null
        return try {
            if (text.startsWith("0x", true)) {
                text.drop(2).toLong(16).toInt()
            } else {
                text.toLong().toInt()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseUint32(data: ByteArray?): Long? {
        if (data == null || data.size < 4) return null
        val value = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int
        return value.toLong() and 0xFFFFFFFFL
    }

    private fun disconnectGatt() {
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        valueCharacteristic = null
        updateConnectionState("Disconnected", StatusColor.GRAY)
        updateStatus("Disconnected")
    }

    private fun updateConnectionState(state: String, color: StatusColor) {
        binding.contentMain.connectionState.text = state
        val colorInt = when (color) {
            StatusColor.GREEN -> android.R.color.holo_green_light
            StatusColor.YELLOW -> android.R.color.holo_orange_light
            StatusColor.RED -> android.R.color.holo_red_light
            StatusColor.GRAY -> android.R.color.darker_gray
        }
        binding.contentMain.statusDot.setBackgroundColor(ContextCompat.getColor(this, colorInt))
    }

    private fun updateStatus(message: String) {
        binding.contentMain.statusText.text = message
        Log.d("BLE", message)
    }

    private fun setLastValue(value: Long?) {
        binding.contentMain.lastValue.text = value?.let { "Last value: $it" } ?: "No value yet"
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    enum class StatusColor { GREEN, YELLOW, RED, GRAY }
}
