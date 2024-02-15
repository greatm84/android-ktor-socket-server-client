package com.kaltok.tcpip.client.ui.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaltok.tcpip.client.repository.ClientRepository
import com.kaltok.tcpip.client.service.ClientService
import com.kaltok.tcpip.client.ui.ClientUiState
import com.kaltok.tcpip.common.constant.Define
import com.kaltok.tcpip.common.util.NetworkUtils
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ClientViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREF_NAME = "client"
        private const val ID_LEN = 5
        private const val tag = "KSH_TEST"
    }

    private val repository = ClientRepository
    private val sharedPref = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ClientUiState())
    val uiState: StateFlow<ClientUiState> = _uiState.asStateFlow()

    private val _outputData = MutableStateFlow("")
    val outputData: StateFlow<String> = _outputData.asStateFlow()

    private val _serverHostIpList = MutableSharedFlow<List<String>>()
    val serverHostIpList = _serverHostIpList.asSharedFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.connectionStatus.collect { status ->
                _uiState.update {
                    it.copy(connectStatus = status)
                }
            }
        }

        viewModelScope.launch {
            repository.logList.collect { logList ->
                _uiState.update {
                    it.copy(logList = logList)
                }
            }
        }
    }

    fun stopSearchServerHostIpList() {
        searchJob?.cancel()
        setSearchHostIps(false)
    }

    fun searchServerHostIpList() {
        setSearchHostIps(true)
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val hostIpList = mutableListOf<String>()
            val errorIpList = mutableListOf<String>()
            val jobs = mutableListOf<Deferred<Unit>>()
            val localAddressList = NetworkUtils.getLocalIpAddress()
            for (ipAddress in localAddressList) {
                val job = async {
                    val client = HttpClient {
                        install(WebSockets)
                    }
                    try {
                        client.webSocket(
                            method = HttpMethod.Get,
                            host = ipAddress,
                            port = Define.DEFAULT_PORT,
                            path = "/open"
                        ) {
                            hostIpList.add(ipAddress)
                            setSearchedServerHostIps(hostIpList)
                            client.close()
                        }
                    } catch (e: Exception) {
                        Log.i(tag, "exception with $ipAddress $e")
                        errorIpList.add(ipAddress)
                        Unit
                    } finally {

                    }
                }
                jobs.add(job)
            }
            jobs.awaitAll()
            setSearchedServerHostIps(hostIpList)
            println(errorIpList)
            setSearchHostIps(false)
        }
    }

    fun sendMessage(message: String) {
        repository.sendMessage(message)
    }

    private fun setSearchHostIps(value: Boolean) {
        _uiState.value = _uiState.value.copy(searchHostIp = value)
    }

    private fun setSearchedServerHostIps(hostIpList: List<String>) {
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

    fun toggleClient(context: Context) {
        val intent = Intent(context, ClientService::class.java)
        if (isServiceRunning(context, ClientService::class.java)) {
            context.stopService(intent)
        } else {
            val ip = uiState.value.serverIp
            val port = uiState.value.serverPort.toIntOrNull() ?: Define.DEFAULT_PORT
            _uiState.update { it.copy(serverPort = port.toString()) }
            intent.putExtra("ip", ip).putExtra("port", port)
            context.startService(intent)
        }
    }

    fun loadLastServerIpFromPref() {
        setServerIp(sharedPref.getString("lastServerIp", "") ?: "")
    }

    private fun saveLastServerIpPref() {
        sharedPref.edit().apply {
            putString("lastServerIp", _uiState.value.serverIp)
            apply()
        }
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = manager.getRunningServices(Int.MAX_VALUE)
        for (service in services) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}