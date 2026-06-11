package mirujam.nekomemo.ui.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import mirujam.nekomemo.R
import mirujam.nekomemo.ui.shared.ExportState

@Composable
fun ExportLauncher(
    exportState: ExportState,
    onExportError: (String) -> Unit,
    onClearExportState: () -> Unit
) {
    val context = LocalContext.current
    val appContext = remember { context.applicationContext }
    // Pre-format the error template in the Composable scope
    val errorTemplate = context.getString(R.string.library_delete_error)
    var capturedExportJson by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            val json = capturedExportJson ?: return@let
            try {
                appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                onExportError(errorTemplate.format(e.message ?: "Unknown error"))
            }
            onClearExportState()
            capturedExportJson = null
        }
    }

    LaunchedEffect(exportState) {
        if (exportState.isReady) {
            capturedExportJson = exportState.json
            exportLauncher.launch(exportState.fileName)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onClearExportState()
        }
    }
}
