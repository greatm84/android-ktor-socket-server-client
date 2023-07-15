package com.kaltok.tcpip.client.ui.viewmodel

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaltok.tcpip.client.ConnectStatus
import com.kaltok.tcpip.client.ui.ClientUiState
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class ClientViewModel : ViewModel() {

    companion object {
        private const val ID_LEN = 5
        private const val tag = "KSH_TEST"
    }

    private val _uiState = MutableStateFlow(ClientUiState())
    val uiState: StateFlow<ClientUiState> = _uiState.asStateFlow()

    private var _client: HttpClient? = null

    private val _outputData = MutableStateFlow("")
    val outputData: StateFlow<String> = _outputData.asStateFlow()

    private val _sendMessagePool = MutableStateFlow<Frame?>(null)

    private var clientId = Random.nextLong(1, 99999).toString().padStart(ID_LEN, '0')


    fun sendMessageToServer(message: String) {
        viewModelScope.launch {
            _sendMessagePool.emit(Frame.Text(message))
        }
    }

    private fun sendCloseEventToServer() {
        viewModelScope.launch {
            _sendMessagePool.emit(Frame.Close())
        }
    }

    private fun initSendMessagePool() {
        viewModelScope.launch {
            _sendMessagePool.emit(null)
        }
    }


    fun setServerIp(value: String) {
        _uiState.value = _uiState.value.copy(serverIp = value)
    }

    fun setServerPort(value: String) {
        _uiState.value = _uiState.value.copy(serverPort = value)
    }

    fun setUseLocalIp(value: Boolean) {
        _uiState.value = _uiState.value.copy(useLocalIp = value)
    }

    fun appendLogItem(id: String, content: String, color: Color = Color.Black) {
        _uiState.value =
            _uiState.value.copy(logList = _uiState.value.logList.toMutableList().apply {
                add(Triple(id, content, if (id != clientId) Color.Blue else color))
            })
    }

    fun setConnectStatus(status: ConnectStatus) {
        _uiState.value = _uiState.value.copy(connectStatus = status)
    }


    fun toggleClient() {
        when (_uiState.value.connectStatus) {
            ConnectStatus.IDLE -> {
                initSendMessagePool()
                setConnectStatus(ConnectStatus.CONNECTING)

                CoroutineScope(Dispatchers.IO).launch {
                    val client = HttpClient {
                        install(WebSockets)
                    }
                    val targetIp =
                        if (_uiState.value.useLocalIp) "127.0.0.1" else _uiState.value.serverIp
                    Log.i(tag, "try connect $clientId $targetIp")

                    try {
                        client.webSocket(
                            method = HttpMethod.Get,
                            host = targetIp,
                            port = _uiState.value.serverPort.toInt(),
                            path = "/chat"
                        ) {
                            setConnectStatus(ConnectStatus.CONNECTED)

                            launch {
                                _sendMessagePool.collect {
                                    if (!client.isActive) {
                                        return@collect
                                    }

                                    when (it) {
                                        null -> {}
                                        is Frame.Text -> {
                                            val sendText = it.readText()
                                            try {
                                                send("$clientId:${sendText}")
                                                appendLogItem(clientId, sendText)
                                            } catch (e: Exception) {
                                                Log.e(
                                                    tag,
                                                    "Error while sending: " + e.localizedMessage
                                                )
                                                appendLogItem(
                                                    clientId,
                                                    "Error while sending : $sendText",
                                                    Color.Red
                                                )
                                            }
                                        }

                                        else -> {
                                            Log.i(tag, "client send $it")
                                            send(it)
                                            appendLogItem(clientId, "send close")
                                        }
                                    }
                                }
                            }

                            while (uiState.value.connectStatus == ConnectStatus.CONNECTED) {
                                when (val frame = incoming.receive()) {
                                    is Frame.Text -> {
                                        val receivedMessage = frame.readText()

                                        Log.i(tag, "client outputMessage $receivedMessage")

                                        if (receivedMessage.length > ID_LEN && receivedMessage[ID_LEN] == ':') {
                                            val id = receivedMessage.substring(0, ID_LEN)
                                            if (id != clientId) {
                                                val remain =
                                                    receivedMessage.substring(ID_LEN + 1)
                                                Log.i(
                                                    tag,
                                                    "client remain outputMessage $remain"
                                                )
                                                appendLogItem(id, remain)
                                                _outputData.emit(remain)
                                            }
                                        } else {
                                            appendLogItem("server", receivedMessage)
                                        }
                                    }

                                    is Frame.Close -> {
                                        appendLogItem(clientId, "Server Closed Exception")
                                        throw ClosedReceiveChannelException("server closed")
                                    }

                                    else -> {}
                                }
                            }
                        }
                        _client = client
                    } catch (e: Exception) {
                        Log.e(tag, e.stackTraceToString())
                        if (_client?.isActive == true) {
                            _client?.close()
                            _client = null
                        }

                        setConnectStatus(ConnectStatus.IDLE)
                    }
                }
            }

            ConnectStatus.CONNECTED -> {
                viewModelScope.launch {
                    setConnectStatus(ConnectStatus.DISCONNECTING)
                    sendCloseEventToServer()
                    setConnectStatus(ConnectStatus.IDLE)
                }
            }

            else -> {
                // do nothing
            }
        }
    }
}