package io.github.kineks.mdnsserver

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager

class MDNSServerApplication : Application() {
    companion object {
        private var instance: MDNSServerApplication? = null
        const val TAG = "MDNSServerApplication"
        
        fun getContext(): Context? = instance?.applicationContext
    }
    
    private lateinit var mdnsServiceRegistration: MDNSServiceRegistration
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化mDNS服务注册器
        mdnsServiceRegistration = MDNSServiceRegistration()
    }
    
    fun getMDNSServiceRegistration(): MDNSServiceRegistration {
        return mdnsServiceRegistration
    }
    
    // 启动mDNS后台服务
    fun startMDNSService(port: Int = 8080, ipAddress: String? = null, serviceName: String = "sillytavern") {
        Log.d(TAG, "启动mDNS服务，端口: $port, IP: $ipAddress, 服务名: $serviceName")
        val intent = Intent(this, MDNSService::class.java).apply {
            putExtra("port", port)
            putExtra("ip_address", ipAddress)
            putExtra("service_name", serviceName)
        }
        try {
            startForegroundService(intent)
        } catch (ex: Exception) {
            Log.e(TAG, "启动前台服务失败: ${ex.message}", ex)
            // 在某些Android版本上，如果服务已经在运行，可能会抛出异常，尝试普通启动
            if (!isServiceForegrounded(MDNSService::class.java)) {
                startService(intent)
            }
        }
    }
    
    // 停止mDNS后台服务
    fun stopMDNSService() {
        Log.d(TAG, "停止mDNS服务")
        val intent = Intent(this, MDNSService::class.java)
        stopService(intent)
    }
    
    // 检查服务是否正在运行
    fun isServiceRunning(serviceClass: Class<*>): Boolean {
        return ServiceUtils.isServiceRunning(this, serviceClass)
    }
    
    // 检查服务是否在前台运行
    fun isServiceForegrounded(serviceClass: Class<*>): Boolean {
        return ServiceUtils.isServiceForegrounded(this, serviceClass)
    }
    
    // 获取最后使用的接口IP
    fun getLastUsedInterfaceIp(): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getString("last_used_ip", null)
    }
    
    // 获取最后使用的端口
    fun getLastUsedPort(): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getInt("last_used_port", 8080)
    }
    
    // 重启mDNS服务
    fun restartMDNSService() {
        Log.d(TAG, "重启mDNS服务")
        stopMDNSService()
        // 延迟一段时间再启动服务，确保完全停止
        Thread {
            try {
                Thread.sleep(1000) // 延迟1秒
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            val lastUsedPort = getLastUsedPort()
            val lastUsedIp = getLastUsedInterfaceIp()
            startMDNSService(lastUsedPort, lastUsedIp)
        }.start()
    }
}