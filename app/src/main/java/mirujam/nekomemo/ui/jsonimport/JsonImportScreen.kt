package mirujam.nekomemo.ui.jsonimport

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mirujam.nekomemo.R
import mirujam.nekomemo.navigation.Route
import mirujam.nekomemo.ui.component.AppTopBar
import mirujam.nekomemo.ui.component.LocalSnackbarHostState
import mirujam.nekomemo.ui.model.UiText
import mirujam.nekomemo.ui.theme.AppShapes
import mirujam.nekomemo.ui.theme.ButtonShapes
import timber.log.Timber
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

/** 文本输入和文件读取的大小上限，防止大文本注入 TextField 导致渲染 ANR */
private const val MAX_INPUT_SIZE = 2 * 1024 * 1024L // 2MB

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
    var pasted by remember { mutableStateOf(false) }

    // Reset paste indicator after 1 second
    LaunchedEffect(pasted) {
        if (pasted) {
            delay(1000L.milliseconds)
            pasted = false
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val results = withContext(Dispatchers.IO) {
                    uris.map { validateAndReadFile(context, it) }
                }
                val contents = results.filterIsInstance<FileReadResult.Success>().map { it.content }
                if (contents.isNotEmpty()) {
                    viewModel.parseFiles(contents)
                }
                results.filterIsInstance<FileReadResult.Error>().firstOrNull()?.let {
                    viewModel.showSnackbar(it.message)
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
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).coerceToText(context).toString()
                        if (text.isBlank()) {
                            FileReadResult.Error(UiText.StringResource(R.string.json_import_clipboard_empty))
                        } else if (text.length > MAX_INPUT_SIZE) {
                            FileReadResult.Error(UiText.StringResource(R.string.json_import_error_text_too_long))
                        } else {
                            FileReadResult.Success(text)
                        }
                    } else {
                        FileReadResult.Error(UiText.StringResource(R.string.json_import_clipboard_empty))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to read clipboard")
                    FileReadResult.Error(UiText.StringResource(R.string.json_import_clipboard_empty))
                }
            }
            when (result) {
                is FileReadResult.Success -> {
                    viewModel.setJsonText(result.content)
                    pasted = true
                }
                is FileReadResult.Error -> viewModel.showSnackbar(result.message)
            }
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
                val pasteBorderColor by animateColorAsState(
                    targetValue = if (pasted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    animationSpec = tween(300)
                )
                OutlinedButton(
                    onClick = { pasteFromClipboard() },
                    modifier = Modifier.weight(1f),
                    shape = ButtonShapes,
                    border = BorderStroke(1.dp, pasteBorderColor)
                ) {
                    Crossfade(
                        targetState = pasted,
                        animationSpec = tween(300)
                    ) { isPasted ->
                        if (isPasted) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = stringResource(R.string.json_import_paste),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val textColor by animateColorAsState(
                        targetValue = if (pasted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                        animationSpec = tween(300)
                    )
                    Text(
                        text = if (pasted) stringResource(R.string.json_import_pasted) else stringResource(R.string.json_import_paste),
                        color = textColor
                    )
                }
                OutlinedButton(
                    onClick = {
                        fileLauncher.launch(arrayOf("application/json", "text/*"))
                    },
                    modifier = Modifier.weight(1f),
                    shape = ButtonShapes
                ) {
                    Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.json_import_select_file))
                }
            }

            val errorMessage = uiState.errorMessage
            if (uiState.batchJson.size > 1) {
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = AppShapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.json_import_batch_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(
                                R.string.json_import_batch_summary,
                                uiState.batchJson.size,
                                uiState.batchFailedCount
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.previewFirstBatch() },
                                enabled = !uiState.isParsing
                            ) {
                                Text(stringResource(R.string.json_import_batch_open_first))
                            }
                            Button(
                                onClick = { viewModel.saveAllBatch() },
                                enabled = !uiState.isParsing
                            ) {
                                Text(stringResource(R.string.json_import_batch_save_all))
                            }
                        }
                    }
                }
            }
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

// ── 文件读取结果 ──────────────────────────────────────────────

private sealed class FileReadResult {
    data class Success(val content: String) : FileReadResult()
    data class Error(val message: UiText) : FileReadResult()
}

/**
 * 校验并安全读取文件：
 * 1. 查询文件大小，拒绝过大文件
 * 2. 校验 MIME 类型/扩展名，拒绝非文本文件（图片/视频等）
 * 3. 限制读取字节数，防止恶意超大文件
 */
private fun validateAndReadFile(context: Context, uri: Uri): FileReadResult {
    val resolver = context.contentResolver

    // ── 1. 校验文件格式 ──
    val mimeType = try {
        resolver.getType(uri)
    } catch (e: Exception) {
        Timber.w(e, "Failed to get MIME type")
        null
    }

    // 明确拒绝媒体类型
    if (mimeType != null) {
        val nonTextPrefixes = listOf("image/", "video/", "audio/", "application/zip", "application/pdf")
        if (nonTextPrefixes.any { mimeType.startsWith(it) }) {
            return FileReadResult.Error(UiText.StringResource(R.string.json_import_error_file_format))
        }
    }

    // 检查扩展名
    val fileName = queryDisplayName(resolver, uri)
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val hasJsonExtension = extension == "json" || extension == "txt"

    // 如果 MIME 不明确且扩展名也不对，拒绝
    if (mimeType == null && !hasJsonExtension && fileName.isNotEmpty()) {
        // 允许无扩展名的文件通过（某些导出工具不带扩展名），但拒绝明显的非 JSON 扩展名
        val blockedExtensions = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "mp4", "avi", "mov",
            "mp3", "wav", "flac", "zip", "rar", "7z", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "apk", "exe", "bin", "db", "sqlite")
        if (extension in blockedExtensions) {
            return FileReadResult.Error(UiText.StringResource(R.string.json_import_error_file_format))
        }
    }

    // ── 2. 校验文件大小 ──
    val fileSize = queryFileSize(resolver, uri)
    if (fileSize > MAX_INPUT_SIZE) {
        Timber.w("File too large: $fileSize bytes (max=$MAX_INPUT_SIZE)")
        return FileReadResult.Error(UiText.StringResource(R.string.json_import_error_file_too_large))
    }

    // ── 3. 限制读取字节数 ──
    return try {
        resolver.openInputStream(uri)?.use { stream ->
            // 读取最多 MAX_INPUT_SIZE + 1 字节（多读 1 字节用于判断是否超限）
            val maxBytes = MAX_INPUT_SIZE + 1
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            var totalRead = 0L
            var exceeded = false

            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                totalRead += read
                if (totalRead > maxBytes) {
                    exceeded = true
                    break
                }
                output.write(buffer, 0, read)
            }

            if (exceeded) {
                Timber.w("File content exceeded limit during read")
                return FileReadResult.Error(UiText.StringResource(R.string.json_import_error_file_too_large))
            }

            val content = output.toString("UTF-8")

            // ── 4. 基本内容校验：JSON 必须包含 { 或 [ ──
            // 不要求首字符为 {/[，因为 ViewModel 会做 extractJsonContent 预处理，
            // 容忍 Markdown 代码围栏、前后注释等。这里只拒绝明显不含 JSON 结构的文件。
            if (!content.contains('{') && !content.contains('[')) {
                Timber.w("File content does not contain { or [, likely not JSON")
                return FileReadResult.Error(UiText.StringResource(R.string.json_import_error_file_format))
            }

            FileReadResult.Success(content)
        } ?: FileReadResult.Error(
            UiText.StringResource(R.string.json_import_file_read_error, arrayOf("Cannot open file"))
        )
    } catch (e: OutOfMemoryError) {
        Timber.e(e, "OOM while reading file")
        FileReadResult.Error(UiText.StringResource(R.string.json_import_error_file_too_large))
    } catch (e: IOException) {
        Timber.e(e, "IO error while reading file")
        FileReadResult.Error(
            UiText.StringResource(R.string.json_import_file_read_error, arrayOf(e.message ?: ""))
        )
    } catch (e: Exception) {
        Timber.e(e, "Unexpected error while reading file")
        FileReadResult.Error(
            UiText.StringResource(R.string.json_import_file_read_error, arrayOf(e.message ?: ""))
        )
    }
}

private fun queryFileSize(resolver: android.content.ContentResolver, uri: Uri): Long {
    return try {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                cursor.getLong(0)
            } else {
                -1L
            }
        } ?: -1L
    } catch (e: Exception) {
        Timber.w(e, "Failed to query file size")
        -1L
    }
}

private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String {
    return try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) ?: "" else ""
        } ?: ""
    } catch (e: Exception) {
        Timber.w(e, "Failed to query display name")
        ""
    }
}
