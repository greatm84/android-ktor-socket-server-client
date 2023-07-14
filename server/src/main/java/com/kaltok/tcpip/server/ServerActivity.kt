package com.kaltok.tcpip.server

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaltok.tcpip.server.model.ServerStatus
import com.kaltok.tcpip.server.ui.theme.ServerTheme
import com.kaltok.tcpip.server.ui.viewmodel.ServerViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val tag = "KSH_TEST"
    }

    private val viewModel: ServerViewModel by viewModels { ViewModelProvider.AndroidViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ServerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.outputData.collect {
                if (it.isNotEmpty()) {
                    when {
                        it.startsWith("http") -> {
                            Log.i(tag, "server forward to webview $it")
                            val webViewPackageName =
                                "com.samsung.android.game.cloudgame.service.webview"
                            val launchIntent =
                                packageManager.getLaunchIntentForPackage(webViewPackageName)
                            if (launchIntent != null && canResolve(launchIntent)) {
                                launchIntent.apply {
                                    action = Intent.ACTION_VIEW
                                    data = it.toUri()
                                }
                                startActivity(launchIntent)
                            } else {
                                Log.i(tag, "can not resolve $it")
                            }
                        }

                        else -> {
                            val intent = Intent.parseUri(it, Intent.URI_ALLOW_UNSAFE)
                            when {
                                isFromMarketIntent(intent) -> {
                                    intent.data?.getQueryParameter("id")?.let { packageName ->
                                        jumpToGalaxyStorePage(packageName)
                                    } ?: run {
                                        startActivity(intent)
                                    }
                                }

                                canResolve(intent) -> {
                                    startActivity(intent)
                                }
                            }
                        }
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

    private fun canResolve(intent: Intent): Boolean {
        return intent.resolveActivity(packageManager) != null
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        Log.i(tag, "new intent to server $intent")
        handleDeepLink(intent)
    }


    private fun Intent.isLaunchFromHistory(): Boolean {
        return (this.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY).also {
            Log.d(tag, "isLaunchFromHistory: $it")
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        intent?.let {
            if (it.isLaunchFromHistory() || !isFromWebUrl(it)) {
                return
            }

            handleBrowserIntent(intent)
        }
    }

    private fun jumpToGalaxyStorePage(packageName: String) {
        val url = "samsungapps://ProductDetail/$packageName"

        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        val uri = Uri.parse(intent.dataString)
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun isFromMarketIntent(intent: Intent?): Boolean {
        val scheme = getSchemeFromIntent(intent)
        return "market" == scheme
    }

    private fun isFromWebUrl(newIntent: Intent?): Boolean {
        val scheme = getSchemeFromIntent(newIntent)
        return scheme == "http" || scheme == "https"
    }

    private fun getSchemeFromIntent(newIntent: Intent?): String? {
        return newIntent?.data?.scheme
    }

    private fun handleBrowserIntent(intent: Intent) {
        if (intent.action.equals(Intent.ACTION_VIEW) && intent.data != null) {

            val url = intent.data.toString()
            Log.i(tag, "try forwarding url: $url")
            viewModel.sendMessageToClient(url)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: ServerViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshServerIpAddress(context)
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
            TextField(value = uiState.serverPort, onValueChange = { viewModel.setServerPort(it) })
            Button(
                onClick = { viewModel.toggleServer() },
                enabled = when (uiState.serverStatus) {
                    ServerStatus.IDLE, ServerStatus.CREATED -> true
                    else -> false
                }
            ) {
                Text(
                    text = when (uiState.serverStatus) {
                        ServerStatus.IDLE -> "CREATE"
                        ServerStatus.CREATING -> "WAIT"
                        ServerStatus.CREATED -> "STOP"
                        ServerStatus.STOPPING -> "STOPPING"
                    }
                )
            }
        }
    }
}