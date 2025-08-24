// File: app/src/main/java/com/example/sneakylinky/data/Daos.kt
package com.example.sneakylinky.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sneakylinky.service.report.LinkHistory
import com.example.sneakylinky.service.report.LocalCheck
import com.example.sneakylinky.service.report.RemoteStatus
import com.example.sneakylinky.service.report.ReportSendState
import com.example.sneakylinky.service.report.UserVerdict

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


@Dao
interface LinkHistoryDao {

    // Create / update
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: LinkHistory): Long

    @Update
    suspend fun update(row: LinkHistory)

    // ---- Queries ----

    // ✅ this is the one missing in your build
    @Query("""
        SELECT * FROM link_history
        WHERE url = :url
        ORDER BY createdAt DESC
        LIMIT 1
    """)
    suspend fun latestForUrl(url: String): LinkHistory?

    @Query("""
        SELECT lh.*
        FROM link_history AS lh
        WHERE lh.createdAt = (
            SELECT MAX(createdAt) FROM link_history WHERE url = lh.url
        )
        ORDER BY lh.createdAt DESC
        LIMIT :limit
    """)
    suspend fun recentDistinct(limit: Int = 200): List<LinkHistory>

    // Point updates (keep signatures exactly)
    @Query("""
        UPDATE link_history SET 
            localCheck = :localCheck,
            finalUrl = :finalUrl,
            canonHost = :canonHost,
            updatedAt = :now
        WHERE id = :id
    """)
    suspend fun markLocalResult(
        id: Long,
        localCheck: LocalCheck,
        finalUrl: String?,
        canonHost: String?,
        now: Long
    )

    @Query("""
        UPDATE link_history SET 
            openedInBrowser = :opened,
            updatedAt = :now
        WHERE id = :id
    """)
    suspend fun markOpened(id: Long, opened: Boolean, now: Long)

    @Query("""
        UPDATE link_history SET 
            remoteStatus = :status,
            remoteScore = :score,
            updatedAt = :now
        WHERE id = :id
    """)
    suspend fun markRemote(id: Long, status: RemoteStatus, score: Float?, now: Long)

    @Query("""
        UPDATE link_history SET 
            userVerdict = :verdict,
            userReason = :reason,
            reportSendState = :state,
            updatedAt = :now
        WHERE id = :id
    """)
    suspend fun markUserReport(
        id: Long,
        verdict: UserVerdict,
        reason: String?,
        state: ReportSendState,
        now: Long
    )

    @Query("""
        UPDATE link_history SET 
            reportSendState = :state,
            serverAckId = :ackId,
            serverAckMessage = :ackMsg,
            updatedAt = :now
        WHERE id = :id
    """)
    suspend fun markServerAck(
        id: Long,
        state: ReportSendState,
        ackId: String?,
        ackMsg: String?,
        now: Long
    )
}

