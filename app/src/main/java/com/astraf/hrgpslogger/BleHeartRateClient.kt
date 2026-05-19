package com.astraf.hrgpslogger

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class ScannedDevice(
    val address: String,
    val name: String,
)

enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    READY,
}

class BleHeartRateClient(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices.asStateFlow()

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _heartRateBpm = MutableStateFlow<Int?>(null)
    val heartRateBpm: StateFlow<Int?> = _heartRateBpm.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    private val _statusMessage = MutableStateFlow("Готов")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private val discovered = linkedMapOf<String, ScannedDevice>()

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val name = device.name?.takeIf { it.isNotBlank() }
                ?: result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                ?: address
            discovered[address] = ScannedDevice(address = address, name = name)
            _devices.value = discovered.values.sortedBy { it.name.lowercase() }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _statusMessage.value = "Ошибка сканирования BLE: $errorCode"
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _statusMessage.value = "Ошибка GATT: $status"
                cleanupGatt()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleConnectionState.CONNECTED
                    _statusMessage.value = "Подключено, поиск сервисов..."
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    _heartRateBpm.value = null
                    _statusMessage.value = "Отключено"
                    cleanupGatt()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _statusMessage.value = "Ошибка обнаружения сервисов: $status"
                return
            }
            val service = gatt.getService(HEART_RATE_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
            if (characteristic == null) {
                _statusMessage.value = "Heart Rate Service не найден"
                return
            }
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_UUID)
            if (descriptor == null) {
                _statusMessage.value = "Descriptor CCCD не найден"
                return
            }
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                characteristic.uuid == HEART_RATE_MEASUREMENT_UUID
            ) {
                _heartRateBpm.value = parseHeartRate(value)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                characteristic.uuid == HEART_RATE_MEASUREMENT_UUID
            ) {
                val bpm = parseHeartRate(characteristic.value ?: return)
                _heartRateBpm.value = bpm
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS &&
                descriptor.uuid == CLIENT_CONFIG_UUID
            ) {
                _connectionState.value = BleConnectionState.READY
                _statusMessage.value = "Пульсометр готов"
            }
        }
    }

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _statusMessage.value = "Bluetooth выключен или недоступен"
            return
        }
        if (isScanning) return
        discovered.clear()
        _devices.value = emptyList()
        isScanning = true
        _statusMessage.value = "Сканирование BLE..."
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        adapter.bluetoothLeScanner.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        _statusMessage.value = "Сканирование остановлено"
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        stopScan()
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _statusMessage.value = "Bluetooth недоступен"
            return
        }
        disconnect()
        _connectedDeviceAddress.value = address
        val device: BluetoothDevice = adapter.getRemoteDevice(address)
        _connectionState.value = BleConnectionState.CONNECTING
        _statusMessage.value = "Подключение к $address..."
        bluetoothGatt = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED
        _heartRateBpm.value = null
        _connectedDeviceAddress.value = null
    }

    fun release() {
        disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun cleanupGatt() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    companion object {
        val HEART_RATE_SERVICE_UUID: UUID =
            UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun parseHeartRate(data: ByteArray): Int {
            if (data.isEmpty()) return 0
            val flags = data[0].toInt() and 0xFF
            val isUint16 = flags and 0x01 != 0
            return if (isUint16) {
                if (data.size < 3) 0
                else (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
            } else {
                if (data.size < 2) 0
                else data[1].toInt() and 0xFF
            }
        }
    }
}
