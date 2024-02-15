package com.kaltok.tcpip.client.ui

import androidx.compose.ui.graphics.Color
import com.kaltok.tcpip.client.ConnectStatus
import com.kaltok.tcpip.common.constant.Define

data class ClientUiState(
    val searchHostIp: Boolean = false,
    val serverIp: String = "",
    val serverPort: String = Define.DEFAULT_PORT.toString(),
    val connectStatus: ConnectStatus = ConnectStatus.IDLE,
    val logList: List<Triple<String, String, Color>> = emptyList()
)