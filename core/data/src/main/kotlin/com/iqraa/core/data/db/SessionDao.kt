package com.iqraa.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** §15 SessionEntity — one completed or checkpointed session row. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startedAt: Long,
    val endedAt: Long?,
    val mode: String,
    val surah: Int,
    val fromAyah: Int,
    val toAyah: Int,
    val modelId: String,
    val modelRev: String,
    val preset: String,
    val configHash: String,
    val degradedLevel: Int,
    val countsCorrect: Int,
    val countsClose: Int,
    val countsWrong: Int,
    val countsMissed: Int,
    val countsHinted: Int,
    val countsExtra: Int,
    val durationMs: Long,
    val deviceNote: String?,
)

/** §15 WordFlagEntity — the tappable flagged-words list (state per word). */
@Entity(tableName = "word_flags", primaryKeys = ["sessionId", "globalWordIndex"])
data class WordFlagEntity(
    val sessionId: Long,
    val globalWordIndex: Int,
    val surah: Int,
    val ayah: Int,
    val state: String,
)

/** DAO for sessions + checkpoints (§11: write after each FINAL observation). */
@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun sessionById(id: Long): SessionEntity?

    /** §11 process-death: the latest session without endedAt = the interrupted one. */
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestOpenSession(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE endedAt IS NOT NULL ORDER BY endedAt DESC")
    fun history(): Flow<List<SessionEntity>>

    @Query("UPDATE sessions SET endedAt = :endedAt, durationMs = :durationMs WHERE id = :id")
    suspend fun closeSession(id: Long, endedAt: Long, durationMs: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWordFlags(flags: List<WordFlagEntity>)

    @Query("SELECT * FROM word_flags WHERE sessionId = :sessionId ORDER BY globalWordIndex")
    suspend fun wordFlagsFor(sessionId: Long): List<WordFlagEntity>

    @Query("DELETE FROM word_flags WHERE sessionId = :sessionId")
    suspend fun clearWordFlags(sessionId: Long)

    /** Atomic checkpoint: session counts + flagged words in one transaction. */
    @Transaction
    suspend fun checkpoint(session: SessionEntity, flags: List<WordFlagEntity>): Long {
        val id = upsertSession(session)
        val withId = flags.map { it.copy(sessionId = if (it.sessionId == 0L) id else it.sessionId) }
        clearWordFlags(id)
        upsertWordFlags(withId)
        return id
    }
}
