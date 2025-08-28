package co.kobby.clinicalaide.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.kobby.clinicalaide.data.app.AppRepository
import co.kobby.clinicalaide.data.app.entities.ChatMessage
import co.kobby.clinicalaide.data.app.entities.ChatSession
import co.kobby.clinicalaide.data.rag.RagRepository
import co.kobby.clinicalaide.services.MockLLMService
import co.kobby.clinicalaide.ui.chat.UiConverters.toMessageUIs
import co.kobby.clinicalaide.ui.chat.UiConverters.toSessionPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * ViewModel for the chat screen.
 * Manages chat state, message processing, and session management.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val ragRepository: RagRepository,
    private val llmService: MockLLMService
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // Current session
    private var currentSessionId: Long? = null
    
    init {
        loadSessions()
        loadOrCreateSession()
    }
    
    // ==================== SESSION MANAGEMENT ====================
    
    /**
     * Load or create the initial session.
     */
    private fun loadOrCreateSession() {
        viewModelScope.launch {
            try {
                val session = appRepository.getOrCreateActiveSession()
                currentSessionId = session.sessionId
                _uiState.update { it.copy(currentSession = session) }
                loadMessagesForSession(session.sessionId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load session: ${e.message}") }
            }
        }
    }
    
    /**
     * Load all chat sessions for the drawer.
     */
    private fun loadSessions() {
        viewModelScope.launch {
            appRepository.observeSessions()
                .map { sessions -> sessions.map { it.toSessionPreview() } }
                .collect { sessionPreviews ->
                    _uiState.update { it.copy(sessions = sessionPreviews) }
                }
        }
    }
    
    /**
     * Load messages for a specific session.
     */
    private fun loadMessagesForSession(sessionId: Long) {
        viewModelScope.launch {
            appRepository.observeMessages(sessionId)
                .map { messages -> 
                    messages.flatMap { it.toMessageUIs() }
                }
                .collect { messageUIs ->
                    _uiState.update { it.copy(messages = messageUIs) }
                }
        }
    }
    
    /**
     * Start a new chat session.
     */
    fun startNewSession() {
        viewModelScope.launch {
            try {
                val session = appRepository.createSession()
                currentSessionId = session.sessionId
                _uiState.update { 
                    it.copy(
                        currentSession = session,
                        messages = emptyList(),
                        currentQuery = "",
                        error = null
                    )
                }
                loadMessagesForSession(session.sessionId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to create session: ${e.message}") }
            }
        }
    }
    
    /**
     * Load an existing session.
     */
    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            try {
                val session = appRepository.getSession(sessionId)
                if (session != null) {
                    currentSessionId = sessionId
                    _uiState.update { it.copy(currentSession = session) }
                    loadMessagesForSession(sessionId)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load session: ${e.message}") }
            }
        }
    }
    
    /**
     * Delete a session.
     */
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            try {
                appRepository.deleteSession(sessionId)
                if (currentSessionId == sessionId) {
                    loadOrCreateSession()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete session: ${e.message}") }
            }
        }
    }
    
    /**
     * Clear the current chat.
     */
    fun clearCurrentChat() {
        currentSessionId?.let { sessionId ->
            viewModelScope.launch {
                try {
                    appRepository.clearCurrentChat(sessionId)
                    startNewSession()
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = "Failed to clear chat: ${e.message}") }
                }
            }
        }
    }
    
    // ==================== MESSAGE HANDLING ====================
    
    /**
     * Update the current query text.
     */
    fun updateQuery(query: String) {
        _uiState.update { it.copy(currentQuery = query) }
    }
    
    /**
     * Send a query and process the response.
     */
    fun sendQuery() {
        val query = _uiState.value.currentQuery.trim()
        if (query.isBlank()) return
        
        val sessionId = currentSessionId ?: return
        
        viewModelScope.launch {
            try {
                // Clear query and set processing state
                _uiState.update { 
                    it.copy(
                        currentQuery = "",
                        isProcessing = true,
                        error = null
                    )
                }
                
                // Add user message to UI immediately
                val userMessage = MessageUI(
                    id = System.currentTimeMillis(),
                    text = query,
                    isUser = true,
                    timestamp = formatCurrentTime()
                )
                
                _uiState.update { 
                    it.copy(messages = it.messages + userMessage)
                }
                
                // Add loading message
                val loadingMessage = MessageUI(
                    id = System.currentTimeMillis() + 1,
                    text = "",
                    isUser = false,
                    timestamp = formatCurrentTime(),
                    isLoading = true
                )
                
                _uiState.update { 
                    it.copy(messages = it.messages + loadingMessage)
                }
                
                // Determine if context is needed
                val needsContext = appRepository.shouldIncludeContext(query)
                
                // Build prompt with context if needed
                val finalQuery = if (needsContext) {
                    val recentMessages = appRepository.getRecentMessages(sessionId, 1)
                    if (recentMessages.isNotEmpty()) {
                        val previous = recentMessages.first()
                        llmService.buildContextualPrompt(
                            currentQuery = query,
                            previousQuery = previous.queryText,
                            previousResponse = previous.responseText
                        )
                    } else {
                        query
                    }
                } else {
                    query
                }
                
                // Search STG database for relevant content
                val startTime = System.currentTimeMillis()
                val ragContext = ragRepository.buildRagContext(finalQuery, maxContent = 5)
                
                // Generate response with mock LLM
                val llmResponse = llmService.generateResponse(query, ragContext)
                val processingTime = System.currentTimeMillis() - startTime
                
                // Parse citations for UI
                val citations = ragContext.citations.mapIndexed { index, citation ->
                    Citation(
                        chapter = "Chapter ${(index % 23) + 1}",
                        section = null,
                        pageNumber = extractPageNumber(citation),
                        title = "Ghana STG Reference"
                    )
                }
                
                // Remove loading message and add actual response
                _uiState.update { state ->
                    val messagesWithoutLoading = state.messages.filterNot { it.isLoading }
                    val botMessage = MessageUI(
                        id = System.currentTimeMillis() + 2,
                        text = llmResponse.text,
                        isUser = false,
                        timestamp = formatCurrentTime(),
                        citations = citations,
                        processingTimeMs = processingTime,
                        similarityScore = llmResponse.similarityScore
                    )
                    state.copy(
                        messages = messagesWithoutLoading + botMessage,
                        isProcessing = false
                    )
                }
                
                // Save to database
                appRepository.saveMessage(
                    sessionId = sessionId,
                    queryText = query,
                    responseText = llmResponse.text,
                    contentIds = llmResponse.contentIds,
                    citations = llmResponse.citations,
                    processingTimeMs = processingTime,
                    similarityScore = llmResponse.similarityScore
                )
                
            } catch (e: Exception) {
                // Remove loading message on error
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.filterNot { it.isLoading },
                        isProcessing = false,
                        error = "Failed to process query: ${e.message}"
                    )
                }
            }
        }
    }
    
    // ==================== UI ACTIONS ====================
    
    /**
     * Toggle the navigation drawer.
     */
    fun toggleDrawer() {
        _uiState.update { it.copy(isDrawerOpen = !it.isDrawerOpen) }
    }
    
    /**
     * Close the navigation drawer.
     */
    fun closeDrawer() {
        _uiState.update { it.copy(isDrawerOpen = false) }
    }
    
    /**
     * Show clear chat confirmation dialog.
     */
    fun showClearConfirmation() {
        _uiState.update { it.copy(showClearConfirmation = true) }
    }
    
    /**
     * Hide clear chat confirmation dialog.
     */
    fun hideClearConfirmation() {
        _uiState.update { it.copy(showClearConfirmation = false) }
    }
    
    /**
     * Clear any error messages.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * Handle quick action selection.
     */
    fun selectQuickAction(query: String) {
        updateQuery(query)
        sendQuery()
    }
    
    // ==================== HELPER FUNCTIONS ====================
    
    private fun formatCurrentTime(): String {
        val instant = Instant.now()
        val formatter = java.time.format.DateTimeFormatter
            .ofPattern("h:mm a")
            .withZone(java.time.ZoneId.systemDefault())
        return formatter.format(instant)
    }
    
    private fun extractPageNumber(citation: String): Int {
        // Extract page number from citation string like "Page 45"
        return citation.substringAfter("Page ", "0")
            .substringBefore(" ")
            .toIntOrNull() ?: 0
    }
}