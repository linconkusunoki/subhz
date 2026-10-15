package com.subhz

import android.app.Application
import com.subhz.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Global application class. It runs before any Activity.
 */
class SubHzApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Koin Dependency Injection
        startKoin {
            androidContext(this@SubHzApp)
            modules(appModule)
        }
    }
}