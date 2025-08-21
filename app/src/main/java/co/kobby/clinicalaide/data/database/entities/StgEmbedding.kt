package co.kobby.clinicalaide.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "stg_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = StgContentBlock::class,
            parentColumns = ["id"],
            childColumns = ["contentBlockId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["contentBlockId"])
    ]
)
data class StgEmbedding(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contentBlockId: Long,
    val embedding: String,
    val embeddingModel: String,
    val embeddingDimensions: Int = 768,
    val createdAt: Long = System.currentTimeMillis()
)