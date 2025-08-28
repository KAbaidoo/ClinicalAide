package co.kobby.clinicalaide.data.app.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Entity representing a chat session in the app database.
 * Each session contains multiple messages and tracks conversation metadata.
 */
@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "session_id")
    val sessionId: Long = 0,
    
    @ColumnInfo(name = "start_time")
    val startTime: String = Instant.now().toString(), // ISO 8601 format
    
    @ColumnInfo(name = "last_message_time")
    val lastMessageTime: String? = null, // ISO 8601 format
    
    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0,
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    
    @ColumnInfo(name = "title")
    val title: String? = null // Optional session title derived from first query
)