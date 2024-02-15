package com.kaltok.tcpip.client

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kaltok.tcpip.client.ui.ClientScreen
import com.kaltok.tcpip.client.ui.theme.ServerTheme
import com.kaltok.tcpip.client.ui.viewmodel.ClientViewModel
import kotlinx.coroutines.launch

class ClientActivity : ComponentActivity() {

    companion object {
        private const val tag = "KSH_TEST"
    }

    private lateinit var viewModel: ClientViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[ClientViewModel::class.java]

        setContent {
            ServerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClientScreen(viewModel)
                }
            }
        }

        Log.i(tag, "client launch")

        lifecycleScope.launch {
            viewModel.outputData.collect {
                // TODO you can control received
//                if (it.isNotEmpty()) {
//                    if (it.startsWith("http")) { // goto webview
//                        Log.i(tag, "cilent forward to webview $it")
//                        launchWebView(it)
//                    } else {
//                        Log.i(tag, "client output collect send intent $it")
//                        val intent = Intent.parseUri(it, Intent.URI_INTENT_SCHEME)
//                        val uri = Uri.parse(intent.dataString)
//                        startActivity(Intent(Intent.ACTION_VIEW, uri))
//                    }
//                }
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
        viewModel.sendMessage(getUriStringFromIntent(newIntent))
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