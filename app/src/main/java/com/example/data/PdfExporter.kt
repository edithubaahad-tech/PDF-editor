package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.ui.editor.decodeBase64ToBitmap
import java.io.File
import java.io.FileOutputStream

object PdfExporter {
    suspend fun exportPdf(
        context: Context,
        pdfTitle: String,
        pdfUri: String,
        annotations: List<AnnotationItem>,
        signatures: List<SignatureItem>
    ): Uri? {
        val pdfRendererService = PdfRendererService(context)
        val count = if (pdfUri.isNotEmpty() && pdfUri != "sample_uri") {
            pdfRendererService.openUri(Uri.parse(pdfUri))
        } else {
            0
        }

        val pdfDocument = PdfDocument()
        val paint = Paint()

        if (count > 0) {
            for (index in 0 until count) {
                val bitmap = pdfRendererService.renderPage(index) ?: continue
                renderPageToPdf(pdfDocument, bitmap, index, annotations, signatures, paint)
            }
        } else {
            // Fallback for sample or no document
            val sampleCount = 3
            for (index in 0 until sampleCount) {
                val bitmap = Bitmap.createBitmap(800, 1100, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                paint.color = Color.DKGRAY
                paint.textSize = 30f
                canvas.drawText("Sample Page ${index + 1}", 50f, 100f, paint)
                renderPageToPdf(pdfDocument, bitmap, index, annotations, signatures, paint)
            }
        }

        pdfRendererService.close()

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        
        val file = File(exportDir, "${pdfTitle.replace(" ", "_")}_edited.pdf")
        return try {
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    private fun renderPageToPdf(
        pdfDocument: PdfDocument,
        bitmap: Bitmap,
        index: Int,
        annotations: List<AnnotationItem>,
        signatures: List<SignatureItem>,
        paint: Paint
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Draw base page
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // Draw annotations
        annotations.filter { it.pageIndex == index }.forEach { ann ->
            when (ann.type) {
                "HIGHLIGHT" -> {
                    paint.color = Color.YELLOW
                    paint.alpha = 100
                    canvas.drawRect(ann.x, ann.y, ann.x + ann.width, ann.y + ann.height, paint)
                }
                "REDACT" -> {
                    paint.color = Color.BLACK
                    paint.alpha = 255
                    canvas.drawRect(ann.x, ann.y, ann.x + ann.width, ann.y + ann.height, paint)
                }
                "DRAW" -> {
                    paint.color = Color.parseColor(ann.colorHex)
                    paint.alpha = 255
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = ann.strokeWidth
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    
                    if (ann.points.size >= 4) {
                        val path = android.graphics.Path()
                        path.moveTo(ann.points[0], ann.points[1])
                        for (i in 2 until ann.points.size step 2) {
                            path.lineTo(ann.points[i], ann.points[i + 1])
                        }
                        canvas.drawPath(path, paint)
                    }
                    paint.style = Paint.Style.FILL // Reset style
                }
                "TEXT" -> {
                    paint.color = Color.parseColor(ann.colorHex)
                    paint.alpha = 255
                    paint.textSize = 24f
                    canvas.drawText(ann.textNote, ann.x, ann.y, paint)
                }
                "NOTE" -> {
                    paint.color = Color.parseColor("#FBC02D")
                    paint.alpha = 255
                    canvas.drawRect(ann.x, ann.y, ann.x + 40f, ann.y + 40f, paint)
                }
                "SIGNATURE" -> {
                    val matchingSig = signatures.find { it.id == ann.textNote || it.name == ann.textNote }
                    val pngBase64 = matchingSig?.pointsJson ?: ann.textNote
                    val sigBitmap = decodeBase64ToBitmap(pngBase64)
                    if (sigBitmap != null) {
                        val destRect = RectF(ann.x, ann.y, ann.x + ann.width, ann.y + ann.height)
                        canvas.drawBitmap(sigBitmap, null, destRect, paint)
                    }
                }
            }
        }

        pdfDocument.finishPage(page)
    }

    suspend fun imagesToPdf(context: Context, images: List<Uri>, title: String): Uri? {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        
        images.forEachIndexed { index, uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                        val page = pdfDocument.startPage(pageInfo)
                        page.canvas.drawBitmap(bitmap, 0f, 0f, paint)
                        pdfDocument.finishPage(page)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val file = File(exportDir, "${title.replace(" ", "_")}.pdf")
        
        return try {
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    suspend fun mergePdfs(context: Context, pdfUris: List<Uri>, title: String): Uri? {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val pdfRendererService = PdfRendererService(context)
        var totalPageCounter = 1

        pdfUris.forEach { uri ->
            try {
                val pageCount = pdfRendererService.openUri(uri)
                for (i in 0 until pageCount) {
                    val bitmap = pdfRendererService.renderPage(i) ?: continue
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, totalPageCounter++).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, paint)
                    pdfDocument.finishPage(page)
                }
                pdfRendererService.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val file = File(exportDir, "${title.replace(" ", "_")}_merged.pdf")
        
        return try {
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    suspend fun compressPdf(context: Context, pdfUri: Uri, title: String): Uri? {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val pdfRendererService = PdfRendererService(context)
        
        try {
            val pageCount = pdfRendererService.openUri(pdfUri)
            for (i in 0 until pageCount) {
                val originalBitmap = pdfRendererService.renderPage(i) ?: continue
                
                // Compress: Scale down and reduce quality
                val scaledWidth = (originalBitmap.width * 0.7f).toInt()
                val scaledHeight = (originalBitmap.height * 0.7f).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
                
                val pageInfo = PdfDocument.PageInfo.Builder(scaledWidth, scaledHeight, i + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
                pdfDocument.finishPage(page)
            }
            pdfRendererService.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val file = File(exportDir, "${title.replace(" ", "_")}_compressed.pdf")
        
        return try {
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}
