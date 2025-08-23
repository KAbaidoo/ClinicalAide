package co.kobby.clinicalaide.data.rag.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapters")
data class Chapter(
    @PrimaryKey
    val id: Int,
    
    @ColumnInfo(name = "number")
    val number: Int,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "start_page")
    val startPage: Int
)