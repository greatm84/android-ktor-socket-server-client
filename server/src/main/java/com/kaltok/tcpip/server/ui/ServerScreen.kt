package com.kaltok.tcpip.server.ui

import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaltok.tcpip.server.model.ServerStatus
import com.kaltok.tcpip.server.ui.viewmodel.ServerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ServerViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val logList by viewModel.logList.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val listState = rememberLazyListState()
    var inputMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshServerIpAddress(context)
    }

    LaunchedEffect(logList.size) {
        listState.animateScrollToItem(logList.size)
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize()) {
            Text(text = "Server Ip")
            TextField(
                value = uiState.serverIp,
                onValueChange = { viewModel.setServerIp(it) },
                readOnly = true
            )
            Text(text = "Server Port")
            TextField(
                value = uiState.serverPort,
                onValueChange = { viewModel.setServerPort(it) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(
                onClick = { viewModel.toggleServer(context) },
                enabled = when (serverStatus) {
                    ServerStatus.IDLE, ServerStatus.CREATED -> true
                    else -> false
                }
            ) {
                Text(
                    text = when (serverStatus) {
                        ServerStatus.IDLE -> "CREATE"
                        ServerStatus.CREATING -> "WAIT"
                        ServerStatus.CREATED -> "STOP"
                        ServerStatus.STOPPING -> "STOPPING"
                    }
                )
            }

            Text(text = "Logs")
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(logList) {
                    LogItem(it.first, it.second)
                }
            }

            Spacer(modifier = Modifier.padding(10.dp))

            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = inputMessage, onValueChange = {
                    inputMessage = it
                }, modifier = Modifier.weight(1f))
                Button(onClick = {
                    viewModel.sendMessageToClient(inputMessage)
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
fun LogItem(id: String, log: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Blue)
    ) {
        Text(
            text = id,
            Modifier
                .weight(0.1f)
//                .background(color)
        )
        Text(text = log, Modifier.weight(0.9f))
    }
}