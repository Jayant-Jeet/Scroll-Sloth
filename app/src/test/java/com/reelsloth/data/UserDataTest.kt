package com.reelsloth.data

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserDataTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val exports = File(context.cacheDir, "exports-test").apply { deleteRecursively() }
    private var capturesDeleted = 0
    private var calibrationDeleted = 0
    private val userData = UserData(
        reelDao = database.reelDao(),
        commentaryDao = database.commentaryDao(),
        exportDirectory = exports,
        deleteCaptures = { capturesDeleted++ },
        deleteCalibration = { calibrationDeleted++ },
        clock = { 1_792_454_400_000 },
        zone = { ZoneId.of("UTC") },
    )

    @After
    fun tearDown() {
        database.close()
        exports.deleteRecursively()
    }

    @Test
    fun exportWritesEveryEventAndReplacesOlderExports() = runBlocking {
        exports.mkdirs()
        val old = File(exports, "scroll-sloth-reels-2026-01-01.csv").apply { writeText("old") }
        database.reelDao().insert(ReelEventEntity(timestampMs = 2_000, platform = "YOUTUBE", dwellMs = 3_000))
        database.reelDao().insert(ReelEventEntity(timestampMs = 1_000, platform = "INSTAGRAM", dwellMs = 2_500))

        val file = userData.exportCsv()

        assertEquals("scroll-sloth-reels-2026-10-20.csv", file.name)
        assertFalse(old.exists())
        assertEquals(
            listOf(
                CsvExporter.HEADER,
                "1000,1970-01-01T00:00:01Z,INSTAGRAM,2500",
                "2000,1970-01-01T00:00:02Z,YOUTUBE,3000",
            ),
            file.readLines(),
        )
    }

    @Test
    fun eraseAllRemovesEventsCommentaryCapturesAndExports() = runBlocking {
        database.reelDao().insert(ReelEventEntity(timestampMs = 1_000, platform = "INSTAGRAM", dwellMs = 2_500))
        database.commentaryDao().upsert(CommentaryEntity(periodKey = "DAY:2026-10-19", tone = "GENTLE", text = "Calm.", source = "TEMPLATE", retries = 0, createdAtMs = 1))
        val export = userData.exportCsv()

        userData.eraseAll()

        assertTrue(database.reelDao().allEvents().isEmpty())
        assertNull(database.commentaryDao().get("DAY:2026-10-19"))
        assertEquals(1, capturesDeleted)
        assertEquals(1, calibrationDeleted)
        assertFalse(export.exists())
    }
}
