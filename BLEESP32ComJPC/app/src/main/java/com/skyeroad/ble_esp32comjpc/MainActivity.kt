package com.skyeroad.ble_esp32comjpc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skyeroad.ble_esp32comjpc.BleViewModel
import com.skyeroad.ble_esp32comjpc.ble.BleUiState
import com.skyeroad.ble_esp32comjpc.ble.StatusColor
import com.skyeroad.ble_esp32comjpc.ui.theme.BLEESP32ComJPCTheme

class MainActivity : ComponentActivity() {
    private val viewModel: BleViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.all { it.value }
        if (granted) {
            viewModel.onScan()
        } else {
            viewModel.onPermissionsDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BLEESP32ComJPCTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        state = state,
                        onInputChange = viewModel::onInputChange,
                        onScan = { ensurePermissionsAndScan() },
                        onDisconnect = viewModel::onDisconnect,
                        onRead = viewModel::onRead,
                        onWrite = viewModel::onWrite
                    )
                }
            }
        }
    }

    private fun ensurePermissionsAndScan() {
        val needsScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
        val needsConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (needsScan || needsConnect) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            viewModel.onScan()
        }
    }
}

@Composable
private fun MainScreen(
    state: BleUiState,
    onInputChange: (String) -> Unit,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
    onRead: () -> Unit,
    onWrite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .statusBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "ESP32 BLE Bridge",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDot(color = statusColorToCompose(state.statusColor))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = state.connectionState,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = state.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onScan,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.WifiTethering, contentDescription = "Scan")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan")
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Disconnect")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Disconnect")
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Last value",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.lastValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Text(
            text = "Send new value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = state.inputText,
            onValueChange = onInputChange,
            label = { Text("UInt32 (decimal or 0x...)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRead,
                modifier = Modifier.weight(1f)
            ) {
                Text("Read")
            }
            Button(
                onClick = onWrite,
                modifier = Modifier.weight(1f)
            ) {
                Text("Write")
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(12.dp)
            .background(color = color, shape = CircleShape)
    )
}

@Composable
private fun statusColorToCompose(statusColor: StatusColor): Color {
    return when (statusColor) {
        StatusColor.Green -> Color(0xFF4CAF50)
        StatusColor.Yellow -> Color(0xFFFFC107)
        StatusColor.Red -> Color(0xFFF44336)
        StatusColor.Gray -> Color(0xFF9E9E9E)
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    BLEESP32ComJPCTheme {
        MainScreen(
            state = BleUiState(
                connectionState = "Connected",
                statusMessage = "Ready",
                lastValue = "Last value: 42",
                statusColor = StatusColor.Green,
                inputText = "123"
            ),
            onInputChange = {},
            onScan = {},
            onDisconnect = {},
            onRead = {},
            onWrite = {}
        )
    }
}
