package co.kobby.clinicalaide.data.rag

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import co.kobby.clinicalaide.data.rag.dao.RagDao
import co.kobby.clinicalaide.data.rag.entities.*

@Database(
    entities = [
        Chapter::class,
        ContentChunk::class,
        ConditionEnhanced::class,
        MedicationEnhanced::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RagDatabase : RoomDatabase() {
    
    abstract fun ragDao(): RagDao
    
    companion object {
        private const val DATABASE_NAME = "stg_rag.db"
        
        @Volatile
        private var INSTANCE: RagDatabase? = null
        
        fun getInstance(context: Context): RagDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RagDatabase::class.java,
                    DATABASE_NAME
                )
                    .createFromAsset("databases/stg_rag.db")
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}