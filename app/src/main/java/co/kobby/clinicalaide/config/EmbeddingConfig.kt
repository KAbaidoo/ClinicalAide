package co.kobby.clinicalaide.config

import co.kobby.clinicalaide.BuildConfig

/**
 * Configuration for the embedding service.
 * Controls which embedding generation method to use.
 */
object EmbeddingConfig {
    /**
     * Embedding generation modes.
     */
    enum class Mode {
        /**
         * Production mode: Real-time embedding generation for any text input.
         * Uses embedding weights loaded from binary file.
         * This is the recommended mode for production.
         */
        PRODUCTION,
        
        /**
         * Pre-computed mode: Uses pre-computed embeddings from JSON.
         * Limited to 129 predefined queries.
         * Fallback for unknown queries may use mock embeddings.
         * Not recommended for production.
         */
        PRE_COMPUTED,
        
        /**
         * Mock mode: Generates deterministic mock embeddings.
         * For testing and development only.
         * Should never be used in production.
         */
        MOCK
    }
    
    /**
     * Current embedding mode.
     * In production builds, this is locked to PRODUCTION mode.
     * In debug builds, this can be changed for testing.
     */
    var currentMode: Mode = if (BuildConfig.IS_PRODUCTION_BUILD) {
        Mode.PRODUCTION
    } else {
        // Allow flexibility in debug builds
        Mode.PRODUCTION // Default to production even in debug for safety
    }
        set(value) {
            if (BuildConfig.IS_PRODUCTION_BUILD && value != Mode.PRODUCTION) {
                throw IllegalStateException(
                    "Cannot change embedding mode in production builds. " +
                    "Production builds must always use PRODUCTION mode."
                )
            }
            field = value
        }
    
    /**
     * Check if we're in production mode.
     */
    fun isProductionMode(): Boolean = currentMode == Mode.PRODUCTION
    
    /**
     * Check if we're in development/testing mode.
     */
    fun isDevelopmentMode(): Boolean = currentMode != Mode.PRODUCTION
    
    /**
     * Validate configuration for production deployment.
     * Throws exception if configuration is not suitable for production.
     */
    fun validateForProduction() {
        // Check build configuration
        if (BuildConfig.IS_PRODUCTION_BUILD) {
            if (currentMode != Mode.PRODUCTION) {
                throw IllegalStateException(
                    "CRITICAL: Production build detected but not using PRODUCTION mode! " +
                    "Current mode: $currentMode. This is a security violation."
                )
            }
            if (BuildConfig.EMBEDDING_MODE != "PRODUCTION") {
                throw IllegalStateException(
                    "CRITICAL: Build configuration mismatch! " +
                    "Expected PRODUCTION, got ${BuildConfig.EMBEDDING_MODE}"
                )
            }
        }
        
        // Additional validation
        if (currentMode != Mode.PRODUCTION) {
            throw IllegalStateException(
                "Invalid embedding configuration for production. " +
                "Current mode: $currentMode. " +
                "Production deployments must use PRODUCTION mode."
            )
        }
    }
    
    /**
     * Check if this is a production build.
     */
    fun isProductionBuild(): Boolean = BuildConfig.IS_PRODUCTION_BUILD
    
    /**
     * Initialize and validate configuration.
     * Should be called during app startup.
     */
    fun initialize() {
        if (BuildConfig.IS_PRODUCTION_BUILD) {
            // Force production mode in release builds
            currentMode = Mode.PRODUCTION
            validateForProduction()
        }
    }
    
    /**
     * Get a description of the current mode.
     */
    fun getModeDescription(): String = when (currentMode) {
        Mode.PRODUCTION -> "Production: Real-time embedding generation for any text"
        Mode.PRE_COMPUTED -> "Pre-computed: Limited to 129 predefined queries"
        Mode.MOCK -> "Mock: Development only, generates fake embeddings"
    }
}