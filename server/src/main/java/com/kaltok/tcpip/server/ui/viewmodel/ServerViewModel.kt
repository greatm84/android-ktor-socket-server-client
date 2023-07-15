package com.kaltok.tcpip.server.ui.viewmodel

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaltok.tcpip.server.model.Connection
import com.kaltok.tcpip.server.model.ServerStatus
import com.kaltok.tcpip.server.ui.ServerUiState
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.Collections

class ServerViewModel : ViewModel() {

    companion object {
        private const val TAG = "KSH_TEST:SERVER"
        private const val ID_LEN = 5
    }


    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    private var _engine: NettyApplicationEngine? = null

    private val _sendMessagePool = MutableStateFlow<Frame?>(null)

    private val _outputData = MutableStateFlow("")
    val outputData: StateFlow<String> = _outputData.asStateFlow()

    fun sendMessageToClient(message: String) {
        viewModelScope.launch {
            _sendMessagePool.emit(Frame.Text(message))
        }
    }

    private fun sendCloseEventToClient() {
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

    fun setServerStatus(serverStatus: ServerStatus) {
        _uiState.value = _uiState.value.copy(serverStatus = serverStatus)
        appendLogItem("server status :$serverStatus")
    }

    fun appendLogItem(content: String, id: String = "server", color: Color = Color.Black) {
        _uiState.value =
            _uiState.value.copy(logList = _uiState.value.logList.toMutableList().apply {
                add(Triple(id, content, if (id != "server") Color.Blue else color))
            })
    }

    fun toggleServer() {
        when (_uiState.value.serverStatus) {
            ServerStatus.IDLE -> {
                initSendMessagePool()
                setServerStatus(serverStatus = ServerStatus.CREATING)

                val port = _uiState.value.serverPort.toIntOrNull() ?: 7008
                setServerPort(port.toString())

                val engine = embeddedServer(Netty, port) {
                    install(WebSockets) {
                        pingPeriod = Duration.ofSeconds(15)
                        timeout = Duration.ofSeconds(15)
                        maxFrameSize = Long.MAX_VALUE
                        masking = false
                    }
                    routing {
                        val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())

                        CoroutineScope(Dispatchers.IO).launch {
                            _sendMessagePool.collect { frame ->
                                if (frame == null) {
                                    return@collect
                                }

                                Log.i(TAG, "server send $frame")

                                if (frame is Frame.Text) {
                                    val sendText = frame.readText()
                                    appendLogItem(sendText)
                                    connections.forEach {
                                        it.session.send("00000:${sendText}")
                                    }
                                } else {
                                    appendLogItem(frame.toString())
                                    connections.forEach {
                                        it.session.send(frame)
                                    }
                                }
                            }
                        }

                        webSocket("/chat") {
                            Log.i("KSH_TEST", "server client added")
                            appendLogItem("server client added")
                            val thisConnection = Connection(this)
                            connections += thisConnection

                            try {
                                Log.i(TAG, "server send welcome")
                                val welcomeMessage =
                                    "You are connected! id is ${thisConnection.name} There are ${connections.count()} users here."
                                send(welcomeMessage)
                                appendLogItem("server send $welcomeMessage")

                                while (uiState.value.serverStatus == ServerStatus.CREATED &&
                                    thisConnection.session.isActive
                                ) {
                                    when (val frame = incoming.receive()) {
                                        is Frame.Text -> {
                                            val receivedText = frame.readText()

                                            Log.i(TAG, "server received $receivedText")
                                            appendLogItem("server received :$receivedText")

                                            if (receivedText.length > ID_LEN && receivedText[ID_LEN] == ':') {
                                                val id = receivedText.substring(0, ID_LEN)
                                                val remain = receivedText.substring(ID_LEN + 1)
                                                Log.i(
                                                    TAG,
                                                    "server receive client message : $remain"
                                                )
                                                appendLogItem(remain, id)
                                                _outputData.emit(remain)
                                            }

                                            connections.forEach {
                                                it.session.send(receivedText)
                                            }
                                        }

                                        is Frame.Close -> {
                                            appendLogItem("server received close from:${thisConnection.name}")
                                            throw Exception("close received")
                                        }

                                        else -> {}
                                    }
                                }
                            } catch (e: Exception) {
                                Log.i(
                                    TAG,
                                    "server get exception serverStatus: ${_uiState.value.serverStatus} ${thisConnection.session.isActive}"
                                )
                                e.localizedMessage?.let { Log.i("KSH_TEST", it) }
                            } finally {
                                Log.i(TAG, "Removing ${thisConnection.name}!")
                                appendLogItem("Removing ${thisConnection.name}!")
                                if (thisConnection.session.isActive) {
                                    thisConnection.session.close()
                                }
                                connections -= thisConnection
                            }
                        }
                    }
                }.start(wait = false)

                setServerStatus(serverStatus = ServerStatus.CREATED)

                _engine = engine
            }

            ServerStatus.CREATED -> {
                viewModelScope.launch {
                    setServerStatus(serverStatus = ServerStatus.STOPPING)
                    sendCloseEventToClient()
                    _engine?.stop()
                    setServerStatus(serverStatus = ServerStatus.IDLE)
                }
            }

            else -> {}
        }
    }

    fun refreshServerIpAddress(context: Context) {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip: String = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
        setServerIp(ip)
    }
}