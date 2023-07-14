package com.kaltok.tcpip.client

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaltok.tcpip.client.ui.theme.ServerTheme
import com.kaltok.tcpip.client.ui.viewmodel.ClientViewModel
import kotlinx.coroutines.launch

class ClientActivity : ComponentActivity() {

    companion object {
        private const val tag = "KSH_TEST"
    }

    private val viewModel: ClientViewModel by viewModels { ViewModelProvider.AndroidViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ServerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClientScreen()
                }
            }
        }

        Log.i(tag, "client launch")

        lifecycleScope.launch {
            viewModel.outputData.collect {
                if (it.isNotEmpty()) {
                    if (it.startsWith("http")) { // goto webview
                        Log.i(tag, "cilent forward to webview $it")
                        launchWebView(it)
                    } else {
                        Log.i(tag, "client output collect send intent $it")
                        val intent = Intent.parseUri(it, Intent.URI_INTENT_SCHEME)
                        val uri = Uri.parse(intent.dataString)
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                }
            }
        }

        requestPermission()
    }

    private fun requestPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + this.packageName)
            )

            startActivity(intent)
        }
    }

    private fun launchWebView(url: String) {
        val webViewPackageName =
            "com.samsung.android.game.cloudgame.service.webview"
        val launchIntent =
            packageManager.getLaunchIntentForPackage(webViewPackageName)
        if (launchIntent != null && canResolve(launchIntent)) {
            launchIntent.apply {
                action = Intent.ACTION_VIEW
                data = url.toUri()
            }
            startActivity(launchIntent)
            Log.i(tag, "forward to webview done $url")
        } else {
            Log.i(tag, "can not resolve $url")
        }
    }

    private fun canResolve(intent: Intent): Boolean {
        return intent.resolveActivity(packageManager) != null
    }

    override fun onNewIntent(intent: Intent?) {
        handleDeepLink(intent)

        super.onNewIntent(intent)
    }

    private fun handleDeepLink(newIntent: Intent?) {
        Log.i(tag, "newIntent $newIntent")
        if (newIntent == null || newIntent.isLaunchFromHistory() || newIntent.action != Intent.ACTION_VIEW) {
            return
        }

        Log.i(tag, "client append Send Intent $newIntent")
        viewModel.sendMessageToServer(getUriStringFromIntent(newIntent))
    }

    private fun isFromWebUrl(newIntent: Intent?): Boolean {
        val scheme = getSchemeFromIntent(newIntent)
        return scheme == "http" || scheme == "https"
    }

    private fun getSchemeFromIntent(newIntent: Intent?): String? {
        return newIntent?.data?.scheme
    }

    private fun getUriStringFromIntent(newIntent: Intent): String {
        return newIntent.toUri(Intent.URI_ALLOW_UNSAFE).split(";")
            .filter { !it.startsWith("component=") }.reduce { acc, s -> "$acc;$s" }
            .split("#Intent;")[0]
    }

    private fun Intent.isLaunchFromHistory(): Boolean {
        return (this.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY).also {
            Log.d(tag, "isLaunchFromHistory: $it")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen(viewModel: ClientViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    MaterialTheme {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth()) {
                Text(text = "type server ip")
                Checkbox(
                    checked = uiState.useLocalIp,
                    onCheckedChange = { viewModel.setUseLocalIp(it) })
            }
            if (!uiState.useLocalIp) {
                TextField(value = uiState.serverIp, onValueChange = { viewModel.setServerIp(it) })
            }
            Text(text = "Server Port")
            TextField(value = uiState.serverPort, onValueChange = { viewModel.setServerPort(it) })
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
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ServerTheme {
        ClientScreen()
    }
}