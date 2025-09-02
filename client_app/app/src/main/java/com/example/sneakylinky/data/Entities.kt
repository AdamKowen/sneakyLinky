// File: app/src/main/java/com/example/sneakylinky/data/Entities.kt
package com.example.sneakylinky.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whitelist")
data class WhitelistEntry(
    @PrimaryKey val hostAscii: String
)

@Entity(tableName = "blacklist")
data class BlacklistEntry(
    @PrimaryKey val hostAscii: String
)
