package io.github.kineks.mdnsserver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

class MDNSService : Service() {
    private val CHANNEL_ID = "MDNSServiceChannel"
    private val NOTIFICATION_ID = 1
    
    private lateinit var mdnsServiceRegistration: MDNSServiceRegistration
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var powerManagementUtils: PowerManagementUtils
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    companion object {
        const val TAG = "MDNSService"
    }

    override fun onCreate() {
        super.onCreate()
        mdnsServiceRegistration = MDNSServiceRegistration()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        powerManagementUtils = PowerManagementUtils(this)
        createNotificationChannel()
        
        // 获取唤醒锁以确保在屏幕关闭时服务仍然运行
        powerManagementUtils.acquireWakeLock()
        
        // 注册网络变化监听器
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        val port = intent?.getIntExtra("port", 8080) ?: 8080
        val ipAddress = intent?.getStringExtra("ip_address")
        val serviceName = intent?.getStringExtra("service_name") ?: "sillytavern"
        
        // 启动通知
        val notification = createNotification(serviceName, port, ipAddress).build()
        startForeground(NOTIFICATION_ID, notification)

        Log.d(TAG, "启动mDNS服务，端口: $port, IP: $ipAddress, 服务名: $serviceName")

        // 注册mDNS服务
        if (ipAddress != null) {
            mdnsServiceRegistration.registerCustomService(
                "_http._tcp.local.",
                serviceName,
                port,
                mapOf("path" to "/", "version" to "1.0", "service" to serviceName),
                ipAddress
            )
        } else {
            // 如果没有指定IP，则使用自动检测的IP
            mdnsServiceRegistration.registerSillyTavernService(
                port,
                mapOf("path" to "/", "version" to "1.0", "service" to serviceName)
            )
        }

        // 保存当前使用的接口和端口
        sharedPreferences.edit().apply {
            putString("last_used_ip", ipAddress)
            putInt("last_used_port", port)
            apply()
        }

        return START_STICKY // 确保服务被系统重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "MDNSService destroyed")
        mdnsServiceRegistration.unregisterAllServices()
        // 注销网络回调
        unregisterNetworkCallback()
        // 释放唤醒锁
        powerManagementUtils.releaseWakeLock()
        coroutineScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "应用任务被移除，重新启动服务")
        // 当应用从最近任务列表中被清除时，重新启动服务
        val restartServiceIntent = Intent(this, MDNSService::class.java)
        restartServiceIntent.putExtra("port", getLastUsedPort())
        restartServiceIntent.putExtra("ip_address", getLastUsedInterfaceIp())
        restartServiceIntent.putExtra("service_name", "sillytavern")
        startForegroundService(restartServiceIntent)
    }

    private fun createNotification(serviceName: String, port: Int, ipAddress: String?): NotificationCompat.Builder {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("mDNS服务运行中")
            .setContentText("服务: $serviceName, 端口: $port, IP: $ipAddress")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 设置为持续通知，防止被清除
            .setPriority(NotificationCompat.PRIORITY_LOW) // 使用低优先级以减少干扰

        return builder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "mDNS服务通道",
                NotificationManager.IMPORTANCE_LOW // 不需要高优先级打扰用户
            ).apply {
                description = "用于mDNS服务广播的通知通道"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    // 获取保存的最后使用的接口IP
    fun getLastUsedInterfaceIp(): String? {
        return sharedPreferences.getString("last_used_ip", null)
    }
    
    // 获取保存的最后使用的端口
    fun getLastUsedPort(): Int {
        return sharedPreferences.getInt("last_used_port", 8080)
    }
    
    // 注册网络变化监听器
    private fun registerNetworkCallback() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED) {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    //Log.d(TAG, "网络可用，重新注册mDNS服务")
                    // 网络变化时重新注册服务
                    //reRegisterServices()
                }
                
                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.d(TAG, "网络丢失")
                }
                
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    Log.d(TAG, "网络能力改变")
                    // 检查是否有Internet访问能力
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        Log.d(TAG, "网络具有Internet访问能力，重新注册mDNS服务")
                        reRegisterServices()
                    }
                }
            }
            
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()
            
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        }
    }
    
    // 注销网络变化监听器
    private fun unregisterNetworkCallback() {
        networkCallback?.let { callback ->
            try {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "网络回调未注册或已被注销: ${e.message}")
            }
        }
        networkCallback = null
    }
    
    // 重新注册服务
    private fun reRegisterServices() {
        val port = getLastUsedPort()
        val ipAddress = getLastUsedInterfaceIp()
        val serviceName = "sillytavern"
        
        Log.d(TAG, "重新注册服务: $serviceName, 端口: $port, IP: $ipAddress")
        
        if (ipAddress != null) {
            mdnsServiceRegistration.registerCustomService(
                "_http._tcp.local.",
                serviceName,
                port,
                mapOf("path" to "/", "version" to "1.0", "service" to serviceName),
                ipAddress
            )
        } else {
            mdnsServiceRegistration.registerSillyTavernService(
                port,
                mapOf("path" to "/", "version" to "1.0", "service" to serviceName)
            )
        }
    }
}