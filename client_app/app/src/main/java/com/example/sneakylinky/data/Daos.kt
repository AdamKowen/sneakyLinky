// File: app/src/main/java/com/example/sneakylinky/data/Daos.kt
package com.example.sneakylinky.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HostCacheDao {

    @Query(
        """
        SELECT status 
        FROM host_cache 
        WHERE hostAscii = :host 
        LIMIT 1
    """
    )
    suspend fun getStatus(host: String): Int?     // Return host status (1=Trusted, 2=Suspicious, 3=Blacklisted) or null if not found

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cachedHost: CachedHostEntry)     // Insert or replace host; updates validatedAt and status if it exists


    @Query(
        """
        DELETE FROM host_cache
        WHERE validatedAt < :thresholdMillis
        """
    )
    suspend fun deleteOlderThan(thresholdMillis: Long)     // Delete hosts with validatedAt older than thresholdMillis (TTL)

}


@Dao
interface WhitelistDao {


    @Query("SELECT * FROM whitelist")
    suspend fun getAll(): List<WhitelistEntry>     // Return all whitelist entries


    @Query(
        """
        SELECT EXISTS(
            SELECT 1 
            FROM whitelist 
            WHERE hostAscii = :host 
            LIMIT 1)
            """
    )
    suspend fun isWhitelisted(host: String): Boolean     //Returns true if the given host is in the whitelist table.

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: WhitelistEntry)     //Insert a new entry; if it already exists, ignore the conflict.


    @Query(
        """
        DELETE FROM whitelist 
        WHERE hostAscii = :host
        """
    )
    suspend fun delete(host: String)     //Remove a single host from the whitelist.


    @Query("DELETE FROM whitelist")
    suspend fun clearAll()     //Remove all rows from the whitelist.


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<WhitelistEntry>)     //Insert a list of entries at once (bulk insert). Conflicts (duplicate hostAscii) are ignored.

}


@Dao
interface BlacklistDao {

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM blacklist 
            WHERE hostAscii = :host 
            LIMIT 1)
        """
    )
    suspend fun isBlacklisted(host: String): Boolean     // Check if host is blacklisted


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: BlacklistEntry)     // Insert entry if not already in blacklist


    @Query(
        """
        DELETE FROM blacklist 
        WHERE hostAscii = :host
        """
    )
    suspend fun delete(host: String)     // Delete a single host from blacklist


    @Query("DELETE FROM blacklist")
    suspend fun clearAll()    // Clear all blacklist entries


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<BlacklistEntry>)     // Bulk insert entries; ignore conflicts

}
