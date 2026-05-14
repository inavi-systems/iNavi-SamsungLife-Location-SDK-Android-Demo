package com.inavisys.samsunglifelocationdemo

import android.app.Application
import com.inavisys.location.samsunglife.SamsungLifeService

class App: Application() {
    override fun onCreate() {
        super.onCreate()

        SamsungLifeService.initialize(this, "YOUR_LICENSE_KEY")
    }
}