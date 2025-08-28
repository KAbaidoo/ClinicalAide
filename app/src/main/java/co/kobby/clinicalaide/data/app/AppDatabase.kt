package co.kobby.clinicalaide.data.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import co.kobby.clinicalaide.data.app.dao.ChatDao
import co.kobby.clinicalaide.data.app.entities.ChatMessage
import co.kobby.clinicalaide.data.app.entities.ChatSession

/**
 * App database for storing chat sessions and message history.
 * This is separate from the STG content database (stg_rag.db).
 */
@Database(
    entities = [
        ChatSession::class,
        ChatMessage::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun chatDao(): ChatDao
    
    companion object {
        private const val DATABASE_NAME = "app_database.db"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // For development - use migrations in production
                .build()
        }
    }
}