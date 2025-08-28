package co.kobby.clinicalaide.data.app.entities

import androidx.room.*
import java.time.Instant

/**
 * Entity representing a single message in a chat session.
 * Links to ChatSession via foreign key and stores both query and response.
 */
@Entity(
    tableName = "chat_history",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["timestamp"])
    ]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "history_id")
    val historyId: Long = 0,
    
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    
    @ColumnInfo(name = "query_text")
    val queryText: String,
    
    @ColumnInfo(name = "response_text")
    val responseText: String,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: String = Instant.now().toString(), // ISO 8601 format
    
    @ColumnInfo(name = "content_ids")
    val contentIds: String? = null, // JSON array of content IDs from stg_rag.db
    
    @ColumnInfo(name = "citations")
    val citations: String? = null, // JSON array of citation objects
    
    @ColumnInfo(name = "processing_time_ms")
    val processingTimeMs: Long? = null,
    
    @ColumnInfo(name = "similarity_score")
    val similarityScore: Float? = null // Average similarity score from semantic search
)