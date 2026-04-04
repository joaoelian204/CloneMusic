package com.phantombeats.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

/**
 * Punto de entrada principal de la aplicación.
 * @HiltAndroidApp detona la generación de código de Dagger Hilt 
 * para gestionar todas las inyecciones de dependencias en Android.
 */
@HiltAndroidApp
class PhantomBeatsApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // Limitar Coil a 15% de memoria RAM 
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false) // Forzar caché UI
            .build()
    }
}