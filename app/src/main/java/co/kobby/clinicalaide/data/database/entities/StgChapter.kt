package co.kobby.clinicalaide.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stg_chapters")
data class StgChapter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chapterNumber: Int,
    val chapterTitle: String,
    val startPage: Int,
    val endPage: Int,
    val description: String? = null
)