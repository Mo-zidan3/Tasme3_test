package com.iqraa.tasmee.di

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.iqraa.core.data.SessionRepository
import com.iqraa.core.data.db.IqraaDatabase
import com.iqraa.core.data.settings.SettingsRepository
import com.iqraa.feature.reader.SurahListSource
import com.iqraa.feature.reader.TanzilSurahListSource
import com.iqraa.feature.recite.ChunkSourceProvider
import com.iqraa.feature.recite.MicChunkSourceProvider
import com.iqraa.tasmee.OnboardingGate
import com.iqraa.tasmee.PermissionChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * V-048 (issue #11): the app's Hilt graph — the wiring Phase 3 deferred ("full Hilt graph
 * lands with B2 DI wiring"). Everything the NavHost routes need: DataStore + Room +
 * repositories, the capture-source provider, and the onboarding/permission seams. The
 * reader's surah source lives in [SurahSourceModule] — its own module so instrumented
 * tests can uninstall exactly that seam (run 45963636991). The DataStore provider lives
 * in [DataStoreModule] — its own module so instrumented tests can replace it with a
 * per-test file (run 45977626337: "multiple DataStores active" on the shared file).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun settingsRepository(dataStore: DataStore<Preferences>): SettingsRepository = SettingsRepository(dataStore)

    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): IqraaDatabase =
        Room
            .databaseBuilder(context, IqraaDatabase::class.java, IqraaDatabase.NAME)
            .addMigrations(*IqraaDatabase.MIGRATIONS)
            .build()

    @Provides
    @Singleton
    fun sessionRepository(db: IqraaDatabase): SessionRepository = SessionRepository(db)

    @Provides
    @Singleton
    fun chunkSourceProvider(): ChunkSourceProvider = MicChunkSourceProvider()

    @Provides
    @Singleton
    fun onboardingGate(settings: SettingsRepository): OnboardingGate =
        object : OnboardingGate {
            override suspend fun wasCompleted(): Boolean = settings.onboardingCompletedNow()

            override suspend fun complete() = settings.setOnboardingCompleted()
        }

    @Provides
    @Singleton
    fun permissionChecker(
        @ApplicationContext context: Context,
    ): PermissionChecker =
        PermissionChecker {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        }
}

/**
 * The reader's surah source ALONE (V-048, run 45963636991): ReaderErrorState/
 * ReaderEmptyState replace this seam via @BindValue, which ADDS a binding — alongside
 * AppModule's @Provides for the same type it is [Dagger/DuplicateBindings]. In its own
 * module, those tests @UninstallModules exactly this; every other AppModule binding
 * (DataStore, Room, repositories, capture, onboarding) stays live.
 */
@Module
@InstallIn(SingletonComponent::class)
object SurahSourceModule {
    @Provides
    @Singleton
    fun surahListSource(): SurahListSource = TanzilSurahListSource()
}
