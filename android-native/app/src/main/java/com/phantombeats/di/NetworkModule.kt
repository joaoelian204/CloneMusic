package com.phantombeats.di

import com.phantombeats.BuildConfig
import com.phantombeats.data.remote.api.InnerTubeApi
import com.phantombeats.data.remote.api.ItunesApi
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

    // En debug: local (emulador/USB). En release: backend cloud (Render).
    private fun resolveBaseUrl(): String {
        if (BuildConfig.USE_REMOTE_BACKEND) {
            return BuildConfig.REMOTE_BASE_URL
        }

        val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)

        return if (isEmulator) {
            BuildConfig.LOCAL_EMULATOR_BASE_URL
        } else {
            BuildConfig.LOCAL_DEVICE_BASE_URL
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            // Render puede tardar en arrancar en frio; damos margen para evitar falsos "sin conexion".
            .callTimeout(90, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
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
    fun provideInnerTubeRetrofit(okHttpClient: OkHttpClient): InnerTubeApi {
        return Retrofit.Builder()
            .baseUrl("https://music.youtube.com/")
            .client(okHttpClient)
            // Retornamos raw JsonObject con Gson
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(InnerTubeApi::class.java)
    }

    @Provides
    @Singleton
    fun provideItunesApi(okHttpClient: OkHttpClient): ItunesApi {
        return Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ItunesApi::class.java)
    }
} 
