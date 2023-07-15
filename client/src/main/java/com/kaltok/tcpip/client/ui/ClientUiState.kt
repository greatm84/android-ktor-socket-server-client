package com.kaltok.tcpip.client.ui

import androidx.compose.ui.graphics.Color
import com.kaltok.tcpip.client.ConnectStatus

data class ClientUiState(
    val serverIp: String = "192.168.1.202",
    val serverPort: String = "7008",
    val connectStatus: ConnectStatus = ConnectStatus.IDLE,
    val useLocalIp: Boolean = true,
    val logList: List<Triple<String,String, Color>> = emptyList()
)