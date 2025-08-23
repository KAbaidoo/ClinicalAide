package co.kobby.clinicalaide

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ClinicalAideApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Database initialization handled by Hilt + Room.createFromAsset()
    }
}