package co.kobby.clinicalaide

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ClinicalAideApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}