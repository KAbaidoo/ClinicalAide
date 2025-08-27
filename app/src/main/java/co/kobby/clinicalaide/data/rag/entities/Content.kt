package co.kobby.clinicalaide.data.rag.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "content",
    foreignKeys = [
        ForeignKey(
            entity = Section::class,
            parentColumns = ["section_id"],
            childColumns = ["section_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["section_id"]),
        Index(value = ["page_number"])
    ]
)
data class Content(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "content_id")
    val contentId: Int = 0,
    
    @ColumnInfo(name = "section_id")
    val sectionId: Int,
    
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    
    @ColumnInfo(name = "content_text")
    val contentText: String,
    
    @ColumnInfo(name = "content_type")
    val contentType: String
)