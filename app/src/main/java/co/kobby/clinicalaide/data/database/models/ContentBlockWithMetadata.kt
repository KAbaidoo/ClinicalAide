package co.kobby.clinicalaide.data.database.models

import androidx.room.Embedded
import co.kobby.clinicalaide.data.database.entities.StgContentBlock

data class ContentBlockWithMetadata(
    @Embedded val contentBlock: StgContentBlock,
    val conditionName: String,
    val chapterTitle: String
)