package co.kobby.clinicalaide.ui.chat

import co.kobby.clinicalaide.data.app.entities.ChatMessage
import co.kobby.clinicalaide.data.app.entities.ChatSession

/**
 * UI state for the chat screen.
 * Represents all the data needed to render the chat interface.
 */
data class ChatUiState(
    val currentSession: ChatSession? = null,
    val messages: List<MessageUI> = emptyList(),
    val sessions: List<SessionPreview> = emptyList(),
    val currentQuery: String = "",
    val isProcessing: Boolean = false,
    val error: String? = null,
    val isDrawerOpen: Boolean = false,
    val showClearConfirmation: Boolean = false
)

/**
 * UI representation of a message with display formatting.
 */
data class MessageUI(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val timestamp: String,
    val citations: List<Citation> = emptyList(),
    val isLoading: Boolean = false,
    val processingTimeMs: Long? = null,
    val similarityScore: Float? = null,
    val isError: Boolean = false
)

/**
 * Citation information for a message.
 */
data class Citation(
    val chapter: String,
    val section: String?,
    val pageNumber: Int,
    val title: String? = null
)

/**
 * Preview of a chat session for the drawer.
 */
data class SessionPreview(
    val sessionId: Long,
    val title: String,
    val timestamp: String,
    val messageCount: Int,
    val isActive: Boolean
)

/**
 * Converter functions for database entities to UI models.
 */
object UiConverters {
    
    fun ChatMessage.toMessageUIs(): List<MessageUI> {
        val userMessage = MessageUI(
            id = historyId * 2,
            text = queryText,
            isUser = true,
            timestamp = formatTimestamp(timestamp),
            citations = emptyList()
        )
        
        val botMessage = MessageUI(
            id = historyId * 2 + 1,
            text = responseText,
            isUser = false,
            timestamp = formatTimestamp(timestamp),
            citations = parseCitations(citations),
            processingTimeMs = processingTimeMs,
            similarityScore = similarityScore
        )
        
        return listOf(userMessage, botMessage)
    }
    
    fun ChatSession.toSessionPreview(): SessionPreview {
        return SessionPreview(
            sessionId = sessionId,
            title = title ?: "Session $sessionId",
            timestamp = formatTimestamp(lastMessageTime ?: startTime),
            messageCount = messageCount,
            isActive = isActive
        )
    }
    
    private fun formatTimestamp(isoTimestamp: String): String {
        // Simple formatting - can be enhanced with proper date formatting
        return try {
            val instant = java.time.Instant.parse(isoTimestamp)
            val formatter = java.time.format.DateTimeFormatter
                .ofPattern("h:mm a")
                .withZone(java.time.ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            isoTimestamp
        }
    }
    
    private fun parseCitations(citationsJson: String?): List<Citation> {
        // Parse JSON citations - simplified for now
        // In production, use proper JSON parsing library
        return if (citationsJson != null) {
            try {
                // Placeholder - implement proper JSON parsing
                listOf(
                    Citation(
                        chapter = "Chapter 1",
                        section = "Section 1.2",
                        pageNumber = 45,
                        title = "Treatment Guidelines"
                    )
                )
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
}