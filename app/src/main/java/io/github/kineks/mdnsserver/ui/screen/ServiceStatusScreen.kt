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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.kineks.mdnsserver.R
import io.github.kineks.mdnsserver.ui.ServiceState
import io.github.kineks.mdnsserver.ui.ServiceStatusViewModel

@Composable
fun ServiceStatusScreen(
    modifier: Modifier = Modifier,
    viewModel: ServiceStatusViewModel = viewModel()
) {
    val serviceState by viewModel.serviceState.collectAsState()

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
                    InfoCard(label = stringResource(R.string.info_service_name), value = "${state.serviceName}.local")
                    InfoCard(label = stringResource(R.string.info_ip_address), value = state.ip ?: stringResource(R.string.interface_auto))
                    InfoCard(label = stringResource(R.string.info_port), value = state.port.toString())
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

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
}

@Composable
fun StatusCard(state: ServiceState) {
    val isRunning = state is ServiceState.Running
    val containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    val icon = if (isRunning) Icons.Default.CheckCircle else Icons.Default.Error
    val text = if (isRunning) stringResource(R.string.status_active) else stringResource(R.string.status_stopped)

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
    val text = if (isRunning) stringResource(R.string.btn_stop_server) else stringResource(R.string.btn_start_server)
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
