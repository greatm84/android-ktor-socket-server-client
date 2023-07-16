package com.kaltok.tcpip.client.ui

import androidx.compose.ui.graphics.Color
import com.kaltok.tcpip.client.ConnectStatus

data class ClientUiState(
    val searchHostIp:Boolean = false,
    val serverIp: String = "192.168.0.127",
    val serverPort: String = "7008",
    val connectStatus: ConnectStatus = ConnectStatus.IDLE,
    val useLocalIp: Boolean = false,
    val logList: List<Triple<String, String, Color>> = emptyList()
)