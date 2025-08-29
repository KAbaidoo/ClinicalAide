package co.kobby.clinicalaide

import android.app.Application
import android.util.Log
import co.kobby.clinicalaide.config.EmbeddingConfig
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ClinicalAideApplication : Application() {
    
    companion object {
        private const val TAG = "ClinicalAideApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize and validate embedding configuration
        initializeEmbeddingService()
        
        // Database initialization handled by Hilt + Room.createFromAsset()
    }
    
    /**
     * Initialize and validate the embedding service configuration.
     * This ensures production builds always use production embeddings.
     */
    private fun initializeEmbeddingService() {
        try {
            // Initialize configuration
            EmbeddingConfig.initialize()
            
            // Log current configuration
            Log.i(TAG, "=".repeat(60))
            Log.i(TAG, "Embedding Service Configuration")
            Log.i(TAG, "Build Type: ${if (BuildConfig.IS_PRODUCTION_BUILD) "PRODUCTION" else "DEBUG"}")
            Log.i(TAG, "Embedding Mode: ${EmbeddingConfig.currentMode}")
            Log.i(TAG, "Mode Description: ${EmbeddingConfig.getModeDescription()}")
            Log.i(TAG, "=".repeat(60))
            
            // Validate for production if this is a release build
            if (BuildConfig.IS_PRODUCTION_BUILD) {
                EmbeddingConfig.validateForProduction()
                Log.i(TAG, "✓ Production embedding configuration validated successfully")
                
                // Extra safety check
                if (EmbeddingConfig.currentMode != EmbeddingConfig.Mode.PRODUCTION) {
                    val error = "CRITICAL ERROR: Production build not using PRODUCTION embeddings!"
                    Log.e(TAG, error)
                    throw IllegalStateException(error)
                }
            } else {
                Log.i(TAG, "Debug build - embedding mode can be changed for testing")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize embedding service", e)
            
            // In production, we must crash if embedding service is misconfigured
            if (BuildConfig.IS_PRODUCTION_BUILD) {
                throw RuntimeException(
                    "Critical: Cannot start production app with invalid embedding configuration",
                    e
                )
            }
            
            // In debug, we can continue but log the error
            Log.e(TAG, "Continuing in debug mode despite embedding configuration error")
        }
    }
}