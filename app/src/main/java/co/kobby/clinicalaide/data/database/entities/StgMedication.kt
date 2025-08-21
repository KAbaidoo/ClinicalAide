package co.kobby.clinicalaide.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "stg_medications",
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
data class StgMedication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conditionId: Long,
    val medicationName: String,
    val dosage: String,
    val frequency: String,
    val duration: String,
    val route: String,
    val ageGroup: String,
    val weightBased: Boolean = false,
    val contraindications: String? = null,
    val sideEffects: String? = null,
    val evidenceLevel: String? = null,
    val pageNumber: Int
)