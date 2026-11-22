package com.iqraa.core.data

import com.iqraa.core.data.db.IqraaDatabase
import com.iqraa.core.data.db.SessionEntity
import com.iqraa.core.data.db.WordFlagEntity
import kotlinx.coroutines.flow.Flow

/**
 * §11/§15 session persistence: checkpoint after each FINAL observation, no audio, no
 * transcripts. Room write failure (storage full) degrades to warning + in-memory continue
 * per the §11 error table — the caller catches IOException and keeps the session running.
 */
class SessionRepository(private val db: IqraaDatabase) {
    private val dao = db.sessionDao()

    /** §11 checkpoint — cheap, transactional, after every FINAL. */
    suspend fun checkpoint(session: SessionEntity, flags: List<WordFlagEntity>) {
        dao.checkpoint(session, flags)
    }

    /** Process-death recovery: latest session without endedAt (read-only summary). */
    suspend fun latestOpenSession(): SessionEntity? = dao.latestOpenSession()

    suspend fun closeSession(id: Long, endedAt: Long, durationMs: Long) =
        dao.closeSession(id, endedAt, durationMs)

    fun history(): Flow<List<SessionEntity>> = dao.history()

    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    suspend fun deleteAll() = dao.deleteAllSessions()
}
