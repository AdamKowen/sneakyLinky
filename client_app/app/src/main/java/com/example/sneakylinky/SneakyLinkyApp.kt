package com.example.sneakylinky

import android.app.Application
import android.content.Context

class SneakyLinkyApp : Application() {
    companion object {
        private lateinit var instance: SneakyLinkyApp
        fun appContext(): Context = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}