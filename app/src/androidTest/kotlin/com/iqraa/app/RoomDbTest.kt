package com.iqraa.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iqraa.core.data.db.IqraaDatabase
import com.iqraa.core.data.db.SessionEntity
import com.iqraa.core.data.db.WordFlagEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.runner.RunWith
import org.junit.Test

/** §15: Room schema v1 — DAO round-trip + checkpoint transaction on the CI emulator. */
@RunWith(AndroidJUnit4::class)
class RoomDbTest {
    @Test
    fun dao_roundTrip_checkpoint_and_history() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(ctx, IqraaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val session = SessionEntity(
                id = 0L, startedAt = 1000L, endedAt = null, mode = "tasmee",
                surah = 54, fromAyah = 10, toAyah = 12,
                modelId = "quran-asr-small", modelRev = "0c2744b9", preset = "CONSERVATIVE",
                configHash = "hash1", degradedLevel = 0,
                countsCorrect = 3, countsClose = 1, countsWrong = 0,
                countsMissed = 0, countsHinted = 0, countsExtra = 0,
                durationMs = 0L, deviceNote = null,
            )
            val flags = listOf(
                WordFlagEntity(0L, 4, 54, 10, "WRONG"),
                WordFlagEntity(0L, 7, 54, 11, "CLOSE"),
            )
            db.sessionDao().checkpoint(session, flags)

            val open = db.sessionDao().latestOpenSession()
            assertNotNull(open)
            assertEquals(3, open!!.countsCorrect)
            assertEquals(listOf(4, 7), db.sessionDao().wordFlagsFor(open.id).map { it.globalWordIndex })
            assertEquals("WRONG", db.sessionDao().wordFlagsFor(open.id).first().state)

            // close → leaves history
            db.sessionDao().closeSession(open.id, endedAt = 2000L, durationMs = 1000L)
            assertNull(db.sessionDao().latestOpenSession())
            val historyFirst = kotlinx.coroutines.flow.first(db.sessionDao().history())
            assertEquals(1, historyFirst.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun dao_delete_all() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(ctx, IqraaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val session = SessionEntity(
                id = 0L, startedAt = 1L, endedAt = 2L, mode = "tasmee", surah = 1,
                fromAyah = 1, toAyah = 1, modelId = "m", modelRev = "r", preset = "p",
                configHash = "h", degradedLevel = 0,
                countsCorrect = 0, countsClose = 0, countsWrong = 0, countsMissed = 0,
                countsHinted = 0, countsExtra = 0, durationMs = 1L, deviceNote = null,
            )
            val id = db.sessionDao().upsertSession(session)
            db.sessionDao().deleteAllSessions()
            assertNull(db.sessionDao().sessionById(id))
        } finally {
            db.close()
        }
    }
}
