package com.example.sneakylinky

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SneakyLinkyApp : Application() {
    companion object {
        // Singleton instance to access app-wide context
        private lateinit var instance: SneakyLinkyApp

        // Accessor for application context from anywhere
        fun appContext(): Context = instance.applicationContext

        // Global coroutine scope that survives Activity lifecycle
        lateinit var appScope: CoroutineScope
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Save instance for global context access
        instance = this

        // Create a supervisor coroutine scope on the IO dispatcher
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}