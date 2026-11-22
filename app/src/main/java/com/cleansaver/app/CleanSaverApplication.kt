package com.cleansaver.app

import android.app.Application
import androidx.work.Configuration
import com.cleansaver.app.di.AppContainer

class CleanSaverApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(container.workerFactory)
            .build()
}

