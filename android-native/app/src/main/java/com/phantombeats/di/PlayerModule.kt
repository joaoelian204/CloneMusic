package com.phantombeats.di

import android.content.Context
import com.phantombeats.player.PhantomPlayerController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun providePhantomPlayerController(@ApplicationContext context: Context): PhantomPlayerController {
        return PhantomPlayerController(context)
    }
}
