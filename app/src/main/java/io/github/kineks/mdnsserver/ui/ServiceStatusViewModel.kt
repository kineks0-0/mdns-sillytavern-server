package io.github.kineks.mdnsserver.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.kineks.mdnsserver.MDNSServerApplication
import io.github.kineks.mdnsserver.MDNSWorker
import io.github.kineks.mdnsserver.NetworkInterfaceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ServiceStatusViewModel(application: Application) : AndroidViewModel(application) {
    private val workManager = WorkManager.getInstance(application)
    private val mdnsApplication = application as MDNSServerApplication

    val serviceState: StateFlow<ServiceState> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME),
        mdnsApplication.getMDNSServiceRegistration().isRunning
    ) { workInfoList, isRunning ->
        val workInfo = workInfoList.firstOrNull()
        if (workInfo != null && workInfo.state == WorkInfo.State.RUNNING) {
            val progress = workInfo.progress
            val ip = progress.getString(MDNSWorker.KEY_IP_ADDRESS)
            val port = progress.getInt(MDNSWorker.KEY_PORT, 8080)
            val serviceName = progress.getString(MDNSWorker.KEY_SERVICE_NAME) ?: "sillytavern"
            ServiceState.Running(ip, port, serviceName)
        } else if (workInfo != null && (workInfo.state == WorkInfo.State.CANCELLED || workInfo.state == WorkInfo.State.FAILED || workInfo.state == WorkInfo.State.SUCCEEDED)) {
            ServiceState.Stopped
        } else if (isRunning) {
            val ip = mdnsApplication.getLastUsedInterfaceIp()
            val port = mdnsApplication.getLastUsedPort()
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
        // Reload settings from prefs just in case, or trust the UI updated prefs via saveConfiguration
        val port = mdnsApplication.getLastUsedPort()
        val serviceName = mdnsApplication.getLastUsedServiceName()
        val ipAddress = mdnsApplication.getLastUsedInterfaceIp()

        val data = workDataOf(
            MDNSWorker.KEY_PORT to port,
            MDNSWorker.KEY_IP_ADDRESS to ipAddress,
            MDNSWorker.KEY_SERVICE_NAME to serviceName
        )

        val workRequest = OneTimeWorkRequestBuilder<MDNSWorker>()
            .setInputData(data)
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun stopServer() {
        workManager.cancelUniqueWork(WORK_NAME)
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

    companion object {
        const val WORK_NAME = "mdns_service_work"
        const val WORK_TAG = "mdns_service"
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
