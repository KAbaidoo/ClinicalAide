package co.kobby.clinicalaide.di

import android.content.Context
import co.kobby.clinicalaide.data.rag.RagDatabase
import co.kobby.clinicalaide.data.rag.dao.RagDao
import co.kobby.clinicalaide.data.rag.search.SemanticSearchService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing database and repository dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    /**
     * Provides the RAG database for medical content search.
     */
    @Provides
    @Singleton
    fun provideRagDatabase(@ApplicationContext context: Context): RagDatabase {
        return RagDatabase.getInstance(context)
    }
    
    /**
     * Provides the RAG DAO from the database.
     */
    @Provides
    fun provideRagDao(database: RagDatabase): RagDao {
        return database.ragDao()
    }
    
    /**
     * Provides the semantic search service for medical content.
     */
    @Provides
    @Singleton
    fun provideSemanticSearchService(ragDao: RagDao): SemanticSearchService {
        return SemanticSearchService(ragDao)
    }
}