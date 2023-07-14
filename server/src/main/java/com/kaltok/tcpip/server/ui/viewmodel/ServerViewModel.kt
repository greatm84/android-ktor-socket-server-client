package com.kaltok.tcpip.server.ui.viewmodel

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
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

    fun toggleServer() {
        when (_uiState.value.serverStatus) {
            ServerStatus.IDLE -> {
                initSendMessagePool()
                _uiState.value = _uiState.value.copy(serverStatus = ServerStatus.CREATING)

                val port = _uiState.value.serverPort.toIntOrNull() ?: 7008

                _uiState.value = _uiState.value.copy(serverPort = port.toString())

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
                                    connections.forEach {
                                        it.session.send("00000:${frame.readText()}")
                                    }
                                } else {
                                    connections.forEach {
                                        it.session.send(frame)
                                    }
                                }
                            }
                        }

                        webSocket("/chat") {
                            Log.i("KSH_TEST", "server client added")
                            val thisConnection = Connection(this)
                            connections += thisConnection

                            try {
                                Log.i(TAG, "server send welcome")
                                send("You are connected! id is ${thisConnection.name} There are ${connections.count()} users here.")

                                while (uiState.value.serverStatus == ServerStatus.CREATED &&
                                    thisConnection.session.isActive
                                ) {
                                    when (val frame = incoming.receive()) {
                                        is Frame.Text -> {
                                            val receivedText = frame.readText()

                                            Log.i(TAG, "server received $receivedText")

                                            if (receivedText.length > ID_LEN && receivedText[ID_LEN] == ':') {
                                                val remain = receivedText.substring(ID_LEN + 1)
                                                Log.i(
                                                    TAG,
                                                    "server receive client message : $remain"
                                                )
                                                _outputData.emit(remain)
                                            }

                                            connections.forEach {
                                                it.session.send(receivedText)
                                            }
                                        }

                                        is Frame.Close -> {
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
                                if (thisConnection.session.isActive) {
                                    thisConnection.session.close()
                                }
                                connections -= thisConnection
                            }
                        }
                    }
                }.start(wait = false)

                _uiState.value =
                    _uiState.value.copy(serverStatus = ServerStatus.CREATED)

                _engine = engine
            }

            ServerStatus.CREATED -> {
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(serverStatus = ServerStatus.STOPPING)
                    sendCloseEventToClient()
                    _engine?.stop()
                    _uiState.value = _uiState.value.copy(serverStatus = ServerStatus.IDLE)
                }
            }

            else -> {}
        }
    }

    fun refreshServerIpAddress(context: Context) {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip: String = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
        _uiState.value = _uiState.value.copy(serverIp = ip)
    }
}