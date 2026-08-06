package com.yourname.pdftoolkit.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import com.yourname.pdftoolkit.data.SafUriManager
import com.yourname.pdftoolkit.ui.navigation.AppNavigation
import com.yourname.pdftoolkit.ui.theme.EyadPdfTheme
import com.yourname.pdftoolkit.util.CacheManager
import com.yourname.pdftoolkit.util.LanguageManager
import com.yourname.pdftoolkit.util.RatingManager
import com.yourname.pdftoolkit.util.ReviewHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Main entry point for the Eyad PDF app.
 * Handles intent-based PDF opening and sets up navigation.
 *
 * IMPORTANT: This activity properly handles SAF URIs for Android 10+ compliance.
 * Files opened from external apps are either:
 * 1. Accessed directly if persistable permission can be taken
 * 2. Copied to cache and accessed via FileProvider if direct access fails
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var pendingPdfUri: Uri? = null
    private var pendingPdfName: String? = null
    private var pendingIsLoading: Boolean = false

    private var pdfUriState: MutableState<Uri?>? = null
    private var pdfNameState: MutableState<String?>? = null
    private var isLoadingState: MutableState<Boolean>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        try {
            handleIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling intent in onCreate", e)
        }

        Log.d(
            TAG,
            "onCreate: pendingPdfUri=$pendingPdfUri, pendingPdfName=$pendingPdfName, pendingIsLoading=$pendingIsLoading"
        )

        setContent {
            EyadPdfTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = androidx.navigation.compose.rememberNavController()
                    val pdfUri = remember { mutableStateOf(pendingPdfUri) }
                    val pdfName = remember { mutableStateOf(pendingPdfName) }
                    val isLoading = remember { mutableStateOf(pendingIsLoading) }

                    pdfUriState = pdfUri
                    pdfNameState = pdfName
                    isLoadingState = isLoading

                    LaunchedEffect(Unit) {
                        RatingManager.showRatingRequest.collect { shouldShow ->
                            if (shouldShow) {
                                ReviewHelper.showReview(this@MainActivity)
                            }
                        }
                    }

                    if (isLoading.value) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        AppNavigation(
                            navController = navController,
                            modifier = Modifier.fillMaxSize(),
                            initialPdfUri = pdfUri.value,
                            initialPdfName = pdfName.value
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            try {
                Thread {
                    try {
                        CacheManager.clearAllCaches(this)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error clearing cache", e)
                    }
                }.start()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting cache cleanup", e)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        try {
            handleIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling new intent", e)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { processPdfUri(it) }
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                uri?.let { processPdfUri(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                uris?.firstOrNull()?.let { processPdfUri(it) }
            }
        }
    }

    private fun processPdfUri(uri: Uri) {
        pendingIsLoading = true
        isLoadingState?.value = true

        activityScope.launch {
            try {
                val resolved = withContext(Dispatchers.IO) { resolveAccessibleUri(uri) }
                pendingPdfUri = resolved
                pendingPdfName = getDisplayName(uri)
                pdfUriState?.value = resolved
                pdfNameState?.value = pendingPdfName
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process PDF URI", e)
            } finally {
                pendingIsLoading = false
                isLoadingState?.value = false
            }
        }
    }

    private fun resolveAccessibleUri(uri: Uri): Uri {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            SafUriManager.addRecentFile(this, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return uri
        } catch (_: Exception) {
            // Some shared URIs do not permit persisted access. Copy those to app cache.
        }

        val input = contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open shared PDF")
        val cachedFile = File(cacheDir, "shared_${System.currentTimeMillis()}.pdf")
        input.use { source ->
            FileOutputStream(cachedFile).use { target -> source.copyTo(target) }
        }
        return FileProvider.getUriForFile(this, "$packageName.provider", cachedFile)
    }

    private fun getDisplayName(uri: Uri): String {
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(index) ?: "PDF Document"
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "PDF Document"
    }
}
