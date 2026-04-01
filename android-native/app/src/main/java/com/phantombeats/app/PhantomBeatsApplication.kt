package com.phantombeats.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Punto de entrada principal de la aplicación.
 * @HiltAndroidApp detona la generación de código de Dagger Hilt 
 * para gestionar todas las inyecciones de dependencias en Android.
 */
@HiltAndroidApp
class PhantomBeatsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Aquí eventualmente inicializaremos utilidades globales como madereros (Timber), 
        // Crashlytics, o configuraciones tempranas del gestor offline.
    }
}