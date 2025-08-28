package co.kobby.clinicalaide.data.app.dao

import androidx.room.*
import co.kobby.clinicalaide.data.app.entities.ChatMessage
import co.kobby.clinicalaide.data.app.entities.ChatSession
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for chat-related database operations.
 * Manages chat sessions and message history.
 */
@Dao
interface ChatDao {
    
    // ==================== SESSION OPERATIONS ====================
    
    @Insert
    suspend fun insertSession(session: ChatSession): Long
    
    @Update
    suspend fun updateSession(session: ChatSession)
    
    @Delete
    suspend fun deleteSession(session: ChatSession)
    
    @Query("SELECT * FROM chat_sessions ORDER BY last_message_time DESC, start_time DESC")
    fun getAllSessions(): Flow<List<ChatSession>>
    
    @Query("SELECT * FROM chat_sessions WHERE session_id = :sessionId")
    suspend fun getSession(sessionId: Long): ChatSession?
    
    @Query("SELECT * FROM chat_sessions WHERE is_active = 1 ORDER BY last_message_time DESC LIMIT 1")
    suspend fun getMostRecentActiveSession(): ChatSession?
    
    @Query("UPDATE chat_sessions SET message_count = message_count + 1, last_message_time = :timestamp WHERE session_id = :sessionId")
    suspend fun incrementMessageCount(sessionId: Long, timestamp: String)
    
    @Query("UPDATE chat_sessions SET is_active = 0 WHERE session_id = :sessionId")
    suspend fun deactivateSession(sessionId: Long)
    
    @Query("UPDATE chat_sessions SET title = :title WHERE session_id = :sessionId")
    suspend fun updateSessionTitle(sessionId: Long, title: String)
    
    @Query("DELETE FROM chat_sessions WHERE session_id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)
    
    @Query("DELETE FROM chat_sessions WHERE start_time < :cutoffTime")
    suspend fun deleteSessionsOlderThan(cutoffTime: String)
    
    // ==================== MESSAGE OPERATIONS ====================
    
    @Insert
    suspend fun insertMessage(message: ChatMessage): Long
    
    @Query("SELECT * FROM chat_history WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_history WHERE session_id = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(sessionId: Long, limit: Int = 2): List<ChatMessage>
    
    @Query("SELECT * FROM chat_history WHERE history_id = :messageId")
    suspend fun getMessage(messageId: Long): ChatMessage?
    
    @Query("DELETE FROM chat_history WHERE session_id = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)
    
    @Query("SELECT COUNT(*) FROM chat_history WHERE session_id = :sessionId")
    suspend fun getMessageCount(sessionId: Long): Int
    
    // ==================== SEARCH OPERATIONS ====================
    
    @Query("""
        SELECT * FROM chat_history 
        WHERE query_text LIKE '%' || :query || '%' 
           OR response_text LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchMessages(query: String, limit: Int = 20): List<ChatMessage>
    
    @Query("""
        SELECT DISTINCT s.* FROM chat_sessions s
        INNER JOIN chat_history h ON s.session_id = h.session_id
        WHERE h.query_text LIKE '%' || :query || '%' 
           OR h.response_text LIKE '%' || :query || '%'
        ORDER BY s.last_message_time DESC
    """)
    suspend fun searchSessions(query: String): List<ChatSession>
    
    // ==================== STATISTICS ====================
    
    @Query("SELECT COUNT(*) FROM chat_sessions")
    suspend fun getTotalSessionCount(): Int
    
    @Query("SELECT COUNT(*) FROM chat_history")
    suspend fun getTotalMessageCount(): Int
    
    @Query("SELECT AVG(message_count) FROM chat_sessions WHERE message_count > 0")
    suspend fun getAverageMessagesPerSession(): Float?
    
    @Query("""
        SELECT 
            (SELECT COUNT(*) FROM chat_sessions) as sessionCount,
            (SELECT COUNT(*) FROM chat_history) as messageCount,
            (SELECT COUNT(*) FROM chat_sessions WHERE is_active = 1) as activeSessionCount
    """)
    suspend fun getChatStatistics(): ChatStatistics
    
    data class ChatStatistics(
        val sessionCount: Int,
        val messageCount: Int,
        val activeSessionCount: Int
    )
    
    // ==================== CLEANUP OPERATIONS ====================
    
    @Query("DELETE FROM chat_history WHERE timestamp < :cutoffTime")
    suspend fun deleteOldMessages(cutoffTime: String)
    
    @Query("UPDATE chat_sessions SET is_active = 0 WHERE last_message_time < :cutoffTime")
    suspend fun deactivateOldSessions(cutoffTime: String)
    
    @Transaction
    suspend fun cleanupOldData(cutoffTime: String) {
        deleteOldMessages(cutoffTime)
        deleteSessionsOlderThan(cutoffTime)
    }
}