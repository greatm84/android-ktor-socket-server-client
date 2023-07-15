package com.kaltok.tcpip.server

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsSessionToken
import timber.log.Timber

class ServerCustomTab : CustomTabsService() {
    private var bindIntent: Intent? = null

    override fun onCreate() {
        // Kick off the first access to avoid random StrictMode violations in clients.
        super.onCreate()
    }

    override fun onBind(intent: Intent?): IBinder {
        bindIntent = intent
        Timber.i("intent " + intent?.toUri(Intent.URI_ALLOW_UNSAFE))
        return super.onBind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        super.onUnbind(intent)
        return false // No support for onRebind().
    }

    override fun warmup(flags: Long): Boolean {
        return false
    }

    override fun newSession(sessionToken: CustomTabsSessionToken): Boolean {
        return true
    }


    override fun mayLaunchUrl(
        sessionToken: CustomTabsSessionToken,
        url: Uri?,
        extras: Bundle?,
        otherLikelyBundles: MutableList<Bundle>?
    ): Boolean {
        return false
    }

    override fun extraCommand(commandName: String, args: Bundle?): Bundle? {
        return null
    }

    override fun updateVisuals(sessionToken: CustomTabsSessionToken, bundle: Bundle?): Boolean {
        return false
    }

    override fun requestPostMessageChannel(
        sessionToken: CustomTabsSessionToken,
        postMessageOrigin: Uri
    ): Boolean {
        return false
    }

    override fun postMessage(
        sessionToken: CustomTabsSessionToken,
        message: String,
        extras: Bundle?
    ): Int {
        return RESULT_FAILURE_DISALLOWED
    }

    override fun validateRelationship(
        sessionToken: CustomTabsSessionToken,
        relation: Int,
        origin: Uri,
        extras: Bundle?
    ): Boolean {
        return false
    }

    override fun receiveFile(
        sessionToken: CustomTabsSessionToken,
        uri: Uri,
        purpose: Int,
        extras: Bundle?
    ): Boolean {
        return false
    }

    override fun cleanUpSession(sessionToken: CustomTabsSessionToken): Boolean {
        return false
    }
}