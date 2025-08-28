package co.kobby.clinicalaide.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kobby.clinicalaide.ui.chat.components.*
import co.kobby.clinicalaide.ui.chat.drawer.ModalDrawerContent
import kotlinx.coroutines.launch

/**
 * Main chat screen composable.
 * Displays the chat interface with message history and input area.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    
    // Handle drawer state
    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerContent(
                sessions = uiState.sessions,
                currentSessionId = uiState.currentSession?.sessionId,
                onNewChat = {
                    viewModel.startNewSession()
                    scope.launch { drawerState.close() }
                },
                onSelectSession = { sessionId ->
                    viewModel.loadSession(sessionId)
                    scope.launch { drawerState.close() }
                },
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
                    onClearClick = viewModel::showClearConfirmation
                )
            },
            modifier = modifier
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Messages list
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (uiState.messages.isEmpty() && !uiState.isProcessing) {
                        // Welcome message for empty chat
                        WelcomeMessage(
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    } else {
                        // Message list
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = uiState.messages.reversed(),
                                key = { it.id }
                            ) { message ->
                                MessageCard(
                                    message = message,
                                    onCitationClick = { citation ->
                                        // Handle citation click - could open detail view
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Quick actions (optional)
                if (uiState.messages.isEmpty()) {
                    QuickActions(
                        onActionClick = viewModel::selectQuickAction,
                        isEnabled = !uiState.isProcessing,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
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
    
    // Error handling
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error snackbar
        }
    }
    
    // Clear chat confirmation dialog
    if (uiState.showClearConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::hideClearConfirmation,
            title = { Text("Clear Chat?") },
            text = { 
                Text("This will clear the current conversation. The session will be saved in history. Do you want to continue?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCurrentChat()
                        viewModel.hideClearConfirmation()
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideClearConfirmation) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Top app bar for the chat screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    onMenuClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { 
            Text(
                text = "Clinical AI Assistant",
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open chat history"
                )
            }
        },
        actions = {
            IconButton(onClick = onClearClick) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear chat"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
    )
}