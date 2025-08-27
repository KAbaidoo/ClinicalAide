package co.kobby.clinicalaide.data.rag.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "embeddings",
    foreignKeys = [
        ForeignKey(
            entity = Content::class,
            parentColumns = ["content_id"],
            childColumns = ["content_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Embedding(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "embedding_id")
    val embeddingId: Int = 0,
    
    @ColumnInfo(name = "content_id")
    val contentId: Int,
    
    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Embedding

        if (embeddingId != other.embeddingId) return false
        if (contentId != other.contentId) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = embeddingId
        result = 31 * result + contentId
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}