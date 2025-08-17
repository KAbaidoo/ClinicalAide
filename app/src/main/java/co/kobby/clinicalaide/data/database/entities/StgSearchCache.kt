package co.kobby.clinicalaide.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stg_search_cache")
data class StgSearchCache(
    @PrimaryKey
    val queryHash: String,
    val results: String,
    val timestamp: Long = System.currentTimeMillis(),
    val hitCount: Int = 1
)