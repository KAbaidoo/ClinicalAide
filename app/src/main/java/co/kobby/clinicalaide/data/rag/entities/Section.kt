package co.kobby.clinicalaide.data.rag.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(
            entity = Chapter::class,
            parentColumns = ["chapter_id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Section::class,
            parentColumns = ["section_id"],
            childColumns = ["parent_section_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chapter_id"]),
        Index(value = ["parent_section_id"])
    ]
)
data class Section(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "section_id")
    val sectionId: Int = 0,
    
    @ColumnInfo(name = "chapter_id")
    val chapterId: Int,
    
    @ColumnInfo(name = "section_number")
    val sectionNumber: String?,
    
    @ColumnInfo(name = "section_title")
    val sectionTitle: String,
    
    @ColumnInfo(name = "parent_section_id")
    val parentSectionId: Int?
)