package io.github.kineks.mdnsserver.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import io.github.kineks.mdnsserver.MDNSServiceRegistration
import io.github.kineks.mdnsserver.NetworkInterfaceInfo
import io.github.kineks.mdnsserver.ui.ServiceState
import io.github.kineks.mdnsserver.ui.ServiceStatusViewModel

/**
 * Network Interface Selector Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkInterfaceSelectorScreen(
    mdnsServiceRegistration: MDNSServiceRegistration?,
    viewModel: ServiceStatusViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedInterface by remember { mutableStateOf<NetworkInterfaceInfo?>(null) }
    var availableInterfaces by remember { mutableStateOf(emptyList<NetworkInterfaceInfo>()) }
    var port by rememberSaveable { mutableStateOf("8080") }

    val serviceState by viewModel.serviceState.collectAsState()
    val isRunning = serviceState is ServiceState.Running
    val context = LocalContext.current

    Surface(modifier = modifier) {
        if (mdnsServiceRegistration != null) {

            // Try to auto-select last used interface
            LaunchedEffect(Unit) {
                availableInterfaces = mdnsServiceRegistration.getAvailableNetworkInterfaces()
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val lastUsedIp = prefs.getString("last_used_ip", null)
                val lastUsedPort = prefs.getInt("last_used_port", 8080)

                if (lastUsedIp != null) {
                    val savedInterface = availableInterfaces.find { it.ipAddress == lastUsedIp }
                    if (savedInterface != null) {
                        selectedInterface = savedInterface
                        port = lastUsedPort.toString()
                    }
                } else {
                     port = lastUsedPort.toString()
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = selectedInterface?.displayName ?: "Auto (Default)",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        // Option for Auto
                        DropdownMenuItem(
                            text = { Text("Auto (Default)") },
                            onClick = {
                                selectedInterface = null
                                expanded = false
                            }
                        )

                        availableInterfaces.forEach { interfaceInfo ->
                            DropdownMenuItem(
                                text = {
                                    Text("${interfaceInfo.displayName} (${interfaceInfo.ipAddress})")
                                },
                                onClick = {
                                    selectedInterface = interfaceInfo
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                TextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.padding(top = 16.dp)
                )

                Button(
                    onClick = {
                        // Save preferences
                        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                        prefs.edit()
                            .putString("last_used_ip", selectedInterface?.ipAddress) // null for auto
                            .putInt("last_used_port", port.toIntOrNull() ?: 8080)
                            .apply()

                        // Restart or Start
                        viewModel.startServer()
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(if (isRunning) "Restart Service" else "Start Service")
                }

                if (isRunning) {
                    Button(
                        onClick = {
                            viewModel.stopServer()
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Stop Service")
                    }
                }

                // Show status
                Text(
                    text = if (isRunning) "Service is running in background" else "Service is stopped",
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
