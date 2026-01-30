package io.github.kineks.mdnsserver

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import javax.jmdns.impl.JmDNSImpl
import javax.jmdns.impl.PatchedSocketListener

data class NetworkInterfaceInfo(
    val displayName: String,
    val name: String,
    val ipAddress: String?,
    val isActive: Boolean
)

class MDNSServiceRegistration {
    private var jmDNS: JmDNS? = null
    private val mutex = Mutex()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    val hostAddress get() = jmDNS?.inetAddress?.hostAddress

    companion object {
        private const val TAG = "MDNSServiceRegistration"
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
                        jmDNS = runInterruptible {
                            JmDNS.create(inetAddress, serviceName)
                        }

                        // Apply patch for Android Legacy Unicast Response
                        jmDNS?.let { applyJmDNSPatch(it) }

                        val txtRecords = txtRecord ?: mapOf("path" to "/")

                        val serviceInfo = ServiceInfo.create(
                            serviceType,
                            serviceName,
                            port,
                            0,
                            0,
                            txtRecords
                        )

                        runInterruptible {
                            jmDNS?.registerService(serviceInfo)
                        }
                        _isRunning.value = true
                        Log.i(TAG, "Registered service: $serviceName type: $serviceType on port: $port, address: ${inetAddress.hostAddress}")
                    } else {
                        Log.e(TAG, "Could not find a valid network interface for registration")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register service: ${e.message}", e)
                    _isRunning.value = false
                }
            }
        }
    }

    /**
     * Unregister all services and close JmDNS.
     */
    suspend fun unregisterAllServices() {
        mutex.withLock {
            Log.d(TAG, "Unregistering all services")
            withContext(Dispatchers.IO) {
                _isRunning.value = false
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

    private fun getPriorityList(): List<String> {
        val app = MDNSServerApplication.getContext() as? MDNSServerApplication
        return app?.getInterfacePriorityList() ?: emptyList()
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
                    if ((address is Inet4Address || address is Inet6Address)&& !address.isLoopbackAddress) {
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

        val priorityList = getPriorityList()
        return interfaces.sortedBy { info ->
            val idx = priorityList.indexOfFirst {
                info.name.startsWith(it, ignoreCase = true) || info.displayName.startsWith(it, ignoreCase = true)
            }
            if (idx == -1) Int.MAX_VALUE else idx
        }
    }

    private fun getFirstNonLoopbackAddress(): InetAddress? {
        val interfaces = getAvailableNetworkInterfaces()
        val bestInterface = interfaces.firstOrNull() ?: return null
        return try {
            InetAddress.getByName(bestInterface.ipAddress)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating InetAddress from ${bestInterface.ipAddress}: ${e.message}")
            null
        }
    }

    private fun applyJmDNSPatch(jmdnsInstance: JmDNS) {
        try {
            if (jmdnsInstance is JmDNSImpl) {
                val impl = jmdnsInstance
                synchronized(impl) {
                    Log.i(TAG, "Applying JmDNS Legacy Unicast Patch...")

                    // 1. Set state to CLOSING to prevent original listener from triggering recover()
                    // Use reflection for robustness
                    try {
                        val closeStateMethod = JmDNSImpl::class.java.getMethod("closeState")
                        closeStateMethod.invoke(impl)
                    } catch (e: NoSuchMethodException) {
                        val closeStateMethod = JmDNSImpl::class.java.getDeclaredMethod("closeState")
                        closeStateMethod.isAccessible = true
                        closeStateMethod.invoke(impl)
                        Log.e(TAG, "Error getting closeStateMethod: ${e.message}")
                        e.printStackTrace()
                    }

                    // 2. Close the multicast socket (stops the original listener thread)
                    val closeSocketMethod = JmDNSImpl::class.java.getDeclaredMethod("closeMulticastSocket")
                    closeSocketMethod.isAccessible = true
                    closeSocketMethod.invoke(impl)

                    // Get LocalHost via reflection to be safe
                    val localHostField = JmDNSImpl::class.java.getDeclaredField("_localHost")
                    localHostField.isAccessible = true
                    val localHostObj = localHostField.get(impl)

                    // 3. Re-open the multicast socket
                    val openSocketMethod = JmDNSImpl::class.java.getDeclaredMethod("openMulticastSocket", localHostObj.javaClass)
                    openSocketMethod.isAccessible = true
                    openSocketMethod.invoke(impl, localHostObj)

                    // 4. Recover state (back to PROBING/ANNOUNCED)
                    try {
                        val recoverStateMethod = JmDNSImpl::class.java.getMethod("recoverState")
                        recoverStateMethod.invoke(impl)
                    } catch (e: NoSuchMethodException) {
                        val recoverStateMethod = JmDNSImpl::class.java.getDeclaredMethod("recoverState")
                        recoverStateMethod.isAccessible = true
                        recoverStateMethod.invoke(impl)
                        Log.e(TAG, "Error getting recoverStateMethod: ${e.message}")
                        e.printStackTrace()
                    }

                    // 5. Inject and start the patched listener
                    val listenerField = JmDNSImpl::class.java.getDeclaredField("_incomingListener")
                    listenerField.isAccessible = true
                    val patchedListener = PatchedSocketListener(impl)
                    listenerField.set(impl, patchedListener)
                    patchedListener.start()

                    Log.i(TAG, "JmDNS Legacy Unicast Patch Applied Successfully")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply JmDNS patch", e)
        }
    }

}