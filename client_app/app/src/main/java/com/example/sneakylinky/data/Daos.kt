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
interface WhitelistDao {

    @Query("SELECT * FROM whitelist")
    suspend fun getAll(): List<WhitelistEntry>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM whitelist 
            WHERE hostAscii = :host 
            LIMIT 1)
    """)
    suspend fun isWhitelisted(host: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: WhitelistEntry)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<WhitelistEntry>)

    @Query("DELETE FROM whitelist WHERE hostAscii = :host")
    suspend fun delete(host: String)

    @Query("DELETE FROM whitelist")
    suspend fun clearAll()
}

@Dao
interface BlacklistDao {

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM blacklist 
            WHERE hostAscii = :host 
            LIMIT 1)
    """)
    suspend fun isBlacklisted(host: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: BlacklistEntry)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<BlacklistEntry>)

    @Query("DELETE FROM blacklist WHERE hostAscii = :host")
    suspend fun delete(host: String)

    @Query("DELETE FROM blacklist")
    suspend fun clearAll()
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


    // Stream every run, newest first (updates automatically)
    @Query("""
        SELECT * FROM link_history
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    fun recentStream(limit: Int = 200): kotlinx.coroutines.flow.Flow<List<LinkHistory>>

    //clear all rows
    @Query("DELETE FROM link_history")
    suspend fun clearAll()



    @Query("""
    SELECT * FROM link_history
    ORDER BY createdAt DESC
    LIMIT :limit
""")
    suspend fun recent(limit: Int = 200): List<LinkHistory>


}

