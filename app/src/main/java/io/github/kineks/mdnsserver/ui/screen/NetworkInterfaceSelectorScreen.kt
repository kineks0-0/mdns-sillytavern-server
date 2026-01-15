package io.github.kineks.mdnsserver.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import io.github.kineks.mdnsserver.MDNSServerApplication
import io.github.kineks.mdnsserver.MDNSService
import io.github.kineks.mdnsserver.MDNSServiceRegistration
import io.github.kineks.mdnsserver.NetworkInterfaceInfo

/**
 * 网络接口选择器屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkInterfaceSelectorScreen(
    mdnsServiceRegistration: MDNSServiceRegistration?,
    mdnsServerApplication: MDNSServerApplication?,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedInterface by remember { mutableStateOf<NetworkInterfaceInfo?>(null) }
    var availableInterfaces by remember { mutableStateOf(emptyList<NetworkInterfaceInfo>()) }
    var port by rememberSaveable { mutableStateOf("8080") }
    var serviceStarted by rememberSaveable { mutableStateOf(mdnsServerApplication?.isServiceRunning(MDNSService::class.java) ?: false) }

    // 监听服务状态变化
    DisposableEffect(mdnsServerApplication) {
        val updateServiceStatus = {
            serviceStarted = mdnsServerApplication?.isServiceRunning(MDNSService::class.java) ?: false
        }
        updateServiceStatus()

        onDispose { }
    }

    Surface(modifier = modifier) {
        if (mdnsServiceRegistration != null) {
            val context = androidx.compose.ui.platform.LocalContext.current

            // 尝试自动选择上次使用的接口
            LaunchedEffect(Unit) {
                availableInterfaces = mdnsServiceRegistration.getAvailableNetworkInterfaces()
                if (mdnsServerApplication != null) {
                    // 从Application获取上次保存的接口信息
                    val lastUsedIp = mdnsServerApplication.getLastUsedInterfaceIp()
                    val lastUsedPort = mdnsServerApplication.getLastUsedPort()

                    if (lastUsedIp != null) {
                        val savedInterface = availableInterfaces.find { it.ipAddress == lastUsedIp }
                        if (savedInterface != null) {
                            selectedInterface = savedInterface
                            port = lastUsedPort.toString()
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        value = selectedInterface?.displayName ?: "选择网络接口",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableInterfaces.forEach { interfaceInfo ->
                            DropdownMenuItem(
                                text = {
                                    Text("${interfaceInfo.displayName} (${interfaceInfo.ipAddress})")
                                },
                                onClick = {
                                    selectedInterface = interfaceInfo
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                TextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("端口") },
                    modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
                )

                Button(
                    onClick = {
                        if (selectedInterface != null) {
                            serviceStarted = true
                            if (mdnsServerApplication == null) {
                                serviceStarted = false
                                return@Button
                            }
                            // 启动后台服务而不是直接注册
                            mdnsServerApplication.startMDNSService(
                                port = port.toIntOrNull() ?: 8080,
                                ipAddress = selectedInterface!!.ipAddress,
                                serviceName = "sillytavern"
                            )

                            // 保存当前选择
                            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                            prefs.edit()
                                .putString("last_used_ip", selectedInterface!!.ipAddress)
                                .putInt("last_used_port", port.toIntOrNull() ?: 8080)
                                .apply()

                        }
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(if (!(mdnsServerApplication?.isServiceRunning(MDNSService::class.java) ?: false)) "启动后台服务" else "重启服务")
                }

                Button(
                    onClick = {
                        mdnsServerApplication?.stopMDNSService()
                    },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                ) {
                    Text("停止服务")
                }

                // 显示当前状态
                Text(
                    text = if (serviceStarted) "服务正在后台运行" else "服务未运行",
                    modifier = Modifier.padding(16.dp)
                )
            }

        }
    }
}