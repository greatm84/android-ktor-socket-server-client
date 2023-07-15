package com.kaltok.tcpip.server.ui.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaltok.tcpip.common.constant.Define
import com.kaltok.tcpip.server.repository.ServerRepository
import com.kaltok.tcpip.server.service.ServerService
import com.kaltok.tcpip.server.ui.ServerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServerViewModel : ViewModel() {

    private val repository = ServerRepository.getInstance()
    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()
    val serverStatus = repository.serverStatus
    val logList = repository.logList
    val outputData = repository.outputData

    fun setServerIp(value: String) {
        _uiState.value = _uiState.value.copy(serverIp = value)
    }

    fun setServerPort(value: String) {
        _uiState.value = _uiState.value.copy(serverPort = value)
    }

    fun toggleServer(context: Context) {
        val intent = Intent(context, ServerService::class.java)
        if (isServiceRunning(context, ServerService::class.java)) {
            context.stopService(intent)
        } else {
            intent.apply {
                putExtra("port", _uiState.value.serverPort.toIntOrNull() ?: Define.DEFAULT_PORT)
            }
            context.startService(intent)
        }
    }

    fun sendMessageToClient(message: String) {
        viewModelScope.launch {
            repository.sendMessageToClient(message)
        }
    }

    fun refreshServerIpAddress(context: Context) {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip: String = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
        setServerIp(ip)
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