package io.github.kineks.mdnsserver.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kineks.mdnsserver.MDNSServerApplication
import io.github.kineks.mdnsserver.MDNSService
import io.github.kineks.mdnsserver.NetworkInterfaceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ServiceStatusViewModel(application: Application) : AndroidViewModel(application) {
    private val mdnsApplication = application as MDNSServerApplication

    val serviceState: StateFlow<ServiceState> = mdnsApplication.getMDNSServiceRegistration().isRunning
        .map { isRunning ->
            if (isRunning) {
                // Since we don't have direct access to the active IP/Port from the Service here easily without binding or shared prefs/bus,
                // we fall back to the "last used" or re-querying registration if we exposed it.
                // For now, assuming successful start uses the configured values is "okay" but strictly we should query the service.
                // Ideally MDNSServiceRegistration would expose the current run config.
                // However, MDNSServiceRegistration stores the JmDNS instance which has the address.

                ServiceState.Running("N/A", -1, "serviceName")

                val jmdns = mdnsApplication.getMDNSServiceRegistration().jmDNSInstance
                val ip = jmdns?.inetAddress?.hostAddress ?: mdnsApplication.getLastUsedInterfaceIp()
                val port = mdnsApplication.getLastUsedPort() // This is approximate if changed while running but acceptable for now
                val serviceName = mdnsApplication.getLastUsedServiceName()

                ServiceState.Running(ip, port, serviceName)
            } else {
                ServiceState.Stopped
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServiceState.Stopped)

    // Configuration state for UI
    private val _configurationState = MutableStateFlow(
        ConfigurationState(
            ipAddress = mdnsApplication.getLastUsedInterfaceIp(),
            port = mdnsApplication.getLastUsedPort(),
            serviceName = mdnsApplication.getLastUsedServiceName(),
            priorityList = mdnsApplication.getInterfacePriorityList()
        )
    )
    val configurationState = _configurationState.asStateFlow()

    fun startServer() {
        val port = mdnsApplication.getLastUsedPort()
        val serviceName = mdnsApplication.getLastUsedServiceName()
        val ipAddress = mdnsApplication.getLastUsedInterfaceIp()

        val intent = Intent(getApplication(), MDNSService::class.java).apply {
            action = MDNSService.ACTION_START
            putExtra(MDNSService.KEY_PORT, port)
            putExtra(MDNSService.KEY_IP_ADDRESS, ipAddress)
            putExtra(MDNSService.KEY_SERVICE_NAME, serviceName)
        }

        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun stopServer() {
        val intent = Intent(getApplication(), MDNSService::class.java).apply {
            action = MDNSService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    fun saveConfiguration(ip: String?, port: Int, serviceName: String) {
        mdnsApplication.setLastUsedInterfaceIp(ip)
        mdnsApplication.setLastUsedPort(port)
        mdnsApplication.setLastUsedServiceName(serviceName)

        updateConfigState()
    }

    fun savePriorityList(list: List<String>) {
        mdnsApplication.setInterfacePriorityList(list)
        updateConfigState()
    }

    private fun updateConfigState() {
        _configurationState.value = ConfigurationState(
            ipAddress = mdnsApplication.getLastUsedInterfaceIp(),
            port = mdnsApplication.getLastUsedPort(),
            serviceName = mdnsApplication.getLastUsedServiceName(),
            priorityList = mdnsApplication.getInterfacePriorityList()
        )
    }

    fun getAvailableInterfaces(): List<NetworkInterfaceInfo> {
        return mdnsApplication.getMDNSServiceRegistration().getAvailableNetworkInterfaces()
    }
}

sealed class ServiceState {
    data object Stopped : ServiceState()
    data class Running(val ip: String?, val port: Int, val serviceName: String) : ServiceState()
}

data class ConfigurationState(
    val ipAddress: String?,
    val port: Int,
    val serviceName: String,
    val priorityList: List<String>
)
