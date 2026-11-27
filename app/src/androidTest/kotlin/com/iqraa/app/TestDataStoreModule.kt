package com.iqraa.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.iqraa.tasmee.di.DataStoreModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.TestInstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.UUID
import javax.inject.Singleton

/**
 * Instrumented-only DataStore (run 45977626337): each test's fresh SingletonComponent
 * gets its OWN file under cacheDir — no shared-file "multiple DataStores active" lock
 * against a still-open store from the previous test, and a clean onboarding flag per
 * test (a fixed file would carry the previous test's completed flag across classes).
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object TestDataStoreModule {
    @Provides
    @Singleton
    fun dataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File(context.cacheDir, "iqraa_test_${UUID.randomUUID()}.preferences_pb")
        }
}
