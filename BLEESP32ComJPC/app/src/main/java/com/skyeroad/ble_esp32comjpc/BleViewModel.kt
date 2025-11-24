package com.skyeroad.ble_esp32comjpc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skyeroad.ble_esp32comjpc.ble.BleClient
import com.skyeroad.ble_esp32comjpc.ble.BleUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BleViewModel(application: Application) : AndroidViewModel(application) {
    private val client = BleClient(application.applicationContext)

    val uiState: StateFlow<BleUiState> = client.uiState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = client.uiState.value
        )

    fun onInputChange(text: String) {
        client.updateInput(text)
    }

    fun onScan() {
        client.startScan()
    }

    fun onDisconnect() {
        client.disconnect()
    }

    fun onRead() {
        client.readValue()
    }

    fun onWrite() {
        client.writeValueFromInput()
    }

    fun onPermissionsDenied() {
        client.permissionsDenied()
    }

    override fun onCleared() {
        client.stop()
        super.onCleared()
    }
}
