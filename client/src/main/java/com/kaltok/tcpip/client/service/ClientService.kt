package com.kaltok.tcpip.client.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.kaltok.tcpip.client.ConnectStatus
import com.kaltok.tcpip.client.repository.ClientRepository
import com.kaltok.tcpip.common.constant.Define
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClientService : Service() {

    companion object {
        private const val PREF_NAME = "client"
        private const val ID_LEN = 5
        private const val tag = "KSH_TEST"
    }

    private val repository = ClientRepository
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var session: WebSocketSession? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ip = intent?.getStringExtra("ip") ?: "localhost"
        val port = intent?.getIntExtra("port", Define.DEFAULT_PORT) ?: Define.DEFAULT_PORT
        connectToServer(ip, port)

        serviceScope.launch {
            repository.sendMessagePool.collect {
                session?.send(Frame.Text(it))
            }
        }

        return START_STICKY
    }

    private fun connectToServer(ip: String, port: Int) {
        when (repository.connectionStatus.value) {
            ConnectStatus.IDLE -> {
                repository.setConnectStatus(ConnectStatus.CONNECTING)

                val client = HttpClient {
                    install(WebSockets)
                }

                serviceScope.launch {
                    try {
                        client.webSocket(
                            method = HttpMethod.Get,
                            host = ip,
                            port = port,
                            path = "/chat"
                        ) {
                            session = this
                            repository.setConnectStatus(ConnectStatus.CONNECTED)

                            for (frame in incoming) {
                                frame as? Frame.Text ?: continue
                                val receivedMessage = frame.readText()

                                Log.i(tag, "client outputMessage $receivedMessage")

                                if (receivedMessage.length > ID_LEN && receivedMessage[ID_LEN] == ':') {
                                    val id = receivedMessage.substring(0, ID_LEN)
                                    val remain =
                                        receivedMessage.substring(ID_LEN + 1)
                                    Log.i(tag, "client remain outputMessage $remain")
                                    repository.appendLog(
                                        id,
                                        remain,
                                        if (id == repository.clientId) Color.Red else Color.Green
                                    )
                                } else {
                                    repository.appendLog("server", receivedMessage)
                                }
                            }

                        }
                    } catch (e: Exception) {
                        e.localizedMessage?.let { Log.i(tag, it) }
                    } finally {
                        repository.sendMessage("exits ${repository.clientId}")
                    }
                }
            }

            ConnectStatus.CONNECTING -> {}
            ConnectStatus.CONNECTED -> {}
            ConnectStatus.DISCONNECTING -> {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        disconnect()
    }

    private fun disconnect() {
        serviceScope.launch {
            repository.setConnectStatus(ConnectStatus.DISCONNECTING)
            delay(1000L)
            repository.setConnectStatus(ConnectStatus.IDLE)
        }
    }
}