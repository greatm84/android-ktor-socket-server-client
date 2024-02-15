package com.kaltok.tcpip.server.repository

import com.kaltok.tcpip.common.util.SingletonHolder
import com.kaltok.tcpip.server.model.ServerStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class ServerRepository private constructor() {

    companion object : SingletonHolder<ServerRepository>(::ServerRepository)

    private val _sendMessagePool = MutableSharedFlow<String>()
    val sendMessagePool = _sendMessagePool.asSharedFlow()

    private val _outputData = MutableStateFlow("")
    val outputData = _outputData.asStateFlow()

    private val _serverStatus = MutableStateFlow(ServerStatus.IDLE)
    val serverStatus = _serverStatus.asStateFlow()

    private val _logList = MutableStateFlow(listOf<Pair<String, String>>())
    val logList = _logList.asStateFlow()


    suspend fun sendMessageToClient(message: String) {
        _sendMessagePool.emit(message)
    }

    fun setServerStatus(status: ServerStatus) {
        _serverStatus.value = status
        appendLogItem("serverStatus :$status")
    }

    fun appendLogItem(content: String, id: String = "server") {
        _logList.value = _logList.value.toMutableList().apply {
            add(id to content)
        }
    }

    suspend fun sendOutputData(data: String) {
        _outputData.emit(data)
    }
}