package org.example.project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.example.project.db.Database

class MainActivity : ComponentActivity() {
    private val database by lazy { createDatabase(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        org.example.project.ReceiptPdfStorage.context = applicationContext

        setContent {
            App(database)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val previewDatabase = rememberPreviewDatabase(context)
    App(previewDatabase)
}

@Composable
private fun rememberPreviewDatabase(context: android.content.Context): Database =
    androidx.compose.runtime.remember(context) {
        createDatabase(context, "cloan-preview.db")
    }
