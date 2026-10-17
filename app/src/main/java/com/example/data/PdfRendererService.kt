package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfRendererService(private val context: Context) {
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    suspend fun openUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        close()
        try {
            val contentResolver = context.contentResolver
            fileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            fileDescriptor?.let {
                pdfRenderer = PdfRenderer(it)
                return@withContext pdfRenderer?.pageCount ?: 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        0
    }

    suspend fun openLocalFile(file: File): Int = withContext(Dispatchers.IO) {
        close()
        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor?.let {
                pdfRenderer = PdfRenderer(it)
                return@withContext pdfRenderer?.pageCount ?: 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        0
    }

    suspend fun renderPage(pageIndex: Int, width: Int = 1000): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        try {
            val page = renderer.openPage(pageIndex)
            val height = (width * page.height / page.width)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun close() {
        pdfRenderer?.close()
        fileDescriptor?.close()
        pdfRenderer = null
        fileDescriptor = null
    }
}
