package com.example.sneakylinky.service.urlanalyzer

import com.example.sneakylinky.SneakyLinkyApp
import com.example.sneakylinky.data.AppDatabase

/**
 * Source of whitelist hostnames for MED checks.
 * Prod default hits Room; unit tests override [loader] with a fake list.
 */
object WhitelistSource {
    @Volatile
    var loader: suspend () -> List<String> = {
        val db = AppDatabase.getInstance(SneakyLinkyApp.appContext())
        db.whitelistDao().getAll().map { it.hostAscii.lowercase() }
    }
}
