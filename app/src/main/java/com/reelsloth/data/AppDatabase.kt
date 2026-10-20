package com.reelsloth.data

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [ReelEventEntity::class, CommentaryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reelDao(): ReelDao
    abstract fun commentaryDao(): CommentaryDao

    companion object {
        /** Replaces the unused version 1 commentary table. Reel events are kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("DROP TABLE IF EXISTS `commentary`")
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `commentary` (`period_key` TEXT NOT NULL, `tone` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, `source` TEXT NOT NULL, `retries` INTEGER NOT NULL, " +
                        "`created_at_ms` INTEGER NOT NULL, PRIMARY KEY(`period_key`))",
                )
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}