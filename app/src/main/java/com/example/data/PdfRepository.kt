package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PdfRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google.")
    }

    fun observePdfs(): Flow<List<PdfDocumentEntity>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("users").document(uid).collection("pdfs")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(PdfDocumentEntity::class.java) }
                .catch { e ->
                    Log.e("PdfRepository", "Error observing PDFs", e)
                    emit(emptyList())
                }
        )
    }

    suspend fun addPdf(title: String, uriString: String, pageCount: Int): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("pdfs").document()
        val data = mapOf(
            "userId" to uid,
            "title" to title,
            "uriString" to uriString,
            "pageCount" to pageCount,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(data).await()
        return docRef.id
    }

    suspend fun deletePdf(pdfId: String) {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("pdfs").document(pdfId).delete().await()
    }

    fun observeAnnotations(pdfId: String): Flow<List<AnnotationItem>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("users").document(uid).collection("pdfs").document(pdfId).collection("annotations")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(AnnotationItem::class.java) }
                .catch { e ->
                    Log.e("PdfRepository", "Error observing annotations", e)
                    emit(emptyList())
                }
        )
    }

    suspend fun addAnnotation(pdfId: String, item: AnnotationItem) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("pdfs").document(pdfId).collection("annotations").document()
        val payload = mapOf(
            "pdfId" to pdfId,
            "pageIndex" to item.pageIndex,
            "type" to item.type,
            "colorHex" to item.colorHex,
            "points" to item.points,
            "textNote" to item.textNote,
            "x" to item.x,
            "y" to item.y,
            "width" to item.width,
            "height" to item.height,
            "strokeWidth" to item.strokeWidth,
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
    }

    suspend fun deleteAnnotation(pdfId: String, annotationId: String) {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("pdfs").document(pdfId).collection("annotations").document(annotationId).delete().await()
    }

    fun observeSignatures(): Flow<List<SignatureItem>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("users").document(uid).collection("signatures")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(SignatureItem::class.java) }
                .catch { emit(emptyList()) }
        )
    }

    suspend fun saveSignature(name: String, pointsJson: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("signatures").document()
        val payload = mapOf(
            "userId" to uid,
            "name" to name,
            "pointsJson" to pointsJson,
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
    }

    suspend fun deleteSignature(signatureId: String) {
        val uid = requireUserId()
        db.collection("users").document(uid).collection("signatures").document(signatureId).delete().await()
    }
}
