# Product Requirements Document: Chat Interface & UI Development
## Ghana STG Clinical Chatbot - Android Application

**Version**: 1.0  
**Date**: August 28, 2025  
**Status**: Implementation Ready  
**Document Type**: UI Development PRD - Source of Truth

---

## 1. Executive Summary

### 1.1 Purpose
This document defines the complete requirements for the chat interface and UI development phase of the Ghana STG Clinical Chatbot. It serves as the single source of truth for implementing the user interface using Jetpack Compose.

### 1.2 Scope
The UI development encompasses:
- Primary chat interface with message history
- Session management via navigation drawer
- Hybrid conversation handling (standalone + context-aware)
- Integration with dual database architecture
- Offline-first design principles
- Accessibility and performance standards

### 1.3 Key Deliverables
1. **Chat Screen** - Primary conversational interface
2. **Session Management** - Side panel with history and navigation
3. **Message Components** - User/bot message display with citations
4. **Database Integration** - Dual database coordination (stg_rag.db + app_database.db)
5. **State Management** - ViewModels with reactive UI updates

---

## 2. User Interface Requirements

### 2.1 Application Entry Point
**Requirement ID**: UI-001  
**Priority**: P0 (Critical)

The application SHALL open directly to the Chat Screen, displaying:
- Most recent chat session (if exists)
- New session (if no history)
- Welcome message for new users

### 2.2 Chat Screen Layout

#### 2.2.1 Top App Bar
**Requirement ID**: UI-002  
**Components**:
- **Title**: "Clinical AI Assistant" (left-aligned)
- **Menu Icon**: Hamburger menu (opens navigation drawer)
- **Clear Action**: Clear current chat (with confirmation)
- **Material Design 3**: Dynamic color theming

#### 2.2.2 Message Display Area
**Requirement ID**: UI-003  
**Implementation**: LazyColumn with reverse layout

**Message Components**:
```kotlin
data class MessageUI(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val timestamp: String,
    val citations: List<Citation>?,
    val isLoading: Boolean = false
)

data class Citation(
    val chapter: String,
    val section: String,
    val pageNumber: Int
)
```

**Visual Design**:
- **User Messages**: Right-aligned, primary color background, max 80% width
- **Bot Messages**: Left-aligned, surface variant background, max 85% width
- **Citations**: Chips below message text, clickable for details
- **Timestamps**: Below message, caption typography
- **Loading State**: Animated dots ("...") with 300ms cycle

#### 2.2.3 Input Area
**Requirement ID**: UI-004  
**Components**:
- **TextField**: Multi-line support, max 4 lines visible
- **Send Button**: Icon button, enabled when text present
- **Quick Actions**: Optional chips for common queries
- **Voice Input**: Optional microphone button (future enhancement)

### 2.3 Session Management Panel

#### 2.3.1 Navigation Drawer
**Requirement ID**: UI-005  
**Type**: Modal Navigation Drawer  
**Width**: 280dp (phones), 360dp (tablets)  
**Opening**: Swipe from left edge or menu icon tap

#### 2.3.2 Drawer Content
**Requirement ID**: UI-006

**Header Section**:
- Title: "Chat History"
- Subtitle: Session count (e.g., "12 sessions")

**Primary Action**:
- "Start New Chat" button (prominent, filled)

**Session List**:
- **Layout**: LazyColumn with Cards
- **Session Card Contents**:
  - Date/Time (e.g., "Today, 9:25 PM")
  - Query preview (first 50 characters)
  - Message count indicator
  - Actions: Resume (default tap), Delete (icon)

**Empty State**:
- Illustration or icon
- Text: "No chat history yet"
- Subtitle: "Start a conversation to begin"

### 2.4 Conversation Handling

#### 2.4.1 Message Processing
**Requirement ID**: UI-007

**Standalone Queries** (Default):
- Detect: Query length > 5 words OR no referential terms
- Context: Query + relevant STG content only
- Processing: Direct semantic search in stg_rag.db

**Follow-up Queries**:
- Detect: Contains ["what about", "also", "and", "how about", "additionally"]
- Detect: Query length < 5 words
- Context: Last 1-2 messages + new query + STG content
- Processing: Include chat_history for coherent responses

#### 2.4.2 Loading States
**Requirement ID**: UI-008

**Query Processing Stages**:
1. **Sending** (0-100ms): Disable input, show sending indicator
2. **Processing** (100ms-2s): Show animated dots
3. **Typing** (simulated): Progressive text display (30ms/char)
4. **Complete**: Enable input, scroll to bottom

**Loading Animation**:
```kotlin
@Composable
fun LoadingDots(modifier: Modifier = Modifier) {
    var dots by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            dots = if (dots < 3) dots + 1 else 1
        }
    }
    Text(
        text = ".".repeat(dots),
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
    )
}
```

### 2.5 Error Handling

#### 2.5.1 User-Facing Errors
**Requirement ID**: UI-009

**Error Types & Messages**:
- **Empty Query**: "Please enter a question"
- **Vague Query**: "Please provide more details about your query"
- **Database Error**: "Unable to access medical data. Please try again."
- **Processing Error**: "Unable to process query. Please rephrase and try again."
- **Storage Full**: "Storage limit reached. Please delete old sessions."

**Error Display**:
- Snackbar for transient errors
- In-chat message for query-specific errors
- Dialog for critical errors

---

## 3. Database Architecture

### 3.1 Dual Database System

#### 3.1.1 STG Database (stg_rag.db)
**Type**: Read-only  
**Location**: Assets folder, copied on first launch  
**Size**: ~3.33MB  
**Contents**:
- 23 chapters of medical guidelines
- 831 sections with hierarchy
- 664 content entries
- 664 vector embeddings (384 dimensions)
- 957 metadata entries

#### 3.1.2 App Database (app_database.db)
**Type**: Read-write  
**Location**: App internal storage  
**Purpose**: Session and chat history storage

### 3.2 App Database Schema

```sql
-- Chat sessions table
CREATE TABLE chat_sessions (
    session_id INTEGER PRIMARY KEY AUTOINCREMENT,
    start_time TEXT NOT NULL,      -- ISO 8601 format
    last_message_time TEXT,         -- ISO 8601 format
    message_count INTEGER DEFAULT 0,
    is_active INTEGER DEFAULT 1     -- Boolean flag
);

-- Chat history table
CREATE TABLE chat_history (
    history_id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL,
    query_text TEXT NOT NULL,
    response_text TEXT NOT NULL,
    timestamp TEXT NOT NULL,        -- ISO 8601 format
    content_ids TEXT,               -- JSON array of content IDs
    citations TEXT,                 -- JSON array of citations
    processing_time_ms INTEGER,     -- Query processing time
    FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id) ON DELETE CASCADE
);

-- Indices for performance
CREATE INDEX idx_chat_history_session ON chat_history(session_id);
CREATE INDEX idx_chat_sessions_time ON chat_sessions(last_message_time);
```

### 3.3 Room Entity Definitions

```kotlin
@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "session_id")
    val sessionId: Long = 0,
    
    @ColumnInfo(name = "start_time")
    val startTime: String,
    
    @ColumnInfo(name = "last_message_time")
    val lastMessageTime: String? = null,
    
    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0,
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)

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
    indices = [Index(value = ["session_id"])]
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
    val timestamp: String,
    
    @ColumnInfo(name = "content_ids")
    val contentIds: String? = null,  // JSON array
    
    @ColumnInfo(name = "citations")
    val citations: String? = null,   // JSON array
    
    @ColumnInfo(name = "processing_time_ms")
    val processingTimeMs: Long? = null
)
```

---

## 4. Technical Implementation

### 4.1 Architecture Components

#### 4.1.1 ViewModel Layer
**Requirement ID**: TECH-001

```kotlin
class ChatViewModel(
    private val appRepository: AppRepository,
    private val ragRepository: RagRepository,
    private val llmService: LLMService
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // Current session
    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()
    
    // Messages for current session
    private val _messages = MutableStateFlow<List<MessageUI>>(emptyList())
    val messages: StateFlow<List<MessageUI>> = _messages.asStateFlow()
    
    // Session history
    private val _sessions = MutableStateFlow<List<SessionPreview>>(emptyList())
    val sessions: StateFlow<List<SessionPreview>> = _sessions.asStateFlow()
    
    fun sendQuery(query: String) {
        viewModelScope.launch {
            // 1. Validate query
            if (query.isBlank()) return@launch
            
            // 2. Add user message to UI
            addUserMessage(query)
            
            // 3. Determine context needs
            val needsContext = shouldIncludeContext(query)
            
            // 4. Build prompt
            val prompt = if (needsContext) {
                buildContextualPrompt(query)
            } else {
                buildStandalonePrompt(query)
            }
            
            // 5. Query STG database
            val ragContext = ragRepository.buildRagContext(query, maxContent = 5)
            
            // 6. Generate response
            val response = llmService.generateResponse(prompt, ragContext)
            
            // 7. Save to database
            saveMessageToHistory(query, response)
            
            // 8. Update UI
            addBotMessage(response)
        }
    }
}
```

#### 4.1.2 Repository Pattern
**Requirement ID**: TECH-002

```kotlin
@Singleton
class AppRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    
    suspend fun createSession(): ChatSession = withContext(ioDispatcher) {
        val session = ChatSession(
            startTime = Instant.now().toString()
        )
        val sessionId = chatDao.insertSession(session)
        session.copy(sessionId = sessionId)
    }
    
    suspend fun getRecentMessages(
        sessionId: Long,
        limit: Int = 2
    ): List<ChatMessage> = withContext(ioDispatcher) {
        chatDao.getRecentMessages(sessionId, limit)
    }
    
    suspend fun saveMessage(message: ChatMessage) = withContext(ioDispatcher) {
        chatDao.insertMessage(message)
        chatDao.incrementMessageCount(message.sessionId)
    }
    
    fun observeSessions(): Flow<List<ChatSession>> = 
        chatDao.getAllSessions()
            .flowOn(ioDispatcher)
}
```

### 4.2 UI Components

#### 4.2.1 Main Chat Screen
**Requirement ID**: TECH-003

```kotlin
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawerContent(
                sessions = uiState.sessions,
                onNewChat = viewModel::startNewSession,
                onSelectSession = viewModel::loadSession,
                onDeleteSession = viewModel::deleteSession
            )
        }
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onClearClick = viewModel::clearCurrentChat
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Messages list
                MessagesList(
                    messages = messages,
                    modifier = Modifier.weight(1f)
                )
                
                // Input area
                ChatInputArea(
                    query = uiState.currentQuery,
                    onQueryChange = viewModel::updateQuery,
                    onSend = viewModel::sendQuery,
                    isEnabled = !uiState.isProcessing
                )
            }
        }
    }
}
```

#### 4.2.2 Message Components
**Requirement ID**: TECH-004

```kotlin
@Composable
fun MessageCard(
    message: MessageUI,
    modifier: Modifier = Modifier
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(if (message.isUser) 0.8f else 0.85f),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Message text
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                // Citations if present
                message.citations?.let { citations ->
                    Spacer(modifier = Modifier.height(8.dp))
                    CitationChips(citations = citations)
                }
                
                // Timestamp
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CitationChips(
    citations: List<Citation>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        citations.forEach { citation ->
            AssistChip(
                onClick = { /* Handle citation click */ },
                label = {
                    Text(
                        text = "Page ${citation.pageNumber}",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.height(24.dp)
            )
        }
    }
}
```

### 4.3 Performance Requirements

#### 4.3.1 Response Times
**Requirement ID**: PERF-001

- **Query Submission**: < 100ms to show in UI
- **STG Database Query**: < 500ms for semantic search
- **Response Generation**: < 2s (excluding LLM inference)
- **UI Updates**: 60 FPS maintained
- **Session Loading**: < 1s for 100 messages

#### 4.3.2 Memory Management
**Requirement ID**: PERF-002

- **Message Limit**: Display max 100 messages in memory
- **Lazy Loading**: Use LazyColumn for efficient scrolling
- **Image Caching**: Cache user avatars if implemented
- **Database Cursors**: Close promptly after use
- **ViewModel Scope**: Clear on configuration changes

### 4.4 Accessibility Standards

#### 4.4.1 WCAG 2.1 Compliance
**Requirement ID**: ACCESS-001

- **Color Contrast**: Minimum 4.5:1 for normal text, 3:1 for large text
- **Touch Targets**: Minimum 48dp x 48dp
- **Screen Reader Support**: All interactive elements with contentDescription
- **Keyboard Navigation**: Full support for external keyboards
- **Font Scaling**: Support up to 200% system font size

#### 4.4.2 TalkBack Support
**Requirement ID**: ACCESS-002

```kotlin
// Example accessible message
Card(
    modifier = Modifier.semantics {
        contentDescription = if (message.isUser) {
            "Your question: ${message.text}, sent at ${message.timestamp}"
        } else {
            "Response: ${message.text}, received at ${message.timestamp}"
        }
    }
)
```

---

## 5. Testing Requirements

### 5.1 Unit Tests

#### 5.1.1 ViewModel Tests
- Query validation logic
- Context detection algorithm
- Session management operations
- Message formatting

#### 5.1.2 Repository Tests
- Database operations
- Session lifecycle
- Message persistence
- Query performance

### 5.2 UI Tests

#### 5.2.1 Compose Testing
```kotlin
@Test
fun chatScreen_displaysMessages() {
    composeTestRule.setContent {
        ChatScreen(viewModel = testViewModel)
    }
    
    // Verify message display
    composeTestRule
        .onNodeWithText("Test message")
        .assertIsDisplayed()
    
    // Test input
    composeTestRule
        .onNodeWithContentDescription("Query input")
        .performTextInput("Test query")
    
    // Test send
    composeTestRule
        .onNodeWithContentDescription("Send query")
        .performClick()
}
```

### 5.3 Integration Tests
- End-to-end query flow
- Database integration
- Session persistence
- Error recovery

---

## 6. Success Metrics

### 6.1 Performance Metrics
- **Query Response Time**: 90% < 2 seconds
- **App Launch Time**: < 1.5 seconds cold start
- **Session Load Time**: < 1 second for 100 messages
- **Frame Rate**: Maintain 60 FPS during scrolling
- **Memory Usage**: < 150MB for typical session

### 6.2 User Experience Metrics
- **Query Success Rate**: > 95% queries get relevant responses
- **Session Engagement**: Average 5+ messages per session
- **Feature Adoption**: 80% users utilize session history
- **Error Rate**: < 1% of queries result in errors
- **Accessibility Score**: 100% WCAG 2.1 AA compliance

### 6.3 Technical Metrics
- **Code Coverage**: > 80% for critical paths
- **Crash-Free Rate**: > 99.9%
- **Database Query Performance**: < 100ms p95
- **UI Test Pass Rate**: 100% for critical flows

---

## 7. Implementation Timeline

### 7.1 Development Phases

#### Phase 1: Foundation (Week 1)
- [ ] Set up Jetpack Compose structure
- [ ] Implement basic chat screen layout
- [ ] Create message components
- [ ] Set up ViewModels and state management

#### Phase 2: Database Integration (Week 2)
- [ ] Implement Room entities and DAOs
- [ ] Create app database
- [ ] Integrate with existing stg_rag.db
- [ ] Implement session management

#### Phase 3: Core Chat Features (Week 3)
- [ ] Query processing pipeline
- [ ] Context detection logic
- [ ] Response formatting with citations
- [ ] Loading states and animations

#### Phase 4: Session Management (Week 4)
- [ ] Navigation drawer implementation
- [ ] Session history display
- [ ] Session CRUD operations
- [ ] Delete confirmation dialogs

#### Phase 5: Polish & Optimization (Week 5)
- [ ] Error handling and recovery
- [ ] Performance optimization
- [ ] Accessibility improvements
- [ ] UI polish and animations

### 7.2 Milestones
- **Week 1**: Basic chat UI functional
- **Week 2**: Database integration complete
- **Week 3**: Query-response cycle working
- **Week 4**: Full session management
- **Week 5**: Production-ready UI

---

## 8. Dependencies

### 8.1 Required Libraries
```gradle
dependencies {
    // Jetpack Compose
    implementation "androidx.compose.ui:ui:1.5.4"
    implementation "androidx.compose.material3:material3:1.1.2"
    implementation "androidx.compose.ui:ui-tooling-preview:1.5.4"
    implementation "androidx.navigation:navigation-compose:2.7.5"
    
    // Lifecycle & ViewModel
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2"
    implementation "androidx.lifecycle:lifecycle-runtime-compose:2.6.2"
    
    // Room Database
    implementation "androidx.room:room-runtime:2.6.0"
    implementation "androidx.room:room-ktx:2.6.0"
    kapt "androidx.room:room-compiler:2.6.0"
    
    // Dependency Injection
    implementation "com.google.dagger:hilt-android:2.48"
    implementation "androidx.hilt:hilt-navigation-compose:1.1.0"
    kapt "com.google.dagger:hilt-compiler:2.48"
    
    // Coroutines
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
    
    // Testing
    androidTestImplementation "androidx.compose.ui:ui-test-junit4:1.5.4"
    debugImplementation "androidx.compose.ui:ui-test-manifest:1.5.4"
}
```

---

## 9. Risk Mitigation

### 9.1 Technical Risks

#### Risk: Slow LLM Response Times
**Mitigation**:
- Implement streaming responses
- Show partial results as available
- Cache common query patterns
- Provide quick action suggestions

#### Risk: Large Session History
**Mitigation**:
- Implement pagination for session list
- Auto-archive sessions > 30 days
- Compress old session data
- Warn at 80% storage capacity

### 9.2 User Experience Risks

#### Risk: Context Misdetection
**Mitigation**:
- Allow manual "new topic" indication
- Provide context reset option
- Show context indicator in UI
- Learn from user corrections

#### Risk: Poor Accessibility
**Mitigation**:
- Regular accessibility testing
- User testing with screen readers
- Compliance validation tools
- Feedback from accessibility users

---

## 10. Appendix

### 10.1 Glossary
- **STG**: Standard Treatment Guidelines
- **RAG**: Retrieval-Augmented Generation
- **LLM**: Large Language Model
- **DAO**: Data Access Object
- **MVVM**: Model-View-ViewModel

### 10.2 References
- [Material Design 3 Guidelines](https://m3.material.io/)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [WCAG 2.1 Standards](https://www.w3.org/WAI/WCAG21/quickref/)
- [Android Accessibility Guidelines](https://developer.android.com/guide/topics/ui/accessibility)

### 10.3 Change Log
- **v1.0** (2025-08-28): Initial comprehensive UI PRD created

---

*This document serves as the authoritative source for UI development of the Ghana STG Clinical Chatbot. All implementation decisions should reference this specification.*