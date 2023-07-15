package com.kaltok.tcpip.server

import android.app.Application
import timber.log.Timber

class ServerApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())
        Timber.tag("KSH_TEST:Server")
    }
}