package com.reelsloth.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommentaryDaoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun entity(key: String, text: String = "Calm day.", source: String = "TEMPLATE") =
        CommentaryEntity(key, tone = "GENTLE", text = text, source = source, retries = 0, createdAtMs = 1_000)

    @Test
    fun upsertReplacesByPeriodKeyAndObserveFollows() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = database.commentaryDao()
        dao.observe("DAY:2026-10-19").test {
            assertNull(awaitItem())
            dao.upsert(entity("DAY:2026-10-19"))
            assertEquals("Calm day.", awaitItem()?.text)
            dao.upsert(entity("DAY:2026-10-19", text = "Gemma says hi.", source = "GEMMA"))
            assertEquals("GEMMA", awaitItem()?.source)
            cancelAndIgnoreRemainingEvents()
        }
        dao.upsert(entity("WEEK:2026-10-12"))
        assertEquals("Calm day.", dao.get("WEEK:2026-10-12")?.text)
        dao.deleteAll()
        assertNull(dao.get("DAY:2026-10-19"))
        database.close()
    }

    @Test
    fun migrationFromVersionOneKeepsReelEvents() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        // The exact version 1 schema Room created.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null).use { v1 ->
            v1.execSQL("CREATE TABLE IF NOT EXISTS `reel_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp_ms` INTEGER NOT NULL, `platform` TEXT NOT NULL, `dwell_ms` INTEGER NOT NULL)")
            v1.execSQL("CREATE TABLE IF NOT EXISTS `commentary` (`dayStartMs` INTEGER NOT NULL, `commentary` TEXT NOT NULL, PRIMARY KEY(`dayStartMs`))")
            v1.execSQL("INSERT INTO reel_events (timestamp_ms, platform, dwell_ms) VALUES (10, 'INSTAGRAM', 3000), (20, 'YOUTUBE', 4000)")
            v1.execSQL("INSERT INTO commentary VALUES (0, 'old')")
            v1.version = 1
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        assertEquals(2, database.reelDao().countBetween(0, 100))
        database.commentaryDao().upsert(entity("MONTH:2026-09-01"))
        assertEquals("MONTH:2026-09-01", database.commentaryDao().get("MONTH:2026-09-01")?.periodKey)
        database.close()
        context.deleteDatabase(name)
        Unit
    }
}
