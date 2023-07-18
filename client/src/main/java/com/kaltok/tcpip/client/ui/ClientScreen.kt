package com.kaltok.tcpip.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kaltok.tcpip.client.ConnectStatus
import com.kaltok.tcpip.client.ui.viewmodel.ClientViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen(viewModel: ClientViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val searchedHosts by viewModel.serverHostIpList.collectAsState(initial = emptyList())
    var inputMessage by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.logList.size) {
        listState.animateScrollToItem(uiState.logList.size)
    }

    LaunchedEffect(Unit) {
        viewModel.loadLastServerIpFromPref()
    }


    MaterialTheme {
        Column(Modifier.fillMaxSize()) {
            Button(
                onClick = {
                    if (uiState.searchHostIp) {
                        viewModel.stopSearchServerHostIpList()
                    } else {
                        viewModel.searchServerHostIpList()
                    }
                },
            ) {
                Text(text = if (uiState.searchHostIp) "Searching" else "Search")
            }

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = Color.Cyan)
            ) {
                items(searchedHosts) { hostIp ->
                    Text(
                        text = hostIp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(1.dp)
                            .clickable { viewModel.setServerIp(hostIp) })
                }
            }
            Spacer(Modifier.padding(1.dp))
            TextField(
                value = uiState.serverIp, onValueChange = { viewModel.setServerIp(it) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Text(text = "Server Port")
            TextField(
                value = uiState.serverPort,
                onValueChange = { viewModel.setServerPort(it) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(
                onClick = { viewModel.toggleClient() },
                enabled = when (uiState.connectStatus) {
                    ConnectStatus.CONNECTING, ConnectStatus.DISCONNECTING -> false
                    else -> true
                }
            ) {
                Text(
                    text = when (uiState.connectStatus) {
                        ConnectStatus.IDLE -> "START"
                        ConnectStatus.CONNECTING -> "WAIT TRY CONNECTING"
                        ConnectStatus.CONNECTED -> "STOP"
                        ConnectStatus.DISCONNECTING -> "DISCONNECTING"
                    }
                )
            }

            Text(text = "Logs")
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(uiState.logList) {
                    LogItem(it.first, it.second, it.third)
                }
            }

            Spacer(modifier = Modifier.padding(10.dp))

            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = inputMessage, onValueChange = {
                    inputMessage = it
                }, modifier = Modifier.weight(1f))
                Button(onClick = {
                    viewModel.sendMessageToServer(inputMessage)
                    inputMessage = ""
                }) {
                    Text("Send")
                }
            }

            Spacer(modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable
fun LogItem(id: String, log: String, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Blue)
    ) {
        Text(
            text = id,
            Modifier
                .weight(0.1f)
                .background(color)
        )
        Text(text = log, Modifier.weight(0.9f))
    }
}
