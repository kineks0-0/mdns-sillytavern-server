# mDNS SillyTavern Server

一个Android应用程序，用于在局域网中广播SillyTavern服务，使得局域网中的其他设备可以通过mDNS（多播DNS）发现并连接到SillyTavern服务。

## 功能特性

- **mDNS服务广播**：自动在局域网中广播名为"sillytavern"的HTTP服务
- **网络接口选择**：支持选择特定的网络接口进行服务广播
- **后台服务**：服务在后台持续运行，即使应用在后台也能保持服务发现
- **现代化UI**：使用Jetpack Compose构建的现代化用户界面
- **自适应布局**：支持各种屏幕尺寸的自适应布局
- **网络变化感知**：自动响应网络变化并重新注册服务

## 技术栈

- Kotlin
- Jetpack Compose
- jmdns (Java mDNS实现)
- Kotlin Coroutines
- AndroidX库

## 使用场景

此应用特别适用于以下场景：
- 在局域网环境中运行SillyTavern服务
- 需要在不同设备间轻松发现SillyTavern服务
- 无需手动输入IP地址和端口即可访问SillyTavern

## 安装

1. 克隆此仓库
2. 使用Android Studio打开项目
3. 构建并安装到Android设备

## 配置

应用默认会在8080端口上广播服务，您可以在应用中更改端口和网络接口设置。

## 工作原理

应用使用jmdns库创建mDNS服务实例，并在指定的网络接口上注册_http._tcp.local.服务类型，这使得其他支持mDNS/Bonjour的设备能够通过[sillytavern.local](http://sillytavern.local)发现并访问服务。

## 许可证

请参阅LICENSE文件。