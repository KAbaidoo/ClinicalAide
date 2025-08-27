package co.kobby.clinicalaide.data.rag.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapters")
data class Chapter(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "chapter_id")
    val chapterId: Int = 0,
    
    @ColumnInfo(name = "chapter_number")
    val chapterNumber: String,
    
    @ColumnInfo(name = "chapter_title")
    val chapterTitle: String
)