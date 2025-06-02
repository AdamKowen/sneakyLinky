// File: app/src/main/java/com/example/sneakylinky/data/AppDatabase.kt
package com.example.sneakylinky.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database holding three tables: trusted_hosts, whitelist, blacklist.
 * Version = 2 because we added whitelist & blacklist alongside the existing trusted_hosts.
 */
@Database(
    entities = [
        TrustedHost::class,
        WhitelistEntry::class,
        BlacklistEntry::class
    ],
    version  = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /** DAO for the trusted_hosts table */
    abstract fun trustedHostDao(): TrustedHostDao

    /** DAO for the whitelist table */
    abstract fun whitelistDao(): WhitelistDao

    /** DAO for the blacklist table */
    abstract fun blacklistDao(): BlacklistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton AppDatabase. On first call, Room will:
         *  • Create (or open) the file "app_database" in internal storage
         *  • Create tables for all listed entities (or run a migration if needed)
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
