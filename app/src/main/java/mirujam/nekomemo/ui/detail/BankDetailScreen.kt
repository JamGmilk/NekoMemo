package mirujam.nekomemo.ui.detail

import android.annotation.SuppressLint
import android.net.Uri
import timber.log.Timber
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import mirujam.nekomemo.R
import mirujam.nekomemo.data.repository.CategoryRepository
import mirujam.nekomemo.ui.component.AppTopBar
import mirujam.nekomemo.ui.component.DialogWithIcon
import mirujam.nekomemo.ui.component.EditBankDialog
import mirujam.nekomemo.ui.component.LocalSnackbarHostState
import mirujam.nekomemo.ui.model.QuestionUiModel
import mirujam.nekomemo.ui.theme.AppShapes
import mirujam.nekomemo.ui.theme.ButtonShapes

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankDetailScreen(
    onStartTest: (Long, Int, Boolean, Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: BankDetailViewModel = hiltViewModel()
) {
    val pagingItems = viewModel.pagedQuestions.collectAsLazyPagingItems()
    val bankTitle by viewModel.bankTitle.collectAsState()
    val bankCategoryId by viewModel.bankCategoryId.collectAsState()
    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val showAddQuestionDialog by viewModel.showAddQuestionDialog.collectAsState()
    val editingQuestionId by viewModel.editingQuestionId.collectAsState()
    val editingQuestion by viewModel.editingQuestion.collectAsState()
    val showDeleteConfirmDialog by viewModel.showDeleteConfirmDialog.collectAsState()
    val showDeleteBankConfirmDialog by viewModel.showDeleteBankConfirmDialog.collectAsState()

    val filteredQuestions by viewModel.filteredQuestions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val questionCount by viewModel.questionCount.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var showTestConfigDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val isSearchBlank by remember { derivedStateOf { searchQuery.isBlank() } }
    val questionCountText = pluralStringResource(R.plurals.library_questions_count, questionCount, questionCount)

    val context = LocalContext.current
    val exportState by viewModel.exportState.collectAsState()
    val snackbarHostState = LocalSnackbarHostState.current

    var exportErrorMessage by remember { mutableStateOf<String?>(null) }
    var capturedExportJson by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(exportErrorMessage) {
        exportErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
            exportErrorMessage = null
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            val json = capturedExportJson ?: return@let
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                exportErrorMessage = context.getString(R.string.library_delete_error, e.message ?: "Unknown error")
            }
            viewModel.clearExportState()
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
            viewModel.clearExportState()
        }
    }

    if (showEditDialog) {
        EditBankDialog(
            initialTitle = bankTitle,
            initialCategoryId = bankCategoryId,
            categories = categories,
            onDismiss = { viewModel.dismissEditDialog() },
            onConfirm = { title, categoryId -> viewModel.updateBank(title, categoryId) }
        )
    }

    if (showAddQuestionDialog) {
        QuestionEditDialog(
            title = stringResource(R.string.detail_add_dialog_title),
            initialText = "",
            initialOptions = listOf("", "", "", ""),
            initialCorrectIndices = listOf(0),
            onDismiss = { viewModel.dismissAddQuestionDialog() },
            onConfirm = { text, options, correctIndices ->
                viewModel.addQuestion(text, options, correctIndices)
            }
        )
    }

    editingQuestion?.let { q ->
        QuestionEditDialog(
            title = stringResource(R.string.detail_edit_question_dialog_title),
            initialText = q.text,
            initialOptions = q.options,
            initialCorrectIndices = q.correctIndices,
            onDismiss = { viewModel.dismissEditQuestionDialog() },
            onConfirm = { text, options, correctIndices ->
                viewModel.updateQuestion(q.id, text, options, correctIndices)
            }
        )
    }

    if (showDeleteConfirmDialog) {
        DialogWithIcon(
            onDismiss = { viewModel.dismissDeleteConfirmDialog() },
            icon = Icons.Outlined.DeleteOutline,
            title = stringResource(R.string.detail_delete_question_title),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = { viewModel.confirmDeleteQuestion() },
            isDestructive = true,
            dismissText = stringResource(R.string.common_cancel),
            content = {
                Text(stringResource(R.string.detail_delete_question_message))
            }
        )
    }

    if (showDeleteBankConfirmDialog) {
        DialogWithIcon(
            onDismiss = { viewModel.dismissDeleteBankDialog() },
            icon = Icons.Outlined.DeleteOutline,
            title = stringResource(R.string.library_delete_title),
            confirmText = stringResource(R.string.library_delete_confirm),
            onConfirm = {
                viewModel.confirmDeleteBank()
                onBack()
            },
            isDestructive = true,
            dismissText = stringResource(R.string.common_cancel),
            content = {
                Text(stringResource(R.string.library_delete_message))
            }
        )
    }

    if (showTestConfigDialog && questionCount > 0) {
        TestConfigDialog(
            totalQuestions = questionCount,
            onDismiss = { showTestConfigDialog = false },
            onStart = { count, shuffleQuestions, shuffleOptions ->
                showTestConfigDialog = false
                Timber.d("Starting Test - bankId: ${viewModel.bankIdValue}, questionCount: $count, shuffleQuestions: $shuffleQuestions, shuffleOptions: $shuffleOptions, totalQuestionsAvailable: $questionCount")
                onStartTest(viewModel.bankIdValue, count, shuffleQuestions, shuffleOptions)
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = bankTitle,
                onNavigationClick = onBack,
                showSearch = true,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Below),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.detail_add_question)) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { viewModel.showAddQuestionDialog() }) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.detail_add_question)
                            )
                        }
                    }
                    Box {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Below),
                            tooltip = { PlainTooltip { Text(stringResource(R.string.library_more_options)) } },
                            state = rememberTooltipState()
                        ) {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.library_more_options)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_edit)) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.showEditDialog()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                                }
                            )
                            if (questionCount > 0) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_export)) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.prepareExport()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.IosShare, null, modifier = Modifier.size(18.dp))
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.showDeleteBankDialog()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (questionCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Quiz,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.detail_no_questions),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.showAddQuestionDialog() },
                        shape = ButtonShapes
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.detail_add_question))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppShapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                val categoryName = categories.find { it.id == bankCategoryId }?.name ?: ""
                                val displayName = if (categoryName == CategoryRepository.DEFAULT_CATEGORY_NAME) {
                                    stringResource(R.string.category_general_display)
                                } else categoryName
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = questionCountText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (isSearchBlank) {
                        items(
                            count = pagingItems.itemCount,
                            key = { index -> pagingItems[index]?.id ?: index },
                            contentType = { "question" }
                        ) { index ->
                            val question = pagingItems[index] ?: return@items
                            QuestionCard(
                                question = question,
                                optionList = question.options,
                                onEdit = { viewModel.showEditQuestionDialog(question.id) },
                                onDelete = { viewModel.deleteQuestion(question.id) }
                            )
                        }
                    } else {
                        items(filteredQuestions, key = { it.id }, contentType = { "question" }) { question ->
                            QuestionCard(
                                question = question,
                                optionList = question.options,
                                onEdit = { viewModel.showEditQuestionDialog(question.id) },
                                onDelete = { viewModel.deleteQuestion(question.id) }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                Button(
                    onClick = { showTestConfigDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = ButtonShapes
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Quiz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.detail_start_test))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionCard(
    question: QuestionUiModel,
    optionList: List<String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = question.text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row(modifier = Modifier) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.common_edit)) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.common_edit),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.common_delete)) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.common_delete),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            optionList.forEachIndexed { index, option ->
                val isCorrect = index in question.correctIndices
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCorrect) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val optionLetter = ('A' + index).toString()
                    Text(
                        text = "$optionLetter. $option",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

enum class TestSelectionMode {
    ALL, CUSTOM
}

@Composable
private fun TestConfigDialog(
    totalQuestions: Int,
    onDismiss: () -> Unit,
    onStart: (count: Int, shuffleQuestions: Boolean, shuffleOptions: Boolean) -> Unit
) {
    var selectedMode by remember { mutableStateOf(TestSelectionMode.ALL) }
    var selectedCount by remember { mutableIntStateOf(totalQuestions) }
    var shuffleQuestions by remember { mutableStateOf(false) }
    var shuffleOptions by remember { mutableStateOf(false) }

    DialogWithIcon(
        onDismiss = onDismiss,
        icon = Icons.Outlined.Quiz,
        title = stringResource(R.string.detail_test_config_title),
        confirmText = stringResource(R.string.detail_start_test),
        onConfirm = { onStart(selectedCount, shuffleQuestions, shuffleOptions) },
        dismissText = stringResource(R.string.common_cancel),
        content = {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TestSelectionMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.small)
                            .selectable(
                                selected = (mode == selectedMode),
                                onClick = {
                                    selectedMode = mode
                                    if (mode == TestSelectionMode.ALL) {
                                        selectedCount = totalQuestions
                                    }
                                },
                                role = Role.RadioButton,
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (mode == selectedMode),
                            onClick = null,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when (mode) {
                                TestSelectionMode.ALL -> stringResource(R.string.detail_all_questions, totalQuestions)
                                TestSelectionMode.CUSTOM -> stringResource(R.string.detail_custom_count)
                            }
                        )
                    }
                }
            }

            if (selectedMode == TestSelectionMode.CUSTOM && totalQuestions > 1) {
                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value = selectedCount.toFloat(),
                    onValueChange = { selectedCount = it.toInt() },
                    valueRange = 1f..totalQuestions.toFloat(),
                    steps = (totalQuestions - 2).coerceAtLeast(0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Text(
                    text = pluralStringResource(R.plurals.detail_selected_questions_count, selectedCount, selectedCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CheckboxRow(
                    text = stringResource(R.string.detail_shuffle_questions),
                    checked = shuffleQuestions,
                    onCheckedChange = { shuffleQuestions = it }
                )

                CheckboxRow(
                    text = stringResource(R.string.detail_shuffle_options),
                    checked = shuffleOptions,
                    onCheckedChange = { shuffleOptions = it }
                )
            }
        }
    )
}

@Composable
private fun CheckboxRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.small)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text)
    }
}

@Composable
private fun QuestionEditDialog(
    title: String,
    initialText: String,
    initialOptions: List<String>,
    initialCorrectIndices: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>, List<Int>) -> Unit
) {
    var questionText by remember { mutableStateOf(initialText) }
    val options = remember { mutableStateListOf(*initialOptions.toTypedArray()) }
    val correctIndices = remember { mutableStateListOf(*initialCorrectIndices.toTypedArray()) }

    DialogWithIcon(
        onDismiss = onDismiss,
        icon = Icons.Outlined.Edit,
        title = title,
        confirmText = stringResource(R.string.common_save),
        onConfirm = {
            onConfirm(questionText, options.filter { it.isNotBlank() }, correctIndices.toList())
        },
        confirmEnabled = questionText.isNotBlank() && options.any { it.isNotBlank() } && correctIndices.isNotEmpty(),
        dismissText = stringResource(R.string.common_cancel),
        content = {
            OutlinedTextField(
                value = questionText,
                onValueChange = { questionText = it },
                label = { Text(stringResource(R.string.detail_question_text_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.extraSmall,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = index in correctIndices,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    correctIndices.add(index)
                                } else {
                                    correctIndices.remove(index)
                                }
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        OutlinedTextField(
                            value = option,
                            onValueChange = { options[index] = it },
                            label = { Text(stringResource(R.string.detail_option_label, index + 1)) },
                            modifier = Modifier.weight(1f),
                            shape = AppShapes.extraSmall,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (options.size > 2) {
                    TextButton(onClick = { options.removeAt(options.lastIndex) }) {
                        Text(stringResource(R.string.detail_remove_last_option))
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                if (options.size < 8) {
                    TextButton(onClick = { options.add("") }) {
                        Text(stringResource(R.string.detail_add_option))
                    }
                }
            }
        }
    )
}
