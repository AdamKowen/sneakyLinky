// File: app/src/main/java/com/example/sneakylinky/data/Entities.kt
package com.example.sneakylinky.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 1) Main cache table: stores host + when it was last validated + status flag.
 *    - hostAscii: String → primary key (e.g. "example.com")
 *    - validatedAt: Long → epoch millis when first inserted
 *    - status: Int → 1=Trusted, 2=Suspicious, 3=Blacklisted
 */
@Entity(tableName = "trusted_hosts")
data class TrustedHost(
    @PrimaryKey val hostAscii: String,
    val validatedAt: Long,
    val status: Int
)

/**
 * 2) Whitelist table: one‐column primary key list of hosts considered “always safe.”
 *    - hostAscii: String → primary key (e.g. "google.com")
 */
@Entity(tableName = "whitelist")
data class WhitelistEntry(
    @PrimaryKey val hostAscii: String
)

/**
 * 3) Blacklist table: one‐column primary key list of hosts considered “always blocked.”
 *    - hostAscii: String → primary key (e.g. "malicious.com")
 */
@Entity(tableName = "blacklist")
data class BlacklistEntry(
    @PrimaryKey val hostAscii: String
)
