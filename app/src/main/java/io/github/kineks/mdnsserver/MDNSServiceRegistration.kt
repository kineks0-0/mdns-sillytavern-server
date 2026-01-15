package io.github.kineks.mdnsserver

import android.util.Log
import kotlinx.coroutines.*
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
    private val TAG = "MDNSServiceRegistration"

    /**
     * 注册sillytavern.local服务
     * @param port 端口号
     * @param txtRecord 附加信息
     * @param ipAddress 特定IP地址，如果提供则使用此IP，否则自动选择
     */
    fun registerSillyTavernService(port: Int, txtRecord: Map<String, String>? = null, ipAddress: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            registerSillyTavernServiceInternal(port, txtRecord, ipAddress)
        }
    }

    /**
     * 内部方法，执行实际的注册逻辑
     */
    private fun registerSillyTavernServiceInternal(port: Int, txtRecord: Map<String, String>? = null, ipAddress: String? = null) {
        try {
            val inetAddress = if (ipAddress != null) {
                InetAddress.getByName(ipAddress)
            } else {
                getFirstNonLoopbackAddress()
            }
            
            if (inetAddress != null) {
                // 关闭现有的JmDNS实例
                jmDNS?.let {
                    try {
                        it.unregisterAllServices()
                        it.close()
                    } catch (e: IOException) {
                        Log.e(TAG, "关闭旧的JmDNS实例时出错: ${e.message}")
                    }
                }
                
                // 创建JmDNS实例
                jmDNS = JmDNS.create(inetAddress, "sillytavern")
                
                // 准备服务信息的TXT记录
                val txtRecords = txtRecord ?: mapOf("path" to "/")

                // 创建服务信息，使用_http._tcp协议，这样可以在浏览器中访问
                val serviceInfo = ServiceInfo.create(
                    "_http._tcp.local.",  // 服务类型
                    "sillytavern",        // 服务名称（不包含.local.后缀，JmDNS会自动添加）
                    port,                 // 端口
                    0,                    // 优先级
                    0,                    // 权重
                    txtRecords            // 附加信息
                )

                // 注册服务
                jmDNS!!.registerService(serviceInfo)

                
                println("已注册服务: ${jmDNS!!.name} 在端口: $port, 地址: ${inetAddress.hostAddress}")
                Log.d(TAG, "已注册服务: ${jmDNS!!.name} 在端口: $port, 地址: ${inetAddress.hostAddress}")
            } else {
                Log.e(TAG, "无法找到有效的网络接口")
                println("无法找到有效的网络接口")
            }
        } catch (e: IOException) {
            Log.e(TAG, "注册sillytavern服务失败: ${e.message}", e)
            e.printStackTrace()
            println("注册sillytavern服务失败: ${e.message}")
        }
    }

    /**
     * 获取所有可用的网络接口列表
     */
    fun getAvailableNetworkInterfaces(): List<NetworkInterfaceInfo> {
        val interfaces = mutableListOf<NetworkInterfaceInfo>()
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val netInterface = networkInterfaces.nextElement()
                
                // 跳过回环和禁用的接口
                if (netInterface.isLoopback || !netInterface.isUp) continue
                
                val addresses = netInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // 只使用IPv4地址
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
            Log.e(TAG, "获取网络接口列表时出错: ${e.message}", e)
            e.printStackTrace()
        }
        
        return interfaces
    }

    /**
     * 获取指定网络接口的IP地址
     */
    fun getIpAddressForInterface(interfaceName: String): String? {
        try {
            val netInterface = NetworkInterface.getByName(interfaceName)
            if (netInterface != null && netInterface.isUp && !netInterface.isLoopback) {
                val addresses = netInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取指定网络接口IP时出错: ${e.message}", e)
            e.printStackTrace()
        }
        
        return null
    }

    /**
     * 获取第一个非回环网络接口地址
     */
    private fun getFirstNonLoopbackAddress(): InetAddress? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val netInterface = interfaces.nextElement()
                
                // 跳过回环和禁用的接口
                if (netInterface.isLoopback || !netInterface.isUp) continue
                
                val addresses = netInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // 只使用IPv4地址
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        Log.d(TAG, "使用网络接口: ${netInterface.name}, IP: ${address.hostAddress}")
                        return address
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取第一个非回环地址时出错: ${e.message}", e)
            e.printStackTrace()
        }
        
        return null
    }

    /**
     * 注销所有服务并清理资源
     */
    fun unregisterAllServices() {
        try {
            jmDNS?.unregisterAllServices()
            jmDNS?.close()
            jmDNS = null
            Log.d(TAG, "已注销所有mDNS服务")
        } catch (e: IOException) {
            Log.e(TAG, "注销服务时出错: ${e.message}", e)
            e.printStackTrace()
        }
    }

    /**
     * 注册自定义服务类型
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun registerCustomService(
        serviceType: String,
        serviceName: String,
        port: Int,
        txtRecord: Map<String, String>? = null,
        ipAddress: String? = null,
        coroutineScope: CoroutineScope = GlobalScope
    ) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                registerCustomServiceInternal(serviceType, serviceName, port, txtRecord, ipAddress)
            }
        }
    }

    private fun registerCustomServiceInternal(
        serviceType: String,
        serviceName: String,
        port: Int,
        txtRecord: Map<String, String>? = null,
        ipAddress: String? = null
    ) {
        try {
            val inetAddress = if (ipAddress != null) {
                InetAddress.getByName(ipAddress)
            } else {
                getFirstNonLoopbackAddress()
            }
            
            if (inetAddress != null) {
                // 关闭现有的JmDNS实例
                jmDNS?.let {
                    try {
                        it.unregisterAllServices()
                        it.close()
                    } catch (e: IOException) {
                        Log.e(TAG, "关闭旧的JmDNS实例时出错: ${e.message}")
                    }
                }
                
                jmDNS = JmDNS.create(inetAddress, serviceName)

                val txtRecords = txtRecord ?: emptyMap()

                val serviceInfo = ServiceInfo.create(
                    serviceType,
                    serviceName,
                    port,
                    0,
                    0,
                    txtRecords
                )

                jmDNS?.registerService(serviceInfo)
                Log.d(TAG, "已注册服务: $serviceName 类型: $serviceType 在端口: $port, 地址: ${inetAddress.hostAddress}")
                println("已注册服务: $serviceName 类型: $serviceType 在端口: $port, 地址: ${inetAddress.hostAddress}")
            } else {
                Log.e(TAG, "无法找到有效的网络接口用于自定义服务注册")
            }
        } catch (e: IOException) {
            Log.e(TAG, "注册自定义服务失败: ${e.message}", e)
            e.printStackTrace()
            println("注册服务失败: ${e.message}")
        }
    }
    
    /**
     * 检查JmDNS实例是否已初始化
     */
    fun isInitialized(): Boolean {
        return jmDNS != null
    }
    
    /**
     * 获取当前JmDNS实例
     */
    fun getJmDNSInstance(): JmDNS? {
        return jmDNS
    }
}