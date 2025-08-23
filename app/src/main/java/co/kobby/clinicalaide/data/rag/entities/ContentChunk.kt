package co.kobby.clinicalaide.data.rag.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "content_chunks")
data class ContentChunk(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "chunk_type")
    val chunkType: String,
    
    @ColumnInfo(name = "source_id")
    val sourceId: Int?,
    
    @ColumnInfo(name = "chapter_number")
    val chapterNumber: Int?,
    
    @ColumnInfo(name = "chapter_title")
    val chapterTitle: String?,
    
    @ColumnInfo(name = "section_number")
    val sectionNumber: String?,
    
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    
    @ColumnInfo(name = "condition_name")
    val conditionName: String?,
    
    @ColumnInfo(name = "reference_citation")
    val referenceCitation: String,
    
    @ColumnInfo(name = "metadata")
    val metadata: String?,
    
    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContentChunk

        if (id != other.id) return false
        if (content != other.content) return false
        if (chunkType != other.chunkType) return false
        if (sourceId != other.sourceId) return false
        if (chapterNumber != other.chapterNumber) return false
        if (chapterTitle != other.chapterTitle) return false
        if (sectionNumber != other.sectionNumber) return false
        if (pageNumber != other.pageNumber) return false
        if (conditionName != other.conditionName) return false
        if (referenceCitation != other.referenceCitation) return false
        if (metadata != other.metadata) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + chunkType.hashCode()
        result = 31 * result + (sourceId?.hashCode() ?: 0)
        result = 31 * result + (chapterNumber ?: 0)
        result = 31 * result + (chapterTitle?.hashCode() ?: 0)
        result = 31 * result + (sectionNumber?.hashCode() ?: 0)
        result = 31 * result + pageNumber
        result = 31 * result + (conditionName?.hashCode() ?: 0)
        result = 31 * result + referenceCitation.hashCode()
        result = 31 * result + (metadata?.hashCode() ?: 0)
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + (createdAt?.hashCode() ?: 0)
        return result
    }
}