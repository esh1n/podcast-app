package com.example.jetcaster.di

import com.example.jetcaster.data.*
import com.example.jetcaster.data.room.TransactionRunner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object RepoModule {

    @Provides
    @Singleton
    fun providePodcastsRepository(podcastFetcher: PodcastsFetcher,
                                  podcastStore: PodcastStore,
                                  episodeStore: EpisodeStore,
                                  categoryStore: CategoryStore,
                                  transactionRunner: TransactionRunner
    ): PodcastsRepository {
        return PodcastsRepository(
            podcastsFetcher = podcastFetcher,
            podcastStore = podcastStore,
            episodeStore = episodeStore,
            categoryStore = categoryStore,
            transactionRunner = transactionRunner,
            mainDispatcher = Dispatchers.Main
        )
    }

}