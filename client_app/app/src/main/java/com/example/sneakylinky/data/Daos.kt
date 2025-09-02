// File: app/src/main/java/com/example/sneakylinky/data/Daos.kt
package com.example.sneakylinky.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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
