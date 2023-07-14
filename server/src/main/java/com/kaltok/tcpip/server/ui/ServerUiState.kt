package com.kaltok.tcpip.server.ui

import com.kaltok.tcpip.server.model.ServerStatus

data class ServerUiState(
    var serverIp: String = "",
    var serverPort: String = "",
    var serverStatus: ServerStatus = ServerStatus.IDLE
)