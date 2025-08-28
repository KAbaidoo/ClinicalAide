package co.kobby.clinicalaide.data.app

import co.kobby.clinicalaide.data.app.dao.ChatDao
import co.kobby.clinicalaide.data.app.entities.ChatMessage
import co.kobby.clinicalaide.data.app.entities.ChatSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for app-specific data operations (chat sessions and messages).
 * Coordinates between the UI layer and the app database.
 */
@Singleton
class AppRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    
    // ==================== SESSION MANAGEMENT ====================
    
    /**
     * Create a new chat session
     */
    suspend fun createSession(): ChatSession = withContext(Dispatchers.IO) {
        val session = ChatSession(
            startTime = Instant.now().toString(),
            isActive = true
        )
        val sessionId = chatDao.insertSession(session)
        session.copy(sessionId = sessionId)
    }
    
    /**
     * Get or create the current active session
     */
    suspend fun getOrCreateActiveSession(): ChatSession = withContext(Dispatchers.IO) {
        chatDao.getMostRecentActiveSession() ?: createSession()
    }
    
    /**
     * Get a specific session by ID
     */
    suspend fun getSession(sessionId: Long): ChatSession? = withContext(Dispatchers.IO) {
        chatDao.getSession(sessionId)
    }
    
    /**
     * Observe all chat sessions
     */
    fun observeSessions(): Flow<List<ChatSession>> = chatDao.getAllSessions()
    
    /**
     * Delete a session and all its messages
     */
    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteSessionById(sessionId)
    }
    
    /**
     * Clear the current chat (deactivate session)
     */
    suspend fun clearCurrentChat(sessionId: Long) = withContext(Dispatchers.IO) {
        chatDao.deactivateSession(sessionId)
    }
    
    // ==================== MESSAGE OPERATIONS ====================
    
    /**
     * Save a message exchange (query and response)
     */
    suspend fun saveMessage(
        sessionId: Long,
        queryText: String,
        responseText: String,
        contentIds: List<Int>? = null,
        citations: String? = null,
        processingTimeMs: Long? = null,
        similarityScore: Float? = null
    ): ChatMessage = withContext(Dispatchers.IO) {
        val timestamp = Instant.now().toString()
        
        val message = ChatMessage(
            sessionId = sessionId,
            queryText = queryText,
            responseText = responseText,
            timestamp = timestamp,
            contentIds = contentIds?.let { "[${it.joinToString(",")}]" },
            citations = citations,
            processingTimeMs = processingTimeMs,
            similarityScore = similarityScore
        )
        
        val messageId = chatDao.insertMessage(message)
        chatDao.incrementMessageCount(sessionId, timestamp)
        
        // Update session title if it's the first message
        val messageCount = chatDao.getMessageCount(sessionId)
        if (messageCount == 1) {
            val title = queryText.take(50) // First 50 chars of first query
            chatDao.updateSessionTitle(sessionId, title)
        }
        
        message.copy(historyId = messageId)
    }
    
    /**
     * Get messages for a session
     */
    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> = 
        chatDao.getMessagesForSession(sessionId)
    
    /**
     * Get recent messages for context
     */
    suspend fun getRecentMessages(sessionId: Long, limit: Int = 2): List<ChatMessage> = 
        withContext(Dispatchers.IO) {
            chatDao.getRecentMessages(sessionId, limit)
        }
    
    // ==================== SEARCH OPERATIONS ====================
    
    /**
     * Search through message history
     */
    suspend fun searchMessages(query: String, limit: Int = 20): List<ChatMessage> = 
        withContext(Dispatchers.IO) {
            chatDao.searchMessages(query, limit)
        }
    
    /**
     * Search for sessions containing query
     */
    suspend fun searchSessions(query: String): List<ChatSession> = 
        withContext(Dispatchers.IO) {
            chatDao.searchSessions(query)
        }
    
    // ==================== CONTEXT DETECTION ====================
    
    /**
     * Determine if a query needs conversation context
     */
    fun shouldIncludeContext(query: String): Boolean {
        val followUpTerms = listOf(
            "what about",
            "how about",
            "and",
            "also",
            "additionally",
            "furthermore",
            "moreover",
            "next",
            "then",
            "after that"
        )
        
        val queryLower = query.lowercase()
        
        // Check for follow-up terms
        val hasFollowUpTerm = followUpTerms.any { queryLower.contains(it) }
        
        // Check for short queries that might be follow-ups
        val isShortQuery = query.split(" ").size < 5
        
        // Check for referential terms
        val hasReferentialTerm = queryLower.contains("it") || 
                                 queryLower.contains("this") || 
                                 queryLower.contains("that") ||
                                 queryLower.contains("they") ||
                                 queryLower.contains("them")
        
        return hasFollowUpTerm || (isShortQuery && hasReferentialTerm)
    }
    
    // ==================== STATISTICS ====================
    
    /**
     * Get chat statistics
     */
    suspend fun getChatStatistics(): ChatDao.ChatStatistics = withContext(Dispatchers.IO) {
        chatDao.getChatStatistics()
    }
    
    /**
     * Get average messages per session
     */
    suspend fun getAverageMessagesPerSession(): Float = withContext(Dispatchers.IO) {
        chatDao.getAverageMessagesPerSession() ?: 0f
    }
    
    // ==================== CLEANUP ====================
    
    /**
     * Clean up old data (sessions and messages older than cutoff)
     */
    suspend fun cleanupOldData(daysToKeep: Int = 30) = withContext(Dispatchers.IO) {
        val cutoffTime = Instant.now().minusSeconds(daysToKeep.toLong() * 24 * 60 * 60).toString()
        chatDao.cleanupOldData(cutoffTime)
    }
}