package com.deivide.webtoontranslator

import android.app.Application
import com.deivide.webtoontranslator.di.AppContainer

class WebtoonApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
