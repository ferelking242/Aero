package com.velobrowser.di

import android.content.Context
import com.velobrowser.core.adblocker.AdBlocker
import com.velobrowser.data.local.datastore.SettingsDataStore
import com.velobrowser.data.local.db.VeloDatabase
import com.velobrowser.data.local.db.dao.*
import com.velobrowser.data.repository.*
import com.velobrowser.domain.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVeloDatabase(@ApplicationContext context: Context): VeloDatabase =
        VeloDatabase.create(context)

    @Provides
    fun provideProfileDao(db: VeloDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideHistoryDao(db: VeloDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideBookmarkDao(db: VeloDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideDownloadDao(db: VeloDatabase): DownloadDao = db.downloadDao()

    @Provides
    @Singleton
    fun provideProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository = impl

    @Provides
    @Singleton
    fun provideHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository = impl

    @Provides
    @Singleton
    fun provideBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository = impl

    @Provides
    @Singleton
    fun provideDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository = impl

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore =
        SettingsDataStore(context)

    @Provides
    @Singleton
    fun provideAdBlocker(): AdBlocker = AdBlocker()
}
