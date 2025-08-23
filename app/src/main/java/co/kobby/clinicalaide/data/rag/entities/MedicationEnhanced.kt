package co.kobby.clinicalaide.data.rag.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications_enhanced")
data class MedicationEnhanced(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "generic_name")
    val genericName: String,
    
    @ColumnInfo(name = "chapter_number")
    val chapterNumber: Int?,
    
    @ColumnInfo(name = "section_number")
    val sectionNumber: String?,
    
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    
    @ColumnInfo(name = "strength")
    val strength: String?,
    
    @ColumnInfo(name = "route")
    val route: String?,
    
    @ColumnInfo(name = "dosage_info")
    val dosageInfo: String?,
    
    @ColumnInfo(name = "reference_citation")
    val referenceCitation: String?,
    
    @ColumnInfo(name = "ocr_source")
    val ocrSource: Boolean = true
)