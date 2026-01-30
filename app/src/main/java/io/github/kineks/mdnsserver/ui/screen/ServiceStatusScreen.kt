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
import androidx.compose.material.icons.filled.Refresh
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
            state = serviceState,
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
    val containerColor = when (state) {
        is ServiceState.Running -> MaterialTheme.colorScheme.primaryContainer
        is ServiceState.Stopped -> MaterialTheme.colorScheme.errorContainer
        is ServiceState.Starting -> MaterialTheme.colorScheme.tertiaryContainer
        is ServiceState.Stopping -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (state) {
        is ServiceState.Running -> MaterialTheme.colorScheme.onPrimaryContainer
        is ServiceState.Stopped -> MaterialTheme.colorScheme.onErrorContainer
        is ServiceState.Starting -> MaterialTheme.colorScheme.onTertiaryContainer
        is ServiceState.Stopping -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val icon = when (state) {
        is ServiceState.Running -> Icons.Default.CheckCircle
        is ServiceState.Stopped -> Icons.Default.Error
        else -> Icons.Default.Refresh
    }

    val text = when (state) {
        is ServiceState.Running -> stringResource(R.string.status_active)
        is ServiceState.Stopped -> stringResource(R.string.status_stopped)
        is ServiceState.Starting -> stringResource(R.string.status_starting)
        is ServiceState.Stopping -> stringResource(R.string.status_stopping)
    }

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
fun StartStopButton(state: ServiceState, onClick: () -> Unit) {
    val isRunning = state is ServiceState.Running
    val isTransitioning = state is ServiceState.Starting || state is ServiceState.Stopping

    val containerColor = when (state) {
        is ServiceState.Running -> MaterialTheme.colorScheme.error
        is ServiceState.Stopped -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (state) {
        is ServiceState.Running -> MaterialTheme.colorScheme.onError
        is ServiceState.Stopped -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val text = when (state) {
        is ServiceState.Running -> stringResource(R.string.btn_stop_server)
        is ServiceState.Stopped -> stringResource(R.string.btn_start_server)
        is ServiceState.Starting -> stringResource(R.string.btn_starting)
        is ServiceState.Stopping -> stringResource(R.string.btn_stopping)
    }

    val icon = when (state) {
        is ServiceState.Running -> Icons.Default.Stop
        is ServiceState.Stopped -> Icons.Default.PlayArrow
        else -> Icons.Default.Refresh
    }

    Button(
        onClick = onClick,
        enabled = !isTransitioning,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.6f),
            disabledContentColor = contentColor.copy(alpha = 0.6f)
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
