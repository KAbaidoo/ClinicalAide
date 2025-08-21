package co.kobby.clinicalaide.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "stg_content_blocks",
    foreignKeys = [
        ForeignKey(
            entity = StgCondition::class,
            parentColumns = ["id"],
            childColumns = ["conditionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["conditionId"])
    ]
)
data class StgContentBlock(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conditionId: Long,
    val blockType: String,
    val content: String,
    val pageNumber: Int,
    val orderInCondition: Int,
    val clinicalContext: String = "general",
    val severityLevel: String? = null,
    val evidenceLevel: String? = null,
    val keywords: String,
    val relatedBlockIds: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)