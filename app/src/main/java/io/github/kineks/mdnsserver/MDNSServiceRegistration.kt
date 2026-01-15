package io.github.kineks.mdnsserver

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

data class NetworkInterfaceInfo(
    val displayName: String,
    val name: String,
    val ipAddress: String?,
    val isActive: Boolean
)

class MDNSServiceRegistration {
    private var jmDNS: JmDNS? = null
    private val mutex = Mutex()
    private val TAG = "MDNSServiceRegistration"

    val jmDNSInstance get() = jmDNS

    /**
     * Register sillytavern.local service.
     */
    suspend fun registerSillyTavernService(port: Int, txtRecord: Map<String, String>? = null, ipAddress: String? = null) {
        registerCustomService("_http._tcp.local.", "sillytavern", port, txtRecord, ipAddress)
    }

    /**
     * Register a custom service.
     */
    suspend fun registerCustomService(
        serviceType: String,
        serviceName: String,
        port: Int,
        txtRecord: Map<String, String>? = null,
        ipAddress: String? = null
    ) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val inetAddress = if (ipAddress != null) {
                        InetAddress.getByName(ipAddress)
                    } else {
                        getFirstNonLoopbackAddress()
                    }

                    if (inetAddress != null) {
                        // Close existing JmDNS instance
                        cleanupJmDNS()

                        Log.i(TAG, "Creating JmDNS on ${inetAddress.hostAddress}")
                        // Create JmDNS instance
                        jmDNS = JmDNS.create(inetAddress, serviceName)

                        val txtRecords = txtRecord ?: mapOf("path" to "/")

                        val serviceInfo = ServiceInfo.create(
                            serviceType,
                            serviceName,
                            port,
                            0,
                            0,
                            txtRecords
                        )

                        jmDNS?.registerService(serviceInfo)
                        Log.i(TAG, "Registered service: $serviceName type: $serviceType on port: $port, address: ${inetAddress.hostAddress}")
                    } else {
                        Log.e(TAG, "Could not find a valid network interface for registration")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register service: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Unregister all services and close JmDNS.
     */
    suspend fun unregisterAllServices() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                cleanupJmDNS()
            }
        }
    }

    private fun cleanupJmDNS() {
        jmDNS?.let {
            try {
                Log.d(TAG, "Unregistering services and closing JmDNS")
                it.unregisterAllServices()
                it.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing JmDNS: ${e.message}")
            }
        }
        jmDNS = null
    }

    /**
     * Get list of available network interfaces.
     */
    fun getAvailableNetworkInterfaces(): List<NetworkInterfaceInfo> {
        val interfaces = mutableListOf<NetworkInterfaceInfo>()
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val netInterface = networkInterfaces.nextElement()
                if (netInterface.isLoopback || !netInterface.isUp) continue
                
                val addresses = netInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        interfaces.add(
                            NetworkInterfaceInfo(
                                displayName = netInterface.displayName ?: netInterface.name,
                                name = netInterface.name,
                                ipAddress = address.hostAddress,
                                isActive = netInterface.isUp
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network interfaces: ${e.message}", e)
        }
        return interfaces
    }

    private fun getFirstNonLoopbackAddress(): InetAddress? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val netInterface = interfaces.nextElement()
                if (netInterface.isLoopback || !netInterface.isUp) continue
                
                val addresses = netInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting first non-loopback address: ${e.message}", e)
        }
        return null
    }

    fun isInitialized(): Boolean {
        return jmDNS != null
    }
}
