package co.kobby.clinicalaide.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * Input area for typing and sending queries.
 */
@Composable
fun ChatInputArea(
    query: String,
    onQueryChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text("Ask about treatment guidelines...")
                },
                enabled = isEnabled,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (query.isNotBlank()) ImeAction.Send else ImeAction.Default
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (query.isNotBlank() && isEnabled) {
                            onSend()
                            keyboardController?.hide()
                        }
                    }
                ),
                shape = MaterialTheme.shapes.medium
            )
            
            FilledIconButton(
                onClick = {
                    onSend()
                    keyboardController?.hide()
                },
                enabled = query.isNotBlank() && isEnabled,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send query"
                )
            }
        }
    }
    
    // Request focus when component is first displayed
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Quick action chips for common queries.
 */
@Composable
fun QuickActions(
    onActionClick: (String) -> Unit,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val quickQueries = listOf(
        "Malaria treatment",
        "Pediatric dosing",
        "Emergency care",
        "Hypertension"
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        quickQueries.forEach { query ->
            SuggestionChip(
                onClick = { onActionClick(query) },
                label = { Text(query) },
                enabled = isEnabled,
                modifier = Modifier.height(32.dp)
            )
        }
    }
}