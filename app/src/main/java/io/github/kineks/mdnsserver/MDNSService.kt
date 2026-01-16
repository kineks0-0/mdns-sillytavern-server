package io.github.kineks.mdnsserver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MDNSService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private lateinit var powerManagementUtils: PowerManagementUtils
    private lateinit var mdnsServiceRegistration: MDNSServiceRegistration
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isStarted = false

    companion object {
        const val ACTION_START = "io.github.kineks.mdnsserver.action.START"
        const val ACTION_STOP = "io.github.kineks.mdnsserver.action.STOP"

        const val KEY_PORT = "port"
        const val KEY_IP_ADDRESS = "ip_address"
        const val KEY_SERVICE_NAME = "service_name"

        const val CHANNEL_ID = "MDNSServiceChannel"
        const val NOTIFICATION_ID = 1
        const val TAG = "MDNSService"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        powerManagementUtils = PowerManagementUtils(this)
        mdnsServiceRegistration = (applicationContext as MDNSServerApplication).getMDNSServiceRegistration()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(KEY_PORT, 8080)
                val ipAddress = intent.getStringExtra(KEY_IP_ADDRESS)
                val serviceName = intent.getStringExtra(KEY_SERVICE_NAME) ?: "sillytavern"

                startForegroundService(serviceName, port, ipAddress)
            }
            ACTION_STOP -> {
                stopForegroundService()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundService(serviceName: String, port: Int, ipAddress: String?) {
        Log.d(TAG, "Starting MDNSService for $serviceName on port $port")

        val notification = createNotification(serviceName, port, ipAddress)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!isStarted) {
            powerManagementUtils.acquireWakeLock()
            isStarted = true
        }

        serviceScope.launch(Dispatchers.IO) {
             try {
                // Register initial service
                registerService(port, ipAddress, serviceName)
                // Register network callback
                registerNetworkCallback(port, serviceName, ipAddress)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting service", e)
                stopForegroundService()
            }
        }
    }

    private fun stopForegroundService() {
        Log.d(TAG, "Stopping MDNSService")
        serviceScope.launch(Dispatchers.IO) {
            isStarted = false
            unregisterNetworkCallback()
            //mdnsServiceRegistration.unregisterAllServices()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        // Ensure cleanup happens if destroyed by system
        CoroutineScope(Dispatchers.IO).launch {
             mdnsServiceRegistration.unregisterAllServices()
        }
        if (powerManagementUtils.isWakeLockHeld()) {
            powerManagementUtils.releaseWakeLock()
        }
        isStarted = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private suspend fun registerService(port: Int, ipAddress: String?, serviceName: String) {
        mdnsServiceRegistration.registerCustomService(
            "_http._tcp.local.",
            serviceName,
            port,
            mapOf("path" to "/", "version" to "1.0", "service" to serviceName),
            ipAddress
        )

        // Update notification
        val currentIp = mdnsServiceRegistration.jmDNSInstance?.inetAddress?.hostAddress ?: ipAddress
        notificationManager.notify(NOTIFICATION_ID, createNotification(serviceName, port, currentIp))
    }

    private fun registerNetworkCallback(port: Int, serviceName: String, targetIp: String?) {
        if (networkCallback != null) return // Already registered

        val callback = object : ConnectivityManager.NetworkCallback() {
            /*override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    serviceScope.launch(Dispatchers.IO) {
                        if (isStarted)
                            registerService(port, targetIp, serviceName)
                    }
                }
            }*/

            override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
                serviceScope.launch(Dispatchers.IO) {
                    if (isStarted)
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
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        return builder.build()
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
