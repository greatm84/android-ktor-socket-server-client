package com.kaltok.tcpip.client.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaltok.tcpip.client.ConnectStatus
import com.kaltok.tcpip.client.ui.ClientUiState
import com.kaltok.tcpip.common.constant.Define
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class ClientViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREF_NAME = "client"
        private const val ID_LEN = 5
        private const val tag = "KSH_TEST"
    }

    private val sharedPref = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ClientUiState())
    val uiState: StateFlow<ClientUiState> = _uiState.asStateFlow()

    private var _client: HttpClient? = null

    private val _outputData = MutableStateFlow("")
    val outputData: StateFlow<String> = _outputData.asStateFlow()

    private val _sendMessagePool = MutableStateFlow<Frame?>(null)

    private var clientId = Random.nextLong(1, 99999).toString().padStart(ID_LEN, '0')

    private val _serverHostIpList = MutableSharedFlow<List<String>>()
    val serverHostIpList = _serverHostIpList.asSharedFlow()

    private var searchJob: Job? = null

    fun stopSearchServerHostIpList() {
        searchJob?.cancel()
        setSearchHostIps(false)
    }

    fun searchServerHostIpList() {
        setSearchHostIps(true)
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val localIpAddress = "192.168." // Replace with your local IP address prefix
            val startRange = 0 // Starting range for scanning
            val endRange = 255 // Ending range for scanning
            val hostIpList = mutableListOf<String>()

            val jobs = mutableListOf<Deferred<Unit>>()
            for (j in startRange..endRange) {
                for (i in startRange..endRange) {
                    val ipAddress = "$localIpAddress$j.$i"
                    val job = async {
                        val client = HttpClient {
                            install(WebSockets)
                        }
                        try {
                            client.webSocket(
                                method = HttpMethod.Get,
                                host = ipAddress,
                                port = Define.DEFAULT_PORT,
                                path = "/chat"
                            ) {
                                hostIpList.add(ipAddress)
                                setSearchedServerHostIps(hostIpList)
                                client.close()
                            }
                        } finally {

                        }
                    }
                    jobs.add(job)
                }
            }

            jobs.awaitAll()
            setSearchedServerHostIps(hostIpList)
            setSearchHostIps(false)
        }
    }

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

    fun setSearchHostIps(value: Boolean) {
        _uiState.value = _uiState.value.copy(searchHostIp = value)
    }

    fun setSearchedServerHostIps(hostIpList: List<String>) {
        Log.i(tag, "emit $hostIpList")
        viewModelScope.launch(Dispatchers.Default) {
            _serverHostIpList.emit(hostIpList)
        }
    }

    fun setServerIp(value: String) {
        _uiState.value = _uiState.value.copy(serverIp = value)
        saveLastServerIpPref()
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
                        if (_uiState.value.useLocalIp) "localhost" else _uiState.value.serverIp
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

    fun loadLastServerIpFromPref() {
        setServerIp(sharedPref.getString("lastServerIp", "") ?: "")
    }

    fun saveLastServerIpPref() {
        sharedPref.edit().apply {
            putString("lastServerIp", _uiState.value.serverIp)
            apply()
        }
    }
}