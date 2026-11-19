package com.cvforge.android

import android.app.Application
import com.cvforge.app.di.initKoin
import org.koin.android.ext.koin.androidContext

class CvForgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(BuildConfig.API_URL) { androidContext(this@CvForgeApplication) }
    }
}
