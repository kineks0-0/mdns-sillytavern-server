package io.github.kineks.mdnsserver.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kineks.mdnsserver.ui.ServiceState
import io.github.kineks.mdnsserver.ui.ServiceStatusViewModel

@Composable
fun ServiceStatusScreen(
    modifier: Modifier = Modifier,
    viewModel: ServiceStatusViewModel = viewModel()
) {
    val serviceState by viewModel.serviceState.collectAsState()
    val configState by viewModel.configurationState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showTermuxDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Status Card
        StatusCard(serviceState)

        // Info Cards (Only if running)
        AnimatedVisibility(
            visible = serviceState is ServiceState.Running,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (serviceState is ServiceState.Running) {
                val state = serviceState as ServiceState.Running
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoCard(label = "Service Name", value = "${state.serviceName}.local")
                    InfoCard(label = "IP Address", value = state.ip ?: "Auto")
                    InfoCard(label = "Port", value = state.port.toString())
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (configState.showTermuxButton) {
            Button(
                onClick = {
                    if (viewModel.getTermuxSetupShown()) {
                        launchTermux(context, configState.termuxCommand)
                    } else {
                        showTermuxDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Start Termux Command")
            }
        }

        // Action Button
        StartStopButton(
            isRunning = serviceState is ServiceState.Running,
            onClick = {
                if (serviceState is ServiceState.Running) {
                    viewModel.stopServer()
                } else {
                    viewModel.startServer()
                }
            }
        )
    }

    if (showTermuxDialog) {
        AlertDialog(
            onDismissRequest = { showTermuxDialog = false },
            title = { Text("Termux Configuration") },
            text = {
                Column {
                    Text("To use this feature, you must enable external app access in Termux.")
                    Spacer(Modifier.height(8.dp))
                    Text("Run this command in Termux once:")
                    Spacer(Modifier.height(8.dp))
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            text = "mkdir -p ~/.termux && echo \"allow-external-apps=true\" >> ~/.termux/termux.properties && termux-reload-settings",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTermuxSetupShown(true)
                    showTermuxDialog = false
                    launchTermux(context, configState.termuxCommand)
                }) {
                    Text("I've done this")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString("mkdir -p ~/.termux && echo \"allow-external-apps=true\" >> ~/.termux/termux.properties && termux-reload-settings"))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy Command")
                }
            }
        )
    }
}

fun launchTermux(context: android.content.Context, command: String) {
    try {
        val intent = android.content.Intent("com.termux.action.RUN_COMMAND")
        intent.setClassName("com.termux", "com.termux.app.RunCommandService")
        intent.putExtra("com.termux.action.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
        intent.putExtra("com.termux.action.RUN_COMMAND_ARGUMENTS", arrayOf("-l", "-c", command))
        intent.putExtra("com.termux.action.RUN_COMMAND_BACKGROUND", false)
        context.startService(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to launch Termux: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun StatusCard(state: ServiceState) {
    val isRunning = state is ServiceState.Running
    val containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    val icon = if (isRunning) Icons.Default.CheckCircle else Icons.Default.Error
    val text = if (isRunning) "Active" else "Stopped"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun InfoCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StartStopButton(isRunning: Boolean, onClick: () -> Unit) {
    val containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val contentColor = if (isRunning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    val text = if (isRunning) "Stop Server" else "Start Server"
    val icon = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}
