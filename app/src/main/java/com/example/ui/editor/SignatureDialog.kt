package com.example.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.SignatureItem
import com.example.data.PdfRepository
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

data class LineSegment(val start: Offset, val end: Offset)

@Composable
fun SignatureDialog(
    repository: PdfRepository,
    onDismiss: () -> Unit,
    onSignatureSelected: (SignatureItem) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var signatureName by remember { mutableStateOf("My Signature") }
    val segments = remember { mutableStateListOf<LineSegment>() }
    var lastPoint by remember { mutableStateOf<Offset?>(null) }
    val signatures by repository.observeSignatures().collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Electronic Signature Capture") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = signatureName,
                    onValueChange = { signatureName = it },
                    label = { Text("Signature Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Draw your signature below (Saved as Transparent PNG):", style = MaterialTheme.typography.bodyMedium)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    lastPoint = offset
                                },
                                onDrag = { change, dragAmount ->
                                    val start = lastPoint ?: change.position
                                    val end = start + dragAmount
                                    segments.add(LineSegment(start, end))
                                    lastPoint = end
                                },
                                onDragEnd = {
                                    lastPoint = null
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (seg in segments) {
                            drawLine(
                                color = Color.Black,
                                start = seg.start,
                                end = seg.end,
                                strokeWidth = 4f
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { segments.clear() }) {
                        Text("Clear Canvas")
                    }
                    Button(onClick = {
                        if (segments.isNotEmpty()) {
                            val bitmap = Bitmap.createBitmap(800, 300, Bitmap.Config.ARGB_8888)
                            val canvas = AndroidCanvas(bitmap)
                            val paint = Paint().apply {
                                color = AndroidColor.BLACK
                                strokeWidth = 6f
                                style = Paint.Style.STROKE
                                isAntiAlias = true
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                            }
                            for (seg in segments) {
                                canvas.drawLine(seg.start.x, seg.start.y, seg.end.x, seg.end.y, paint)
                            }
                            val outputStream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                            val base64Png = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                            coroutineScope.launch {
                                repository.saveSignature(signatureName, base64Png)
                                segments.clear()
                            }
                        }
                    }) {
                        Text("Save as Transparent PNG")
                    }
                }

                Divider()
                Text("Saved Signatures:", style = MaterialTheme.typography.titleSmall)
                if (signatures.isEmpty()) {
                    Text("No saved signatures yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        for (sig in signatures) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(sig.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Transparent PNG Ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                Row {
                                    TextButton(onClick = { onSignatureSelected(sig) }) {
                                        Text("Place on PDF")
                                    }
                                    TextButton(onClick = {
                                        coroutineScope.launch { repository.deleteSignature(sig.id) }
                                    }) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
