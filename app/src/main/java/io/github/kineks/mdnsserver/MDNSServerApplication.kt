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
    
    // Get last used port
    fun getLastUsedPort(): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getInt("last_used_port", 8080)
    }
}
