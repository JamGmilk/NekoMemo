package mirujam.nekomemo.ui.jsonimport

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mirujam.nekomemo.R
import mirujam.nekomemo.navigation.Route
import mirujam.nekomemo.ui.component.AppTopBar
import mirujam.nekomemo.ui.component.LocalSnackbarHostState
import mirujam.nekomemo.ui.model.UiText
import mirujam.nekomemo.ui.theme.AppShapes
import mirujam.nekomemo.ui.theme.ButtonShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonImportScreen(
    onNavigateToExtract: () -> Unit,
    onBack: () -> Unit,
    viewModel: JsonImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            java.io.BufferedReader(java.io.InputStreamReader(stream)).use { reader ->
                                reader.readText()
                            }
                        } ?: throw java.io.IOException("Cannot open file")
                    }
                    viewModel.setJsonText(json)
                    viewModel.showSnackbar(UiText.StringResource(R.string.json_import_file_loaded))
                } catch (e: Exception) {
                    viewModel.showSnackbar(
                        UiText.StringResource(R.string.json_import_file_read_error, arrayOf(e.message ?: ""))
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.getAndClearSaveResult()?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(uiState.navigateToExtract) {
        if (uiState.navigateToExtract) {
            onNavigateToExtract()
            viewModel.onNavigatedToExtract()
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it.asString(context))
            viewModel.clearSnackbar()
        }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(context).toString()
            if (text.isNotBlank()) {
                viewModel.setJsonText(text)
                viewModel.showSnackbar(UiText.StringResource(R.string.json_import_pasted))
            } else {
                viewModel.showSnackbar(UiText.StringResource(R.string.json_import_clipboard_empty))
            }
        } else {
            viewModel.showSnackbar(UiText.StringResource(R.string.json_import_clipboard_empty))
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(Route.JsonImport.titleResId),
                navigationIcon = Icons.Outlined.Close,
                onNavigationClick = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = stringResource(R.string.json_import_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { pasteFromClipboard() },
                    modifier = Modifier.weight(1f),
                    shape = ButtonShapes
                ) {
                    Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.json_import_paste))
                }
                OutlinedButton(
                    onClick = { fileLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.weight(1f),
                    shape = ButtonShapes
                ) {
                    Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.json_import_select_file))
                }
            }

            val errorMessage = uiState.errorMessage
            OutlinedTextField(
                value = uiState.jsonText,
                onValueChange = { viewModel.setJsonText(it) },
                label = { Text(stringResource(R.string.json_import_text_label)) },
                placeholder = { Text(stringResource(R.string.json_import_text_placeholder)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                isError = errorMessage != null,
                supportingText = errorMessage?.let { msg ->
                    @Composable {
                        Text(
                            text = msg.asString(context),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                trailingIcon = {
                    if (uiState.jsonText.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setJsonText("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.json_import_clear)
                            )
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                shape = AppShapes.extraSmall
            )

            Button(
                onClick = { viewModel.parseAndProceed() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = uiState.jsonText.isNotBlank() && !uiState.isParsing,
                shape = ButtonShapes
            ) {
                if (uiState.isParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.json_import_next))
            }
        }
    }
}
