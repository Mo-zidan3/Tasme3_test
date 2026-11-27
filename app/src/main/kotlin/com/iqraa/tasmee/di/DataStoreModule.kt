package com.iqraa.tasmee.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.iqraa.core.data.settings.DataStoreKeys
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The app's DataStore ALONE (run 45977626337): instrumented tests replace exactly this
 * module via @TestInstallIn (TestDataStoreModule) with a per-test file — Hilt builds a
 * NEW SingletonComponent per test, so the shared-file provider in AppModule put a second
 * DataStore on iqraa_settings.preferences_pb while the first was still open
 * ("multiple DataStores active"). Production is unaffected (one Application component
 * per process); every other AppModule binding stays live.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun dataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(DataStoreKeys.FILE) }
}
