package com.kaltok.tcpip.server.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kaltok.tcpip.common.constant.Define
import com.kaltok.tcpip.common.constant.Define.ID_LEN
import com.kaltok.tcpip.server.model.Connection
import com.kaltok.tcpip.server.model.ServerStatus
import com.kaltok.tcpip.server.repository.ServerRepository
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Duration
import java.util.Collections

class ServerService : Service() {

    private var server: ApplicationEngine? = null
    private val repository = ServerRepository.getInstance()
    private val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("port", Define.DEFAULT_PORT) ?: Define.DEFAULT_PORT
        startServer(port)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        stopServer()
    }

    private fun startServer(port: Int) {
        serviceScope.launch {
            repository.sendMessagePool.collect { message ->
                Timber.i("server send $message")
                repository.appendLogItem(message)
                connections.forEach {
                    it.session.send("00000:${message}")
                }
            }
        }

        repository.setServerStatus(ServerStatus.CREATING)
        server = embeddedServer(Netty, port) {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(15)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            routing {
                webSocket("/chat") {
                    Timber.i("server client added")
                    repository.appendLogItem("server client added")
                    val thisConnection = Connection(this)
                    connections += thisConnection

                    try {
                        Timber.i("server send welcome")
                        val welcomeMessage =
                            "You are connected! id is ${thisConnection.name} There are ${connections.count()} users here."
                        send(welcomeMessage)
                        repository.appendLogItem("server send $welcomeMessage")

                        for (frame in incoming) {
                            frame as? Frame.Text ?: continue
                            val receivedText = frame.readText()
                            Timber.i("server received $receivedText")
                            repository.appendLogItem("server received :$receivedText")

                            if (receivedText.length > ID_LEN && receivedText[ID_LEN] == ':') {
                                val id = receivedText.substring(0, ID_LEN)
                                val remain =
                                    receivedText.substring(ID_LEN + 1)
                                Timber.i("server receive client message : $remain")
                                repository.appendLogItem(remain, id)
                            }

                            connections.forEach {
                                it.session.send(receivedText)
                            }
                        }
                    } catch (e: Exception) {
                        e.localizedMessage?.let { Timber.i(it) }
                    } finally {
                        Timber.i("Removing " + thisConnection.name + "!")
                        repository.appendLogItem("Removing ${thisConnection.name}!")
                        connections -= thisConnection
                        repository.sendMessageToClient("exits ${thisConnection.name}")
                    }
                }
            }
        }

        server?.start(wait = false)

        if (server?.application?.isActive == true) {
            repository.setServerStatus(ServerStatus.CREATED)
        }
    }

    private fun stopServer() {
        repository.setServerStatus(ServerStatus.STOPPING)
        serviceScope.launch {
            connections.forEach {
                it.session.send(Frame.Close())
            }
            server?.stop(500, 500)
            delay(500)
            repository.setServerStatus(ServerStatus.IDLE)

            serviceJob.cancel()
        }
    }
}