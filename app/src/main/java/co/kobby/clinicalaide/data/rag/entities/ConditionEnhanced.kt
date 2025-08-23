package co.kobby.clinicalaide.data.rag.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conditions_enhanced")
data class ConditionEnhanced(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "chapter_number")
    val chapterNumber: Int?,
    
    @ColumnInfo(name = "section_number")
    val sectionNumber: String?,
    
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    
    @ColumnInfo(name = "clinical_features")
    val clinicalFeatures: String?,
    
    @ColumnInfo(name = "investigations")
    val investigations: String?,
    
    @ColumnInfo(name = "treatment")
    val treatment: String?,
    
    @ColumnInfo(name = "reference_citation")
    val referenceCitation: String?,
    
    @ColumnInfo(name = "ocr_source")
    val ocrSource: Boolean = true
)