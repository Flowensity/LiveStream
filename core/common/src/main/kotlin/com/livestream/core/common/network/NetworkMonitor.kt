package com.livestream.core.common.network

import kotlinx.coroutines.flow.StateFlow

/**
 * 跨模块网络连接状态监听契约
 * 遵循 Clean Architecture，解耦上层业务与具体 Android 平台 ConnectivityManager
 */
interface NetworkMonitor {

    val isConnected: StateFlow<Boolean>
}
