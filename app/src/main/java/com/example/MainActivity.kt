package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import com.example.data.PdfRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.editor.PdfEditorScreen
import com.example.ui.editor.SignatureDialog
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import com.example.ui.ads.AdBanner
import com.google.android.gms.ads.MobileAds
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = PdfRepository(this)
        val credentialManager = CredentialManager.create(this)

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}

        setContent {
            MyApplicationTheme {
                var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
                var currentPdfId by remember { mutableStateOf<String?>(null) }
                var currentPdfTitle by remember { mutableStateOf<String>("") }
                var currentPdfUri by remember { mutableStateOf<String>("") }
                var showSignatureManager by remember { mutableStateOf(false) }

                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { auth ->
                        currentUser = auth.currentUser
                    }
                    Firebase.auth.addAuthStateListener(listener)
                    onDispose {
                        Firebase.auth.removeAuthStateListener(listener)
                    }
                }

                val coroutineScope = rememberCoroutineScope()

                when {
                    currentUser == null -> {
                        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
                    }
                    currentPdfId != null -> {
                        PdfEditorScreen(
                            pdfId = currentPdfId!!,
                            pdfTitle = currentPdfTitle,
                            pdfUri = currentPdfUri,
                            repository = repository,
                            onBack = { currentPdfId = null }
                        )
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                DashboardScreen(
                                    repository = repository,
                                    onOpenPdf = { id, title, uri ->
                                        currentPdfId = id
                                        currentPdfTitle = title
                                        currentPdfUri = uri
                                    },
                                    onOpenSignatures = { showSignatureManager = true },
                                    onSignOut = {
                                        Firebase.auth.signOut()
                                        coroutineScope.launch {
                                            try {
                                                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                                            } catch (_: Exception) {}
                                            currentUser = null
                                        }
                                    }
                                )
                            }
                            // Show Test Ad Banner at the bottom
                            AdBanner()
                        }
                        
                        if (showSignatureManager) {
                            SignatureDialog(
                                repository = repository,
                                onDismiss = { showSignatureManager = false },
                                onSignatureSelected = { _ -> showSignatureManager = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
