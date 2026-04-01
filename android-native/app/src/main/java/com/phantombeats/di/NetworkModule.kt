package com.phantombeats.di

import com.phantombeats.data.remote.api.PhantomApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import android.os.Build
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // En emulador usar 10.0.2.2; en dispositivo físico por USB usar 127.0.0.1 + adb reverse.
    private fun resolveBaseUrl(): String {
        val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)

        return if (isEmulator) {
            "http://10.0.2.2:3000/"
        } else {
            "http://127.0.0.1:3000/"
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Aquí en un futuro inyectamos Interceptors para el Token JWT
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(resolveBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providePhantomApi(retrofit: Retrofit): PhantomApi {
        return retrofit.create(PhantomApi::class.java)
    }
}
