package io.github.kineks.mdnsserver.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kineks.mdnsserver.MDNSServiceRegistration

/**
 * 显示mDNS服务状态的屏幕
 */
@Composable
fun ServiceStatusScreen(
    mdnsServiceRegistration: MDNSServiceRegistration?,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier) {
        Text(
            text = "mDNS服务已注册: sillytavern.local.:8080",
            modifier = Modifier.padding(16.dp)
        )
    }
}