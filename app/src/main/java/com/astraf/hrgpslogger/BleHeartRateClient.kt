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
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

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

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _heartRateBpm = MutableStateFlow<Int?>(null)
    val heartRateBpm: StateFlow<Int?> = _heartRateBpm.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    private val _statusMessage = MutableStateFlow("Готов")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var scanActive = false
    private var autoConnectScan = false

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    private val scanTimeoutRunnable = Runnable { stopScanInternal(notifyNotFound = true) }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device?.address ?: return
            if (!autoConnectScan) return
            // ScanFilter по MAC для непарного устройства не используем — фильтруем здесь.
            if (!address.equals(TARGET_DEVICE_ADDRESS, ignoreCase = true)) return
            connect(address)
        }

        override fun onScanFailed(errorCode: Int) {
            setScanning(false)
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
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_UUID) {
                _heartRateBpm.value = parseHeartRate(value)
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
    fun scanAndConnectToPreferredDevice() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _statusMessage.value = "Bluetooth выключен или недоступен"
            return
        }
        when (_connectionState.value) {
            BleConnectionState.CONNECTING,
            BleConnectionState.CONNECTED,
            BleConnectionState.READY,
            -> {
                _statusMessage.value = "Уже подключено"
                return
            }
            BleConnectionState.DISCONNECTED -> Unit
        }
        if (scanActive) {
            stopScanInternal(notifyNotFound = false)
        }
        autoConnectScan = true
        setScanning(true)
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            adapter.bluetoothLeScanner.startScan(null, settings, scanCallback)
            mainHandler.removeCallbacks(scanTimeoutRunnable)
            mainHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
        } catch (securityException: SecurityException) {
            Log.e(TAG, "BLE scan permission denied", securityException)
            setScanning(false)
            autoConnectScan = false
            _statusMessage.value = "Нет разрешения Bluetooth"
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal(notifyNotFound: Boolean) {
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        if (!scanActive) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (securityException: SecurityException) {
            Log.e(TAG, "BLE stop scan permission denied", securityException)
        }
        setScanning(false)
        val wasAutoConnect = autoConnectScan
        autoConnectScan = false
        if (notifyNotFound &&
            wasAutoConnect &&
            _connectionState.value == BleConnectionState.DISCONNECTED
        ) {
            _statusMessage.value = "Пульсометр не найден"
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        stopScanInternal(notifyNotFound = false)
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
        stopScanInternal(notifyNotFound = false)
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

    private fun setScanning(scanning: Boolean) {
        scanActive = scanning
        _isScanning.value = scanning
    }

    companion object {
        private const val TAG = "BleHeartRateClient"
        private const val SCAN_TIMEOUT_MS = 30_000L
        const val TARGET_DEVICE_ADDRESS = "D6:64:A3:24:46:8D"

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
