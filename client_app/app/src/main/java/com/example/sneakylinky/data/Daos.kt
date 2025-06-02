// File: app/src/main/java/com/example/sneakylinky/data/Daos.kt
package com.example.sneakylinky.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for the `trusted_hosts` table.
 */
@Dao
interface TrustedHostDao {

    /**
     * Return this host’s status (1, 2, or 3), or null if not present.
     *   1 = Trusted
     *   2 = Suspicious
     *   3 = Blacklisted
     */
    @Query("""
      SELECT status 
      FROM trusted_hosts 
      WHERE hostAscii = :host 
      LIMIT 1
    """)
    suspend fun getStatus(host: String): Int?

    /**
     * Insert new or replace existing row for a host.
     * If host already exists, validatedAt & status are overwritten.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trustedHost: TrustedHost)

    /**
     * Delete any host whose validatedAt is older than thresholdMillis (TTL logic).
     */
    @Query("DELETE FROM trusted_hosts WHERE validatedAt < :thresholdMillis")
    suspend fun deleteOlderThan(thresholdMillis: Long)
}

/**
 * DAO for the `whitelist` table.
 * Supports single‐insert/delete and bulk replace.
 */
@Dao
interface WhitelistDao {

    /**
     * Returns true if the given host is in the whitelist table.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM whitelist WHERE hostAscii = :host LIMIT 1)")
    suspend fun isWhitelisted(host: String): Boolean

    /**
     * Insert a new entry; if it already exists, ignore the conflict.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: WhitelistEntry)

    /**
     * Remove a single host from the whitelist.
     */
    @Query("DELETE FROM whitelist WHERE hostAscii = :host")
    suspend fun delete(host: String)

    /**
     * Remove all rows from the whitelist.
     */
    @Query("DELETE FROM whitelist")
    suspend fun clearAll()

    /**
     * Insert a list of entries at once (bulk insert). Conflicts (duplicate hostAscii) are ignored.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<WhitelistEntry>)
}

/**
 * DAO for the `blacklist` table.
 * Supports single‐insert/delete and bulk replace.
 */
@Dao
interface BlacklistDao {

    /**
     * Returns true if the given host is in the blacklist table.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM blacklist WHERE hostAscii = :host LIMIT 1)")
    suspend fun isBlacklisted(host: String): Boolean

    /**
     * Insert a new entry; if it already exists, ignore the conflict.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: BlacklistEntry)

    /**
     * Remove a single host from the blacklist.
     */
    @Query("DELETE FROM blacklist WHERE hostAscii = :host")
    suspend fun delete(host: String)

    /**
     * Remove all rows from the blacklist.
     */
    @Query("DELETE FROM blacklist")
    suspend fun clearAll()

    /**
     * Insert a list of entries at once (bulk insert). Conflicts are ignored.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<BlacklistEntry>)
}
