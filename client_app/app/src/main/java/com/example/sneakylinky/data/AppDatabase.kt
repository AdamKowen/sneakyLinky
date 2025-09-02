// File: app/src/main/java/com/example/sneakylinky/data/AppDatabase.kt
package com.example.sneakylinky.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.sneakylinky.service.report.LinkHistory

@Database(
    entities = [
        WhitelistEntry::class,
        BlacklistEntry::class,
        LinkHistory::class
    ],
    version = 5,
    exportSchema = false
)


abstract class AppDatabase : RoomDatabase() {
    abstract fun whitelistDao(): WhitelistDao
    abstract fun blacklistDao(): BlacklistDao
    abstract fun linkHistoryDao(): LinkHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    // Dev-friendly: will drop/recreate on version change
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
