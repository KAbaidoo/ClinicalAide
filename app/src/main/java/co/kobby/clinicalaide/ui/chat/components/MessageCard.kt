package co.kobby.clinicalaide.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.kobby.clinicalaide.ui.chat.MessageUI

/**
 * Display a single message in the chat.
 */
@Composable
fun MessageCard(
    message: MessageUI,
    onCitationClick: (co.kobby.clinicalaide.ui.chat.Citation) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics {
                contentDescription = if (message.isUser) {
                    "Your question: ${message.text}, sent at ${message.timestamp}"
                } else {
                    "Response: ${message.text}, received at ${message.timestamp}"
                }
            },
        horizontalAlignment = alignment
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(if (message.isUser) 0.8f else 0.85f),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            // Avatar for bot messages
            if (!message.isUser) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "AI Assistant",
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = backgroundColor,
                    contentColor = contentColor
                ),
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (message.isUser) 12.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 12.dp
                ),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Loading state
                    if (message.isLoading) {
                        LoadingDots()
                    } else {
                        // Message text
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        // Citations for bot messages
                        if (!message.isUser && message.citations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            CitationChips(
                                citations = message.citations,
                                onCitationClick = onCitationClick
                            )
                        }
                        
                        // Processing info for bot messages
                        if (!message.isUser && (message.processingTimeMs != null || message.similarityScore != null)) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                message.processingTimeMs?.let { time ->
                                    Text(
                                        text = "${time}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = contentColor.copy(alpha = 0.6f)
                                    )
                                }
                                message.similarityScore?.let { score ->
                                    Text(
                                        text = "%.1f%% match".format(score * 100),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = contentColor.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Timestamp
                    Text(
                        text = message.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.6f),
                        textAlign = if (message.isUser) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Avatar for user messages
            if (message.isUser) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "You",
                    modifier = Modifier
                        .size(24.dp)
                        .padding(start = 8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Welcome message for new sessions.
 */
@Composable
fun WelcomeMessage(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Welcome to Clinical AI Assistant",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Ask me about treatment guidelines, medications, or clinical protocols from the Ghana STG.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            Text(
                text = "Example queries:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "• Treatment for severe malaria in children",
                    "• Hypertension management guidelines",
                    "• Pediatric diarrhea with dehydration",
                    "• First-line antibiotics for pneumonia"
                ).forEach { example ->
                    Text(
                        text = example,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}