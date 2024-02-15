package com.kaltok.tcpip.client.repository

import androidx.compose.ui.graphics.Color
import com.kaltok.tcpip.client.ConnectStatus
import com.kaltok.tcpip.common.constant.Define
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

object ClientRepository {

    private val _connectionStatus = MutableStateFlow(ConnectStatus.IDLE)
    val connectionStatus = _connectionStatus.asStateFlow()
    private val _sendMessagePool = MutableStateFlow("Connected")
    val sendMessagePool = _sendMessagePool.asStateFlow()

    private val _logList = MutableStateFlow<List<Triple<String, String, Color>>>(emptyList())
    val logList = _logList.asStateFlow()

    val clientId = Random.nextLong(1, 99999).toString().padStart(Define.ID_LEN, '0')

    fun setConnectStatus(status: ConnectStatus) {
        _connectionStatus.value = status
    }

    fun sendMessage(message: String) {
        _sendMessagePool.value = "$clientId:$message"
    }

    fun appendLog(id: String, content: String, color: Color = Color.Black) {
        _logList.value = logList.value.toMutableList().apply {
            add(Triple(id, content, if (id != clientId) Color.Blue else color))
        }
    }
}