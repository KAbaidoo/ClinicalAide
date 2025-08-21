package co.kobby.clinicalaide.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "stg_conditions",
    foreignKeys = [
        ForeignKey(
            entity = StgChapter::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["chapterId"])
    ]
)
data class StgCondition(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chapterId: Long,
    val conditionNumber: Int,
    val conditionName: String,
    val startPage: Int,
    val endPage: Int,
    val keywords: String
)