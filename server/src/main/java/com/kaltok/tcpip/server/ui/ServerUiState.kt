package com.kaltok.tcpip.server.ui

import androidx.compose.ui.graphics.Color
import com.kaltok.tcpip.server.model.ServerStatus

data class ServerUiState(
    var serverIp: String = "",
    var serverPort: String = "",
    var serverStatus: ServerStatus = ServerStatus.IDLE,
    val logList: List<Triple<String,String, Color>> = emptyList()
)