package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class PdfDocumentEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "Untitled PDF",
    val uriString: String = "",
    val pageCount: Int = 1,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class AnnotationItem(
    @DocumentId val id: String = "",
    val pdfId: String = "",
    val pageIndex: Int = 0,
    val type: String = "HIGHLIGHT", // HIGHLIGHT, DRAW, NOTE, REDACT, SIGNATURE, SHAPE
    val colorHex: String = "#FFEB3B",
    val points: List<Float> = emptyList(),
    val textNote: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val strokeWidth: Float = 5f,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class SignatureItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "My Signature",
    val pointsJson: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
