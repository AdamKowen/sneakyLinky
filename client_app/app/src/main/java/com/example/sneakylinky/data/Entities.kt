// File: app/src/main/java/com/example/sneakylinky/data/Entities.kt
package com.example.sneakylinky.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Cached host entry (host, timestamp, status: 1=Trusted, 2=Suspicious, 3=Blacklisted)
@Entity(tableName = "host_cache")
data class CachedHostEntry(
    @PrimaryKey val hostAscii: String,
    val validatedAt: Long,
    val status: Int
)

@Entity(tableName = "whitelist")
data class WhitelistEntry(
    @PrimaryKey val hostAscii: String
)

@Entity(tableName = "blacklist")
data class BlacklistEntry(
    @PrimaryKey val hostAscii: String
)