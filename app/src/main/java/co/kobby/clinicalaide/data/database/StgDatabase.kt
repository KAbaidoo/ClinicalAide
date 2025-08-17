package co.kobby.clinicalaide.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import co.kobby.clinicalaide.data.database.dao.StgDao
import co.kobby.clinicalaide.data.database.entities.*

@Database(
    entities = [
        StgChapter::class,
        StgCondition::class,
        StgContentBlock::class,
        StgEmbedding::class,
        StgMedication::class,
        StgCrossReference::class,
        StgSearchCache::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StgDatabase : RoomDatabase() {
    abstract fun stgDao(): StgDao
}