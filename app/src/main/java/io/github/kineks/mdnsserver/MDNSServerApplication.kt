package io.github.kineks.mdnsserver

import android.app.Application
import android.content.Context
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
        
        // Initialize mDNS service registration
        mdnsServiceRegistration = MDNSServiceRegistration()
    }
    
    fun getMDNSServiceRegistration(): MDNSServiceRegistration {
        return mdnsServiceRegistration
    }
    
    // Get last used interface IP
    fun getLastUsedInterfaceIp(): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getString("last_used_ip", null)
    }

    fun setLastUsedInterfaceIp(ip: String?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putString("last_used_ip", ip).apply()
    }
    
    // Get last used port
    fun getLastUsedPort(): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getInt("last_used_port", 8080)
    }

    fun setLastUsedPort(port: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putInt("last_used_port", port).apply()
    }

    // Get last used service name
    fun getLastUsedServiceName(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getString("last_used_service_name", "sillytavern") ?: "sillytavern"
    }

    fun setLastUsedServiceName(serviceName: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putString("last_used_service_name", serviceName).apply()
    }

    // Interface Priority List
    fun getInterfacePriorityList(): List<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val string = prefs.getString("interface_priority_list", "wlan,eth,tether,tun") ?: "wlan,eth,tether,tun"
        return string.split(",").filter { it.isNotBlank() }
    }

    fun setInterfacePriorityList(list: List<String>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putString("interface_priority_list", list.joinToString(",")).apply()
    }
}
