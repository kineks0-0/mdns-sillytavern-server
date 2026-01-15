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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ServiceStatusViewModel(application: Application) : AndroidViewModel(application) {
    private val workManager = WorkManager.getInstance(application)
    private val mdnsApplication = application as MDNSServerApplication

    val serviceState: StateFlow<ServiceState> = workManager
        .getWorkInfosForUniqueWorkFlow(WORK_NAME)
        .map { workInfoList ->
            val workInfo = workInfoList.firstOrNull()
            if (workInfo != null && workInfo.state == WorkInfo.State.RUNNING) {
                val progress = workInfo.progress
                val ip = progress.getString(MDNSWorker.KEY_IP_ADDRESS)
                val port = progress.getInt(MDNSWorker.KEY_PORT, 8080)
                ServiceState.Running(ip, port)
            } else {
                ServiceState.Stopped
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServiceState.Stopped)

    fun startServer() {
        val port = mdnsApplication.getLastUsedPort()
        val serviceName = "sillytavern"
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

    companion object {
        const val WORK_NAME = "mdns_service_work"
        const val WORK_TAG = "mdns_service"
    }
}

sealed class ServiceState {
    data object Stopped : ServiceState()
    data class Running(val ip: String?, val port: Int) : ServiceState()
}
