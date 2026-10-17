package com.example.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.app.Activity
import android.net.Uri
import android.util.Base64
import com.example.ui.ads.InterstitialAdsHelper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AnnotationItem
import com.example.data.OcrService
import com.example.data.PdfExporter
import com.example.data.PdfRendererService
import com.example.data.PdfRepository
import com.example.data.SignatureItem
import com.example.ui.ai.AiAssistantDialog
import com.example.ui.components.ZoomableBox
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke

enum class EditMode { VIEW, HIGHLIGHT, DRAW, REDACT, NOTE, SIGN, TEXT, ERASER, PLACE_SIGNATURE }

fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
    return try {
        val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    pdfId: String,
    pdfTitle: String,
    pdfUri: String,
    repository: PdfRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentPage by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(1) }
    var editMode by remember { mutableStateOf(EditMode.VIEW) }
    
    val annotations by repository.observeAnnotations(pdfId).collectAsState(initial = emptyList())
    val signatures by repository.observeSignatures().collectAsState(initial = emptyList())
    
    var showAiDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showOcrSidePanel by remember { mutableStateOf(false) }
    var ocrText by remember { mutableStateOf("") }
    var isOcrLoading by remember { mutableStateOf(false) }
    
    var dragStartOffset by remember { mutableStateOf<Offset?>(null) }
    var dragCurrentOffset by remember { mutableStateOf<Offset?>(null) }

    var showStickyNoteDialog by remember { mutableStateOf(false) }
    var stickyNotePos by remember { mutableStateOf(Offset.Zero) }
    var stickyNoteText by remember { mutableStateOf("") }

    var showTextDialog by remember { mutableStateOf(false) }
    var textPos by remember { mutableStateOf(Offset.Zero) }
    var annotationText by remember { mutableStateOf("") }

    var currentColor by remember { mutableStateOf(Color.Blue) }
    var currentStrokeWidth by remember { mutableStateOf(5f) }
    val currentDrawingPoints = remember { mutableStateListOf<Offset>() }
    var pendingSignatureId by remember { mutableStateOf<String?>(null) }

    val pdfRendererService = remember { PdfRendererService(context) }
    val pageBitmaps = remember { mutableStateListOf<Bitmap?>() }
    var isDocumentLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(pdfUri) {
        if (pdfUri.isNotEmpty() && pdfUri != "sample_uri") {
            try {
                val count = pdfRendererService.openUri(Uri.parse(pdfUri))
                totalPages = count
                pageBitmaps.clear()
                repeat(count) { pageBitmaps.add(null) }
                
                // Load first page immediately
                if (count > 0) {
                    val bmp = pdfRendererService.renderPage(0)
                    pageBitmaps[0] = bmp
                }
                isDocumentLoaded = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Fallback for sample or missing URI
            totalPages = 3
            repeat(3) { i ->
                val bmp = Bitmap.createBitmap(800, 1100, Bitmap.Config.ARGB_8888)
                val canvas = AndroidCanvas(bmp)
                canvas.drawColor(AndroidColor.WHITE)
                val paint = Paint().apply {
                    color = AndroidColor.DKGRAY
                    textSize = 32f
                    isAntiAlias = true
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(pdfTitle, 50f, 80f, paint)
                paint.textSize = 24f
                canvas.drawText("Page ${i + 1} of 3 (Sample Preview)", 50f, 130f, paint)
                pageBitmaps.add(bmp)
            }
            isDocumentLoaded = true
        }
    }

    // Lazy load pages as needed
    LaunchedEffect(currentPage) {
        if (isDocumentLoaded && currentPage < pageBitmaps.size && pageBitmaps[currentPage] == null) {
            val bmp = pdfRendererService.renderPage(currentPage)
            if (bmp != null) {
                pageBitmaps[currentPage] = bmp
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pdfRendererService.close()
        }
    }

    LaunchedEffect(Unit) {
        InterstitialAdsHelper.loadAd(context)
    }

    val currentPageBitmap = if (currentPage < pageBitmaps.size) pageBitmaps[currentPage] else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(pdfTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Editing Page ${currentPage + 1} of $totalPages", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val uri = PdfExporter.exportPdf(
                                context = context,
                                pdfTitle = pdfTitle,
                                pdfUri = pdfUri,
                                annotations = annotations,
                                signatures = signatures
                            )
                            if (uri != null) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                
                                val activity = context as? Activity
                                if (activity != null) {
                                    InterstitialAdsHelper.showAd(activity) {
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share Edited PDF"))
                                    }
                                } else {
                                    context.startActivity(android.content.Intent.createChooser(intent, "Share Edited PDF"))
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        if (currentPageBitmap != null) {
                            isOcrLoading = true
                            coroutineScope.launch {
                                ocrText = OcrService.extractText(currentPageBitmap)
                                isOcrLoading = false
                                showOcrSidePanel = true
                            }
                        }
                    }, enabled = !isOcrLoading && currentPageBitmap != null) {
                        if (isOcrLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.TextSnippet, contentDescription = "Extract Text", tint = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = { showAiDialog = true }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assist", tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                    // Tool Options (Color & Stroke)
                    if (editMode != EditMode.VIEW && editMode != EditMode.ERASER && editMode != EditMode.REDACT) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Color:", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(16.dp))
                                val colors = listOf(Color.Red, Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFFEB3B), Color.Black, Color(0xFF9C27B0), Color(0xFFE91E63))
                                colors.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (currentColor == color) 2.dp else 0.dp,
                                                color = if (currentColor == color) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { currentColor = color }
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                            }
                            if (editMode == EditMode.DRAW || editMode == EditMode.HIGHLIGHT) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                                    Text("Size:", style = MaterialTheme.typography.labelMedium)
                                    Slider(
                                        value = currentStrokeWidth,
                                        onValueChange = { currentStrokeWidth = it },
                                        valueRange = 1f..30f,
                                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                        Divider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    // Page Thumbnails
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        itemsIndexed(pageBitmaps) { index, bitmap ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .width(50.dp)
                                        .height(70.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (currentPage == index) MaterialTheme.colorScheme.primaryContainer else Color.White)
                                        .border(
                                            width = if (currentPage == index) 2.dp else 1.dp,
                                            color = if (currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { currentPage = index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Page $index",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.dp)
                                    }
                                }
                                Text(
                                    "${index + 1}", 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (currentPage == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Floating Annotation Toolbar
            if (editMode != EditMode.PLACE_SIGNATURE) {
                Surface(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.CenterStart)
                        .width(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnnotationToolIcon(
                            icon = Icons.Default.Visibility,
                            isSelected = editMode == EditMode.VIEW,
                            onClick = { editMode = EditMode.VIEW },
                            tooltip = "View Mode"
                        )
                        Divider(modifier = Modifier.padding(horizontal = 8.dp))
                        AnnotationToolIcon(
                            icon = Icons.Default.Brush,
                            isSelected = editMode == EditMode.DRAW,
                            onClick = { editMode = EditMode.DRAW },
                            color = currentColor,
                            tooltip = "Pen"
                        )
                        AnnotationToolIcon(
                            icon = Icons.Default.Highlight,
                            isSelected = editMode == EditMode.HIGHLIGHT,
                            onClick = { editMode = EditMode.HIGHLIGHT },
                            color = Color(0xFFFFD600),
                            tooltip = "Highlighter"
                        )
                        AnnotationToolIcon(
                            icon = Icons.Default.StickyNote2,
                            isSelected = editMode == EditMode.NOTE,
                            onClick = { editMode = EditMode.NOTE },
                            color = Color(0xFFFBC02D),
                            tooltip = "Sticky Note"
                        )
                        AnnotationToolIcon(
                            icon = Icons.Default.TextFields,
                            isSelected = editMode == EditMode.TEXT,
                            onClick = { editMode = EditMode.TEXT },
                            color = Color(0xFF9C27B0),
                            tooltip = "Text"
                        )
                        AnnotationToolIcon(
                            icon = Icons.Default.Block,
                            isSelected = editMode == EditMode.REDACT,
                            onClick = { editMode = EditMode.REDACT },
                            color = Color.Black,
                            tooltip = "Redact"
                        )
                        AnnotationToolIcon(
                            icon = Icons.Default.HistoryEdu,
                            isSelected = editMode == EditMode.SIGN,
                            onClick = { showSignatureDialog = true },
                            color = MaterialTheme.colorScheme.tertiary,
                            tooltip = "Sign"
                        )
                        Divider(modifier = Modifier.padding(horizontal = 8.dp))
                        AnnotationToolIcon(
                            icon = Icons.Default.AutoFixNormal,
                            isSelected = editMode == EditMode.ERASER,
                            onClick = { editMode = EditMode.ERASER },
                            tooltip = "Eraser"
                        )
                    }
                }
            }

            if (editMode == EditMode.PLACE_SIGNATURE) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .pointerInput(Unit) {
                            detectDragGestures { _, _ -> /* Block zoom/scroll during placement */ }
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        modifier = Modifier.padding(top = 32.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Tap on the PDF to place signature",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(16.dp))
                            TextButton(onClick = { 
                                editMode = EditMode.VIEW
                                pendingSignatureId = null
                            }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
            ZoomableBox {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(8.5f / 11f) // Standard US Letter aspect ratio
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .pointerInput(editMode, currentPage, currentColor, currentStrokeWidth) {
                            if (editMode != EditMode.VIEW) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        when (editMode) {
                                            EditMode.NOTE -> {
                                                stickyNotePos = offset
                                                showStickyNoteDialog = true
                                            }
                                            EditMode.TEXT -> {
                                                textPos = offset
                                                showTextDialog = true
                                            }
                                            EditMode.DRAW -> {
                                                currentDrawingPoints.clear()
                                                currentDrawingPoints.add(offset)
                                            }
                                            EditMode.PLACE_SIGNATURE -> {
                                                val sigId = pendingSignatureId
                                                if (sigId != null) {
                                                    coroutineScope.launch {
                                                        repository.addAnnotation(
                                                            pdfId,
                                                            AnnotationItem(
                                                                pdfId = pdfId,
                                                                pageIndex = currentPage,
                                                                type = "SIGNATURE",
                                                                textNote = sigId,
                                                                x = offset.x,
                                                                y = offset.y,
                                                                width = 120f,
                                                                height = 50f
                                                            )
                                                        )
                                                        editMode = EditMode.VIEW
                                                        pendingSignatureId = null
                                                    }
                                                }
                                            }
                                            else -> {
                                                dragStartOffset = offset
                                                dragCurrentOffset = offset
                                            }
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (editMode == EditMode.DRAW) {
                                            currentDrawingPoints.add(change.position)
                                        } else if (editMode != EditMode.NOTE && editMode != EditMode.TEXT) {
                                            dragCurrentOffset = (dragCurrentOffset ?: dragStartOffset ?: Offset.Zero) + dragAmount
                                        }
                                    },
                                    onDragEnd = {
                                        if (editMode == EditMode.DRAW) {
                                            val points = currentDrawingPoints.flatMap { listOf(it.x, it.y) }
                                            if (points.isNotEmpty()) {
                                                coroutineScope.launch {
                                                    repository.addAnnotation(
                                                        pdfId,
                                                        AnnotationItem(
                                                            pdfId = pdfId,
                                                            pageIndex = currentPage,
                                                            type = "DRAW",
                                                            colorHex = String.format("#%06X", (0xFFFFFF and currentColor.value.toInt())),
                                                            points = points,
                                                            strokeWidth = currentStrokeWidth
                                                        )
                                                    )
                                                }
                                            }
                                            currentDrawingPoints.clear()
                                        } else if (editMode != EditMode.NOTE && editMode != EditMode.TEXT && dragStartOffset != null && dragCurrentOffset != null) {
                                            val start = dragStartOffset!!
                                            val end = dragCurrentOffset!!
                                            
                                            val x = minOf(start.x, end.x)
                                            val y = minOf(start.y, end.y)
                                            val width = Math.abs(start.x - end.x)
                                            val height = Math.abs(start.y - end.y)
                                            
                                            coroutineScope.launch {
                                                val typeStr = when (editMode) {
                                                    EditMode.HIGHLIGHT -> "HIGHLIGHT"
                                                    EditMode.REDACT -> "REDACT"
                                                    else -> "SHAPE"
                                                }
                                                
                                                if (width > 5f || height > 5f) {
                                                    repository.addAnnotation(
                                                        pdfId,
                                                        AnnotationItem(
                                                            pdfId = pdfId,
                                                            pageIndex = currentPage,
                                                            type = typeStr,
                                                            x = x,
                                                            y = y,
                                                            width = width,
                                                            height = height,
                                                            colorHex = if (typeStr == "HIGHLIGHT") "#40FFFF00" else "#000000"
                                                        )
                                                    )
                                                }
                                            }
                                            dragStartOffset = null
                                            dragCurrentOffset = null
                                        }
                                    },
                                    onDragCancel = {
                                        dragStartOffset = null
                                        dragCurrentOffset = null
                                        currentDrawingPoints.clear()
                                    }
                                )
                            }
                        }
                ) {
                    if (currentPageBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = currentPageBitmap.asImageBitmap(),
                            contentDescription = "PDF Page",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Render saved annotations
                        for (ann in annotations.filter { it.pageIndex == currentPage }) {
                            when (ann.type) {
                                "HIGHLIGHT" -> {
                                    drawRect(
                                        color = Color(android.graphics.Color.parseColor(ann.colorHex)).copy(alpha = 0.4f),
                                        topLeft = Offset(ann.x, ann.y),
                                        size = Size(ann.width.coerceAtLeast(10f), ann.height.coerceAtLeast(10f))
                                    )
                                }
                                "REDACT" -> {
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = Offset(ann.x, ann.y),
                                        size = Size(ann.width.coerceAtLeast(10f), ann.height.coerceAtLeast(10f))
                                    )
                                }
                                "DRAW" -> {
                                    if (ann.points.size >= 4) {
                                        val path = androidx.compose.ui.graphics.Path()
                                        path.moveTo(ann.points[0], ann.points[1])
                                        for (i in 2 until ann.points.size step 2) {
                                            path.lineTo(ann.points[i], ann.points[i + 1])
                                        }
                                        drawPath(
                                            path = path,
                                            color = Color(android.graphics.Color.parseColor(ann.colorHex)),
                                            style = Stroke(width = ann.strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                        )
                                    }
                                }
                                "NOTE" -> {
                                    drawRect(
                                        color = Color(0xFFFFF9C4),
                                        topLeft = Offset(ann.x, ann.y),
                                        size = Size(40f, 40f)
                                    )
                                    drawRect(
                                        color = Color(0xFFFBC02D),
                                        topLeft = Offset(ann.x, ann.y),
                                        size = Size(40f, 40f),
                                        style = Stroke(width = 1f)
                                    )
                                }
                                "TEXT" -> {
                                    // Text is rendered via Box/Text for better control
                                }
                            }
                        }
                        
                        // Render "live" drag rectangle
                        if (dragStartOffset != null && dragCurrentOffset != null && (editMode == EditMode.HIGHLIGHT || editMode == EditMode.REDACT)) {
                            val start = dragStartOffset!!
                            val end = dragCurrentOffset!!
                            val x = minOf(start.x, end.x)
                            val y = minOf(start.y, end.y)
                            val w = Math.abs(start.x - end.x)
                            val h = Math.abs(start.y - end.y)
                            
                            drawRect(
                                color = if (editMode == EditMode.REDACT) Color.Black else Color.Yellow.copy(alpha = 0.4f),
                                topLeft = Offset(x, y),
                                size = Size(w, h)
                            )
                        }

                        // Render "live" drawing path
                        if (currentDrawingPoints.size >= 2) {
                            val path = androidx.compose.ui.graphics.Path()
                            path.moveTo(currentDrawingPoints[0].x, currentDrawingPoints[0].y)
                            currentDrawingPoints.forEach { path.lineTo(it.x, it.y) }
                            drawPath(
                                path = path,
                                color = currentColor,
                                style = Stroke(width = currentStrokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                        }
                    }

                    // Render transparent PNG signatures
                    for (ann in annotations.filter { it.pageIndex == currentPage && it.type == "SIGNATURE" }) {
                        val matchingSig = signatures.find { it.id == ann.textNote || it.name == ann.textNote }
                        val pngBase64 = matchingSig?.pointsJson ?: ann.textNote
                        val sigBitmap = remember(pngBase64) { decodeBase64ToBitmap(pngBase64) }

                        if (sigBitmap != null) {
                            val density = LocalDensity.current
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = with(density) { ann.x.toDp() },
                                        y = with(density) { ann.y.toDp() }
                                    )
                                    .size(
                                        width = with(density) { ann.width.toDp() },
                                        height = with(density) { ann.height.toDp() }
                                    )
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = sigBitmap.asImageBitmap(),
                                    contentDescription = "Signature",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    
                    // Sticky Note Icons (Interactive)
                    for (ann in annotations.filter { it.pageIndex == currentPage && it.type == "NOTE" }) {
                        var showTooltip by remember { mutableStateOf(false) }
                        val density = LocalDensity.current
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = with(density) { ann.x.toDp() },
                                    y = with(density) { ann.y.toDp() }
                                )
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFBC02D))
                                .clickable { 
                                    if (editMode == EditMode.ERASER) {
                                        coroutineScope.launch { repository.deleteAnnotation(pdfId, ann.id) }
                                    } else {
                                        showTooltip = !showTooltip
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        }
                        if (showTooltip) {
                            Surface(
                                modifier = Modifier.offset(x = (ann.x / 4f).dp + 28.dp, y = (ann.y / 4f).dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shadowElevation = 4.dp,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(ann.textNote, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // Text Annotations (Interactive)
                    for (ann in annotations.filter { it.pageIndex == currentPage && it.type == "TEXT" }) {
                        val density = LocalDensity.current
                        Text(
                            text = ann.textNote,
                            color = Color(android.graphics.Color.parseColor(ann.colorHex)),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .offset(
                                    x = with(density) { ann.x.toDp() },
                                    y = with(density) { ann.y.toDp() }
                                )
                                .clickable {
                                    if (editMode == EditMode.ERASER) {
                                        coroutineScope.launch { repository.deleteAnnotation(pdfId, ann.id) }
                                    }
                                }
                        )
                    }

                    // Handle generic eraser clicks for other annotations
                    if (editMode == EditMode.ERASER) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        // Simple eraser: delete any annotation near the touch
                                        val touch = change.position
                                        annotations.filter { it.pageIndex == currentPage }.forEach { ann ->
                                            val dist = Math.sqrt(Math.pow((ann.x - touch.x).toDouble(), 2.0) + Math.pow((ann.y - touch.y).toDouble(), 2.0))
                                            if (dist < 40.0) {
                                                coroutineScope.launch { repository.deleteAnnotation(pdfId, ann.id) }
                                            }
                                        }
                                    }
                                }
                        )
                    }
            }
        }
    }

        // OCR Side Panel
        AnimatedVisibility(
            visible = showOcrSidePanel,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(320.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text("OCR Results", style = MaterialTheme.typography.titleMedium) },
                        actions = {
                            IconButton(onClick = { showOcrSidePanel = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Panel")
                            }
                        }
                    )
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Extracted Text from Page ${currentPage + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = ocrText,
                            onValueChange = { ocrText = it },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            placeholder = { Text("No text extracted...") }
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        OutlinedButton(
                            onClick = { 
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(ocrText))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy")
                        }
                        
                        Button(
                            onClick = {
                                // Potentially add as a note or just keep in panel
                                coroutineScope.launch {
                                    repository.addAnnotation(
                                        pdfId,
                                        AnnotationItem(
                                            pdfId = pdfId,
                                            pageIndex = currentPage,
                                            type = "NOTE",
                                            textNote = ocrText,
                                            x = 50f,
                                            y = 50f
                                        )
                                    )
                                    showOcrSidePanel = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Note")
                        }
                    }
                }
            }
        }
        }
    }

    // Dialogs
    if (showAiDialog) {
        AiAssistantDialog(pdfTitle = pdfTitle, onDismiss = { showAiDialog = false })
    }

    if (showSignatureDialog) {
        SignatureDialog(
            repository = repository,
            onDismiss = { showSignatureDialog = false },
            onSignatureSelected = { sig ->
                showSignatureDialog = false
                pendingSignatureId = sig.id
                editMode = EditMode.PLACE_SIGNATURE
            }
        )
    }

    if (showStickyNoteDialog) {
        AlertDialog(
            onDismissRequest = { showStickyNoteDialog = false },
            title = { Text("Insert Sticky Note") },
            text = {
                OutlinedTextField(
                    value = stickyNoteText,
                    onValueChange = { stickyNoteText = it },
                    label = { Text("Your Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        repository.addAnnotation(
                            pdfId,
                            AnnotationItem(
                                pdfId = pdfId,
                                pageIndex = currentPage,
                                type = "NOTE",
                                textNote = stickyNoteText,
                                x = stickyNotePos.x,
                                y = stickyNotePos.y
                            )
                        )
                        stickyNoteText = ""
                        showStickyNoteDialog = false
                    }
                }) { Text("Place") }
            },
            dismissButton = {
                TextButton(onClick = { showStickyNoteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text") },
            text = {
                OutlinedTextField(
                    value = annotationText,
                    onValueChange = { annotationText = it },
                    label = { Text("Text Content") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        repository.addAnnotation(
                            pdfId,
                            AnnotationItem(
                                pdfId = pdfId,
                                pageIndex = currentPage,
                                type = "TEXT",
                                textNote = annotationText,
                                x = textPos.x,
                                y = textPos.y,
                                colorHex = String.format("#%06X", (0xFFFFFF and currentColor.value.toInt()))
                            )
                        )
                        annotationText = ""
                        showTextDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Removed old OCR dialog
}

@Composable
fun AnnotationToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    color: Color? = null,
    tooltip: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                color ?: MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun EditorToolItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
            color = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent,
            border = if (isSelected) BorderStroke(1.dp, color.copy(alpha = 0.5f)) else null
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
