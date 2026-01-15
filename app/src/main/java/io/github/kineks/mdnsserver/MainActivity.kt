package io.github.kineks.mdnsserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import io.github.kineks.mdnsserver.ui.theme.MdnsServerTheme
import io.github.kineks.mdnsserver.ui.screen.NetworkInterfaceSelectorScreen
import io.github.kineks.mdnsserver.ui.screen.ServiceStatusScreen

class MainActivity : ComponentActivity() {
    private lateinit var mdnsServiceRegistration: MDNSServiceRegistration
    private lateinit var mdnsServerApplication: MDNSServerApplication
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 从应用程序获取mDNS服务注册器
        mdnsServerApplication = application as MDNSServerApplication
        mdnsServiceRegistration = mdnsServerApplication.getMDNSServiceRegistration()
        
        enableEdgeToEdge()
        setContent {
            MdnsServerTheme {
                MdnsServerApp(mdnsServiceRegistration, mdnsServerApplication)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 不再在这里清理服务，因为服务在后台运行
    }
}

@PreviewScreenSizes
@Composable
fun MdnsServerApp(mdnsServiceRegistration: MDNSServiceRegistration? = null, mdnsServerApplication: MDNSServerApplication? = null) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when(currentDestination) {
                AppDestinations.HOME -> ServiceStatusScreen(
                    modifier = Modifier.padding(innerPadding),
                    mdnsServiceRegistration = mdnsServiceRegistration
                )
                AppDestinations.NETWORK -> NetworkInterfaceSelectorScreen(
                    modifier = Modifier.padding(innerPadding),
                    mdnsServiceRegistration = mdnsServiceRegistration,
                    mdnsServerApplication = mdnsServerApplication
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    NETWORK("Network", Icons.Default.NetworkWifi),
}




