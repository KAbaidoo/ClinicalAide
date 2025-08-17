package co.kobby.clinicalaide.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stg_cross_references")
data class StgCrossReference(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fromConditionId: Long,
    val toConditionId: Long,
    val referenceType: String,
    val description: String? = null
)