package io.github.kineks.mdnsserver.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kineks.mdnsserver.NetworkInterfaceInfo
import io.github.kineks.mdnsserver.R
import io.github.kineks.mdnsserver.ui.ServiceState
import io.github.kineks.mdnsserver.ui.ServiceStatusViewModel
import java.util.Collections

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(
    modifier: Modifier = Modifier,
    viewModel: ServiceStatusViewModel = viewModel()
) {
    val serviceState by viewModel.serviceState.collectAsState()
    val configState by viewModel.configurationState.collectAsState()

    var availableInterfaces by remember { mutableStateOf(emptyList<NetworkInterfaceInfo>()) }
    var showInterfaceDialog by remember { mutableStateOf(false) }

    // Local state for inputs to avoid jumping text while typing.
    // Initialized from configState but not reset by it to avoid loop/snapping.
    var serviceNameInput by rememberSaveable { mutableStateOf(configState.serviceName) }
    var portInput by rememberSaveable { mutableStateOf(configState.port.toString()) }

    LaunchedEffect(Unit) {
        availableInterfaces = viewModel.getAvailableInterfaces()
    }

    // Function to save all current values
    fun save() {
        viewModel.saveConfiguration(
            ip = configState.ipAddress, // IP updates via dialog
            port = portInput.toIntOrNull() ?: 8080,
            serviceName = serviceNameInput
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.config_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                StatusBanner(serviceState)
            }

            item {
                SettingsSectionTitle(stringResource(R.string.section_general))
            }

            item {
                SettingsTextField(
                    label = stringResource(R.string.label_service_name),
                    value = serviceNameInput,
                    onValueChange = {
                        serviceNameInput = it
                        save()
                    },
                    icon = Icons.Default.Info,
                    description = stringResource(R.string.desc_service_name)
                )
            }

            item {
                SettingsTextField(
                    label = stringResource(R.string.label_port),
                    value = portInput,
                    onValueChange = {
                        portInput = it
                        // Auto-save if valid number
                        if (it.toIntOrNull() != null) save()
                    },
                    icon = Icons.Default.Settings,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    description = stringResource(R.string.desc_port)
                )
            }

            item {
                SettingsSectionTitle(stringResource(R.string.section_network))
            }

            item {
                val currentInterface = availableInterfaces.find { it.ipAddress == configState.ipAddress }
                val displayValue = currentInterface?.let { "${it.displayName} (${it.ipAddress})" } ?: stringResource(R.string.interface_auto)

                SettingsItem(
                    headline = stringResource(R.string.label_interface),
                    supporting = displayValue,
                    icon = Icons.Default.NetworkWifi,
                    onClick = { showInterfaceDialog = true }
                )
            }

            item {
                PriorityListSection(
                    priorityList = configState.priorityList,
                    onUpdate = { viewModel.savePriorityList(it) }
                )
            }

        }
    }

    if (showInterfaceDialog) {
        InterfaceSelectionDialog(
            availableInterfaces = availableInterfaces,
            currentIp = configState.ipAddress,
            onDismiss = { showInterfaceDialog = false },
            onSelect = { ip ->
                viewModel.saveConfiguration(
                    ip = ip,
                    port = portInput.toIntOrNull() ?: 8080,
                    serviceName = serviceNameInput
                )
                showInterfaceDialog = false
            }
        )
    }
}

@Composable
fun StatusBanner(state: ServiceState) {
    val containerColor = when (state) {
        is ServiceState.Running -> MaterialTheme.colorScheme.primaryContainer
        is ServiceState.Stopped -> MaterialTheme.colorScheme.surfaceVariant
        is ServiceState.Starting -> MaterialTheme.colorScheme.tertiaryContainer
        is ServiceState.Stopping -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (state) {
        is ServiceState.Running -> MaterialTheme.colorScheme.onPrimaryContainer
        is ServiceState.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
        is ServiceState.Starting -> MaterialTheme.colorScheme.onTertiaryContainer
        is ServiceState.Stopping -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (state) {
        is ServiceState.Running -> Icons.Default.CheckCircle
        is ServiceState.Stopped -> Icons.Default.Warning
        else -> Icons.Default.Refresh
    }
    val text = when (state) {
        is ServiceState.Running -> stringResource(R.string.status_banner_active)
        is ServiceState.Stopped -> stringResource(R.string.status_banner_stopped)
        is ServiceState.Starting -> stringResource(R.string.status_banner_starting)
        is ServiceState.Stopping -> stringResource(R.string.status_banner_stopping)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}


@Composable
fun PriorityListSection(
    priorityList: List<String>,
    onUpdate: (List<String>) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSectionTitle(stringResource(R.string.section_priority))

        priorityList.forEachIndexed { index, item ->
            ListItem(
                headlineContent = { Text(item) },
                trailingContent = {
                    Row {
                        if (index > 0) {
                            IconButton(onClick = {
                                val newList = priorityList.toMutableList()
                                Collections.swap(newList, index, index - 1)
                                onUpdate(newList)
                            }) {
                                Icon(Icons.Default.ArrowUpward, "Move Up")
                            }
                        }
                        if (index < priorityList.size - 1) {
                            IconButton(onClick = {
                                val newList = priorityList.toMutableList()
                                Collections.swap(newList, index, index + 1)
                                onUpdate(newList)
                            }) {
                                Icon(Icons.Default.ArrowDownward, "Move Down")
                            }
                        }
                        IconButton(onClick = {
                            val newList = priorityList.toMutableList()
                            newList.removeAt(index)
                            onUpdate(newList)
                        }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }
                }
            )
        }

        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.btn_add_priority))
        }
    }

    if (showAddDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.dialog_add_prefix_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.label_prefix)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        val newList = priorityList.toMutableList()
                        newList.add(text.trim())
                        onUpdate(newList)
                    }
                    showAddDialog = false
                }) {
                    Text(stringResource(R.string.btn_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsItem(
    headline: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        ListItem(
            headlineContent = { Text(headline) },
            supportingContent = { Text(supporting) },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        )
    }
}

@Composable
fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    description: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = keyboardOptions,
            shape = RoundedCornerShape(16.dp),
            supportingText = if (description != null) { { Text(description) } } else null
        )
    }
}

@Composable
fun InterfaceSelectionDialog(
    availableInterfaces: List<NetworkInterfaceInfo>,
    currentIp: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_select_interface)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Auto Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentIp == null,
                        onClick = { onSelect(null) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.interface_auto),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                LazyColumn() {
                    items(availableInterfaces) { interfaceInfo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(interfaceInfo.ipAddress) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentIp == interfaceInfo.ipAddress,
                                onClick = { onSelect(interfaceInfo.ipAddress) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = interfaceInfo.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = interfaceInfo.ipAddress ?: "N/A",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
