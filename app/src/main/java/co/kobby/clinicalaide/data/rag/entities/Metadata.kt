package co.kobby.clinicalaide.data.rag.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "metadata",
    foreignKeys = [
        ForeignKey(
            entity = Content::class,
            parentColumns = ["content_id"],
            childColumns = ["content_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["content_id"])
    ]
)
data class Metadata(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "metadata_id")
    val metadataId: Int = 0,
    
    @ColumnInfo(name = "content_id")
    val contentId: Int,
    
    @ColumnInfo(name = "key")
    val key: String,
    
    @ColumnInfo(name = "value")
    val value: String
)