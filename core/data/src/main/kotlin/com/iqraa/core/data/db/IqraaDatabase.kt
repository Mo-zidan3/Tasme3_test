package com.iqraa.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * §15 IqraaDatabase — exportSchema=true, versioned migrations, NO destructive fallback.
 * Version 1 is the initial schema; every future schema change bumps [version] and appends
 * a [Migration] to [MIGRATIONS] (CI migration tests use room-testing against every version).
 */
@Database(
    entities = [SessionEntity::class, WordFlagEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class IqraaDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "iqraa-sessions.db"

        /**
         * All versioned migrations, oldest first. NEVER use fallbackToDestructiveMigration()
         * in release builds — §15 hard rule; a missing migration must crash loudly in
         * development and fail CI.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // version 1 → 2 migrations land here as the schema evolves.
        )
    }
}
