package com.example.jetcaster.di

import android.content.Context
import androidx.room.Room
import com.example.jetcaster.BuildConfig

import com.example.jetcaster.data.CategoryStore
import com.example.jetcaster.data.EpisodeStore
import com.example.jetcaster.data.PodcastStore
import com.example.jetcaster.data.PodcastsFetcher
import com.example.jetcaster.data.room.JetcasterDatabase
import com.example.jetcaster.data.room.TransactionRunner
import com.example.jetcaster.data.room.TransactionRunnerDao
import com.rometools.rome.io.SyndFeedInput
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.LoggingEventListener
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideOkhttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http_cache"), (20 * 1024 * 1024).toLong()))
            .apply {
                if (BuildConfig.DEBUG) eventListenerFactory(LoggingEventListener.Factory())
            }
            .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JetcasterDatabase =
        Room.databaseBuilder(context, JetcasterDatabase::class.java, "data.db")
            // This is not recommended for normal apps, but the goal of this sample isn't to
            // showcase all of Room.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideTransactionRunner(database: JetcasterDatabase): TransactionRunner =
        database.transactionRunnerDao()

    @Provides
    @Singleton
    fun provideSyndFeedInput(): SyndFeedInput = SyndFeedInput()

    @Provides
    @Singleton
    fun providePodcastsFetcher(
        okHttpClient: OkHttpClient,
        syndFeedInput: SyndFeedInput
    ): PodcastsFetcher = PodcastsFetcher(okHttpClient, syndFeedInput, Dispatchers.IO)

    @Provides
    @Singleton
    fun providePodcastStore(
        database: JetcasterDatabase,
        transactionRunner: TransactionRunner
    ): PodcastStore = PodcastStore(
        database.podcastsDao(),
        database.podcastFollowedEntryDao(),
        transactionRunner
    )

    @Provides
    @Singleton
    fun provideCategoryStore(database: JetcasterDatabase): CategoryStore = CategoryStore(
        categoriesDao = database.categoriesDao(),
        categoryEntryDao = database.podcastCategoryEntryDao(),
        episodesDao = database.episodesDao(),
        podcastsDao = database.podcastsDao()
    )

    @Provides
    @Singleton
    fun provideEpisodeStore(database: JetcasterDatabase): EpisodeStore =
        EpisodeStore(database.episodesDao())

}