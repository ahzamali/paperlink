package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LibraryViewModel
import com.example.ui.viewmodel.ReaderViewModel

class MainActivity : ComponentActivity() {

    private val libraryViewModel: LibraryViewModel by viewModels()
    private val readerViewModel: ReaderViewModel by viewModels()

    private var openedDocIdFromIntent by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(systemDark) }
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Library) }

            // Handle incoming PDF intent if launched via external open or share
            LaunchedEffect(intent) {
                handleIncomingPdfIntent(intent) { docId ->
                    readerViewModel.loadDocument(docId)
                    currentScreen = Screen.Reader(docId)
                }
            }

            LaunchedEffect(openedDocIdFromIntent) {
                openedDocIdFromIntent?.let { docId ->
                    readerViewModel.loadDocument(docId)
                    currentScreen = Screen.Reader(docId)
                }
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (val screen = currentScreen) {
                        is Screen.Library -> {
                            LibraryScreen(
                                viewModel = libraryViewModel,
                                isDarkTheme = isDarkTheme,
                                onToggleDarkTheme = { isDarkTheme = !isDarkTheme },
                                onOpenDocument = { docId ->
                                    readerViewModel.loadDocument(docId)
                                    currentScreen = Screen.Reader(docId)
                                }
                            )
                        }
                        is Screen.Reader -> {
                            ReaderScreen(
                                viewModel = readerViewModel,
                                onBack = {
                                    currentScreen = Screen.Library
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingPdfIntent(intent) { docId ->
            openedDocIdFromIntent = docId
            readerViewModel.loadDocument(docId)
        }
    }

    private fun handleIncomingPdfIntent(intent: Intent?, onDocumentReady: (Long) -> Unit) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }

        if (uri != null) {
            libraryViewModel.openPdf(uri) { docId ->
                onDocumentReady(docId)
            }
        }
    }
}

sealed interface Screen {
    data object Library : Screen
    data class Reader(val documentId: Long) : Screen
}
