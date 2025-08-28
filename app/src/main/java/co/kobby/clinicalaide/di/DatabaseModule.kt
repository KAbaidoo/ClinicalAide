package co.kobby.clinicalaide.di

import android.content.Context
import co.kobby.clinicalaide.data.app.AppDatabase
import co.kobby.clinicalaide.data.app.AppRepository
import co.kobby.clinicalaide.data.app.dao.ChatDao
import co.kobby.clinicalaide.data.rag.RagDatabase
import co.kobby.clinicalaide.data.rag.RagRepository
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
    
    // ==================== RAG DATABASE (STG Content) ====================
    
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
    
    /**
     * Provides the RAG repository for medical content operations.
     */
    @Provides
    @Singleton
    fun provideRagRepository(
        ragDao: RagDao,
        semanticSearchService: SemanticSearchService
    ): RagRepository {
        return RagRepository(ragDao, semanticSearchService)
    }
    
    // ==================== APP DATABASE (Chat History) ====================
    
    /**
     * Provides the app database for chat sessions and history.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }
    
    /**
     * Provides the chat DAO from the app database.
     */
    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }
    
    /**
     * Provides the app repository for chat operations.
     */
    @Provides
    @Singleton
    fun provideAppRepository(chatDao: ChatDao): AppRepository {
        return AppRepository(chatDao)
    }
}