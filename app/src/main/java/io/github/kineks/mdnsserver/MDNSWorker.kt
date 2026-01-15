package io.github.kineks.mdnsserver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

class MDNSWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val powerManagementUtils = PowerManagementUtils(context)
    private val mdnsServiceRegistration =
        (context.applicationContext as MDNSServerApplication).getMDNSServiceRegistration()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    companion object {
        const val CHANNEL_ID = "MDNSServiceChannel"
        const val NOTIFICATION_ID = 1
        const val TAG = "MDNSWorker"

        const val KEY_PORT = "port"
        const val KEY_IP_ADDRESS = "ip_address"
        const val KEY_SERVICE_NAME = "service_name"
    }

    override suspend fun doWork(): Result {
        val port = inputData.getInt(KEY_PORT, 8080)
        val ipAddress = inputData.getString(KEY_IP_ADDRESS)
        val serviceName = inputData.getString(KEY_SERVICE_NAME) ?: "sillytavern"

        Log.d(TAG, "Starting MDNSWorker for $serviceName on port $port")

        // Create notification channel
        createNotificationChannel()

        // Set foreground
        setForeground(createForegroundInfo(serviceName, port, ipAddress))

        // Acquire WakeLock
        powerManagementUtils.acquireWakeLock()

        try {
            // Register initial service
            registerService(port, ipAddress, serviceName)

            // Register network callback
            registerNetworkCallback(port, serviceName, ipAddress)

            // Keep alive
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in MDNSWorker", e)
            return Result.failure()
        } finally {
            Log.d(TAG, "Stopping MDNSWorker")
            unregisterNetworkCallback()
            mdnsServiceRegistration.unregisterAllServices()
            powerManagementUtils.releaseWakeLock()
        }

        return Result.success()
    }

    private suspend fun registerService(port: Int, ipAddress: String?, serviceName: String) {
        if (isStopped) return
        mdnsServiceRegistration.registerCustomService(
            "_http._tcp.local.",
            serviceName,
            port,
            mapOf("path" to "/", "version" to "1.0", "service" to serviceName),
            ipAddress
        )

        // Report progress
        val currentIp = mdnsServiceRegistration.jmDNSInstance?.inetAddress?.hostAddress ?: ipAddress
        setProgress(androidx.work.workDataOf(
            KEY_IP_ADDRESS to currentIp,
            KEY_PORT to port,
            KEY_SERVICE_NAME to serviceName
        ))

        // Update notification to reflect current state (or IP changes)
        notificationManager.notify(NOTIFICATION_ID, createNotification(serviceName, port, currentIp))
    }

    private fun registerNetworkCallback(port: Int, serviceName: String, targetIp: String?) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (isStopped) return
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        registerService(port, targetIp, serviceName)
                    }
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
                if (isStopped) return
                CoroutineScope(Dispatchers.IO).launch {
                    registerService(port, targetIp, serviceName)
                }
            }
        }
        networkCallback = callback

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(TAG, "Network callback unregistration failed: ${e.message}")
            }
        }
        networkCallback = null
    }

    private fun createNotification(serviceName: String, port: Int, ipAddress: String?): android.app.Notification {
        val notificationIntent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.notification_title))
            .setContentText("$serviceName : $port ${ipAddress ?: ""}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return builder.build()
    }

    private fun createForegroundInfo(serviceName: String, port: Int, ipAddress: String?): ForegroundInfo {
        val notification = createNotification(serviceName, port, ipAddress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.notification_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
