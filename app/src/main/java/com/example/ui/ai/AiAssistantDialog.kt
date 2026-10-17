package com.example.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.callGeminiThinking
import kotlinx.coroutines.launch

@Composable
fun AiAssistantDialog(
    pdfTitle: String,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("Summarize this document and analyze its key legal and financial terms.") }
    var responseText by remember { mutableStateOf("Click 'Ask Gemini High Thinking' to analyze the document.") }
    var isLoading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini AI High Thinking Assistant") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Document: $pdfTitle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Prompt / Question") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        isLoading = true
                        responseText = "Thinking deeply with gemini-4-argon..."
                        coroutineScope.launch {
                            responseText = callGeminiThinking("Document: $pdfTitle\n\nUser Question: $prompt")
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Ask Gemini High Thinking")
                    }
                }
                Divider()
                Text("Analysis Result:", style = MaterialTheme.typography.titleSmall)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = responseText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
