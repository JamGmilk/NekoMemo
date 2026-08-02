package mirujam.nekomemo.ui.detail

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mirujam.nekomemo.R
import mirujam.nekomemo.domain.model.PracticeMode
import mirujam.nekomemo.domain.model.QuestionType
import mirujam.nekomemo.ui.component.AppTopBar
import mirujam.nekomemo.ui.component.DialogWithIcon
import mirujam.nekomemo.ui.component.EditBankDialog
import mirujam.nekomemo.ui.component.ExportLauncher
import mirujam.nekomemo.ui.component.LocalSnackbarHostState
import mirujam.nekomemo.ui.component.displayName
import mirujam.nekomemo.ui.model.QuestionUiModel
import mirujam.nekomemo.ui.test.TestQuestionSelector
import mirujam.nekomemo.ui.theme.AppShapes
import mirujam.nekomemo.ui.theme.ButtonShapes
import timber.log.Timber

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankDetailScreen(
    onStartTest: (Long, Int, Boolean, Boolean, PracticeMode, String, Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: BankDetailViewModel = hiltViewModel()
) {
    val bankTitle by viewModel.bankTitle.collectAsStateWithLifecycle()
    val bankCategoryId by viewModel.bankCategoryId.collectAsStateWithLifecycle()
    val showEditDialog by viewModel.showEditDialog.collectAsStateWithLifecycle()
    val showAddQuestionDialog by viewModel.showAddQuestionDialog.collectAsStateWithLifecycle()
    val editingQuestion by viewModel.editingQuestion.collectAsStateWithLifecycle()
    val showDeleteConfirmDialog by viewModel.showDeleteConfirmDialog.collectAsStateWithLifecycle()
    val showDeleteBankConfirmDialog by viewModel.showDeleteBankConfirmDialog.collectAsStateWithLifecycle()

    val questions by viewModel.filteredQuestions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val wrongBookCount by viewModel.wrongBookCount.collectAsStateWithLifecycle()
    val favoriteCount by viewModel.favoriteCount.collectAsStateWithLifecycle()
    val resumableSession by viewModel.resumableSession.collectAsStateWithLifecycle()
    var showTestConfigDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val questionCount = questions?.size ?: 0
    val isSearchActive by remember { derivedStateOf { searchQuery.isNotBlank() } }
    val questionCountText = pluralStringResource(R.plurals.library_questions_count, questionCount, questionCount)
    val bankCategory = remember(bankCategoryId, categories) {
        categories.find { it.id == bankCategoryId }
    }
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    var exportErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(exportErrorMessage) {
        exportErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
            exportErrorMessage = null
        }
    }

    ExportLauncher(
        exportState = exportState,
        onExportError = { exportErrorMessage = it },
        onClearExportState = { viewModel.clearExportState() }
    )

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
            initialType = QuestionType.SINGLE_CHOICE,
            onDismiss = { viewModel.dismissAddQuestionDialog() },
            onConfirm = { text, options, correctIndices, type ->
                viewModel.addQuestion(text, options, correctIndices, type)
            }
        )
    }

    editingQuestion?.let { q ->
        QuestionEditDialog(
            title = stringResource(R.string.detail_edit_question_dialog_title),
            initialText = q.text,
            initialOptions = q.options,
            initialCorrectIndices = q.correctIndices,
            initialType = q.type,
            onDismiss = { viewModel.dismissEditQuestionDialog() },
            onConfirm = { text, options, correctIndices, type ->
                viewModel.updateQuestion(q.id, text, options, correctIndices, type)
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
                viewModel.confirmDeleteBank(onBack)
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
            wrongBookCount = wrongBookCount,
            favoriteCount = favoriteCount,
            onDismiss = { showTestConfigDialog = false },
            onStart = { count, shuffleQuestions, shuffleOptions, practiceMode, types ->
                showTestConfigDialog = false
                Timber.d(
                    "Starting Test - bankId: ${viewModel.bankIdValue}, questionCount: $count, " +
                        "shuffleQuestions: $shuffleQuestions, shuffleOptions: $shuffleOptions, " +
                        "practiceMode: $practiceMode, types: $types"
                )
                onStartTest(
                    viewModel.bankIdValue,
                    count,
                    shuffleQuestions,
                    shuffleOptions,
                    practiceMode,
                    types,
                    false
                )
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
        },
        bottomBar = {
            if (questionCount > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (resumableSession != null) {
                        OutlinedButton(
                            onClick = {
                                onStartTest(
                                    viewModel.bankIdValue,
                                    0,
                                    false,
                                    false,
                                    PracticeMode.ALL,
                                    "",
                                    true
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ButtonShapes
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.detail_continue_test))
                        }
                    }
                    Button(
                        onClick = { showTestConfigDialog = true },
                        modifier = Modifier.fillMaxWidth(),
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
    ) { paddingValues ->
        val questionList = questions
        if (questionList == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else if (questionCount == 0) {
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
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
                            Text(
                                text = bankCategory?.displayName() ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = questionCountText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (wrongBookCount > 0 || favoriteCount > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.detail_stats_summary,
                                        wrongBookCount,
                                        favoriteCount
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (isSearchActive) {
                    item {
                        Text(
                            text = pluralStringResource(R.plurals.library_questions_count, questionList.size, questionList.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                items(questionList, key = { it.id }, contentType = { "question" }) { question ->
                    QuestionCard(
                        question = question,
                        onEdit = { viewModel.showEditQuestionDialog(question.id) },
                        onDelete = { viewModel.deleteQuestion(question) },
                        onToggleFavorite = {
                            viewModel.toggleFavorite(question.id, question.isFavorite)
                        },
                        modifier = Modifier.animateItem()
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionCard(
    question: QuestionUiModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    shape = AppShapes.extraSmall,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    val localizedType = stringResource(question.type.displayNameRes())
                    Text(
                        text = localizedType,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.detail_favorite)) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (question.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = stringResource(R.string.detail_favorite),
                                modifier = Modifier.size(20.dp),
                                tint = if (question.isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.common_edit)) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.common_edit),
                                modifier = Modifier.size(20.dp),
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
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.common_delete),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = question.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            val isFillBlank = question.type == QuestionType.FILL_BLANK || question.type == QuestionType.SHORT_ANSWER
            question.options.forEachIndexed { index, option ->
                val isCorrect = index in question.correctIndices
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isFillBlank) {
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
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val prefix = if (isFillBlank) "(${index + 1})" else ('A' + index).toString() + "."
                    Text(
                        text = "$prefix $option",
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestConfigDialog(
    totalQuestions: Int,
    wrongBookCount: Int,
    favoriteCount: Int,
    onDismiss: () -> Unit,
    onStart: (count: Int, shuffleQuestions: Boolean, shuffleOptions: Boolean, practiceMode: PracticeMode, types: String) -> Unit
) {
    var selectedMode by remember { mutableStateOf(TestSelectionMode.ALL) }
    var practiceMode by remember { mutableStateOf(PracticeMode.ALL) }
    var selectedTypes by remember { mutableStateOf(QuestionType.entries.toSet()) }
    val availableCount = remember(practiceMode, wrongBookCount, favoriteCount, totalQuestions) {
        when (practiceMode) {
            PracticeMode.ALL -> totalQuestions
            PracticeMode.WRONG -> wrongBookCount
            PracticeMode.FAVORITE -> favoriteCount
        }
    }
    var selectedCount by remember(availableCount) { mutableIntStateOf(availableCount.coerceAtLeast(0)) }
    var shuffleQuestions by remember { mutableStateOf(false) }
    var shuffleOptions by remember { mutableStateOf(false) }

    val canStart = availableCount > 0 && selectedTypes.isNotEmpty() && selectedCount > 0

    DialogWithIcon(
        onDismiss = onDismiss,
        icon = Icons.Outlined.Quiz,
        title = stringResource(R.string.detail_test_config_title),
        confirmText = stringResource(R.string.detail_start_test),
        onConfirm = {
            if (canStart) {
                onStart(
                    selectedCount,
                    shuffleQuestions,
                    shuffleOptions,
                    practiceMode,
                    TestQuestionSelector.encodeTypeCodes(selectedTypes)
                )
            }
        },
        confirmEnabled = canStart,
        dismissText = stringResource(R.string.common_cancel),
        content = {
            Text(
                text = stringResource(R.string.detail_practice_mode),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PracticeMode.entries.forEach { mode ->
                    val enabled = when (mode) {
                        PracticeMode.ALL -> totalQuestions > 0
                        PracticeMode.WRONG -> wrongBookCount > 0
                        PracticeMode.FAVORITE -> favoriteCount > 0
                    }
                    val label = when (mode) {
                        PracticeMode.ALL -> stringResource(R.string.detail_practice_all, totalQuestions)
                        PracticeMode.WRONG -> stringResource(R.string.detail_practice_wrong, wrongBookCount)
                        PracticeMode.FAVORITE -> stringResource(R.string.detail_practice_favorite, favoriteCount)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.small)
                            .selectable(
                                selected = practiceMode == mode,
                                enabled = enabled,
                                onClick = {
                                    practiceMode = mode
                                    selectedMode = TestSelectionMode.ALL
                                    selectedCount = when (mode) {
                                        PracticeMode.ALL -> totalQuestions
                                        PracticeMode.WRONG -> wrongBookCount
                                        PracticeMode.FAVORITE -> favoriteCount
                                    }
                                },
                                role = Role.RadioButton
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = practiceMode == mode,
                            onClick = null,
                            enabled = enabled
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            color = if (enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.detail_question_types),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                QuestionType.entries.forEach { type ->
                    val selected = type in selectedTypes
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedTypes = if (selected) {
                                if (selectedTypes.size == 1) selectedTypes else selectedTypes - type
                            } else {
                                selectedTypes + type
                            }
                        },
                        label = { Text(stringResource(type.displayNameRes())) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
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
                                enabled = availableCount > 0,
                                onClick = {
                                    selectedMode = mode
                                    if (mode == TestSelectionMode.ALL) {
                                        selectedCount = availableCount
                                    }
                                },
                                role = Role.RadioButton
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (mode == selectedMode),
                            onClick = null,
                            enabled = availableCount > 0
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when (mode) {
                                TestSelectionMode.ALL -> stringResource(R.string.detail_all_questions, availableCount)
                                TestSelectionMode.CUSTOM -> stringResource(R.string.detail_custom_count)
                            }
                        )
                    }
                }
            }

            if (selectedMode == TestSelectionMode.CUSTOM && availableCount > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = selectedCount.coerceIn(1, availableCount).toFloat(),
                    onValueChange = { selectedCount = it.toInt() },
                    valueRange = 1f..availableCount.toFloat(),
                    steps = (availableCount - 2).coerceAtLeast(0),
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

            if (!canStart) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.detail_no_matching_questions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
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
        verticalAlignment = Alignment.CenterVertically
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
    initialType: QuestionType = QuestionType.SINGLE_CHOICE,
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>, List<Int>, QuestionType) -> Unit
) {
    var questionText by remember { mutableStateOf(initialText) }
    val options = remember { mutableStateListOf(*initialOptions.toTypedArray()) }
    val correctIndices = remember { mutableStateListOf(*initialCorrectIndices.toTypedArray()) }
    var selectedType by remember { mutableStateOf(initialType) }

    // 切换类型时自动修正 correctIndices
    LaunchedEffect(selectedType) {
        when (selectedType) {
            QuestionType.SINGLE_CHOICE, QuestionType.TRUE_FALSE -> {
                // 单选题/判断题只保留第一个正确索引
                if (correctIndices.size > 1) {
                    val first = correctIndices.first()
                    correctIndices.clear()
                    correctIndices.add(first)
                } else if (correctIndices.isEmpty() && options.isNotEmpty()) {
                    correctIndices.add(0)
                }
            }
            QuestionType.FILL_BLANK, QuestionType.SHORT_ANSWER -> {
                // 如果所有选项都为空，精简到 1 个
                if (options.all { it.isBlank() } && options.size > 1) {
                    while (options.size > 1) {
                        options.removeAt(options.lastIndex)
                    }
                }
                // 填空题/简答题所有答案都正确
                correctIndices.clear()
                options.indices.forEach { correctIndices.add(it) }
            }
            else -> {
                // Multiple Choice: 保持不变，用户手动选择
            }
        }
    }

    val typeOptions = listOf(
        QuestionType.SINGLE_CHOICE to stringResource(R.string.question_type_single),
        QuestionType.MULTIPLE_CHOICE to stringResource(R.string.question_type_multiple),
        QuestionType.TRUE_FALSE to stringResource(R.string.question_type_true_false),
        QuestionType.FILL_BLANK to stringResource(R.string.question_type_fill_blank),
        QuestionType.SHORT_ANSWER to stringResource(R.string.question_type_short_answer)
    )

    DialogWithIcon(
        onDismiss = onDismiss,
        icon = Icons.Outlined.Edit,
        title = title,
        confirmText = stringResource(R.string.common_save),
        onConfirm = {
            // 过滤空选项，并修正 correctIndices 的索引偏移
            val nonEmptyOptions = options.mapIndexedNotNull { index, option ->
                if (option.isNotBlank()) index to option else null
            }
            val indexMapping = nonEmptyOptions.map { it.first }.withIndex()
                .associate { it.value to it.index }
            val adjustedIndices = correctIndices.mapNotNull { indexMapping[it] }
            onConfirm(questionText, nonEmptyOptions.map { it.second }, adjustedIndices, selectedType)
        },
        confirmEnabled = questionText.isNotBlank() && options.any { it.isNotBlank() } && correctIndices.isNotEmpty(),
        dismissText = stringResource(R.string.common_cancel),
        content = {
            // 类型选择
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                typeOptions.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = selectedType == value,
                        onClick = { selectedType = value },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = typeOptions.size),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = questionText,
                onValueChange = { questionText = it },
                label = { Text(stringResource(R.string.detail_question_text_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.extraSmall,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(6.dp))

            val isFillBlank = selectedType == QuestionType.FILL_BLANK || selectedType == QuestionType.SHORT_ANSWER
            val isSingleChoice = selectedType == QuestionType.SINGLE_CHOICE || selectedType == QuestionType.TRUE_FALSE

            Column {
                options.forEachIndexed { index, option ->
                    val isChecked = index in correctIndices
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(
                            visible = !isFillBlank,
                            enter = expandHorizontally(),
                            exit = shrinkHorizontally()
                        ) {
                            Crossfade(targetState = isSingleChoice to isChecked, label = "selectControl") { (single, checked) ->
                                if (single) {
                                    RadioButton(
                                        selected = checked,
                                        onClick = {
                                            correctIndices.clear()
                                            correctIndices.add(index)
                                        },
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                } else {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { c ->
                                            if (c) {
                                                correctIndices.add(index)
                                            } else {
                                                correctIndices.remove(index)
                                            }
                                        },
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = option,
                            onValueChange = { options[index] = it },
                            label = {
                                Text(if (isFillBlank) stringResource(R.string.detail_answer_label, index + 1) else stringResource(R.string.detail_option_label, index + 1))
                            },
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
                val minOptionCount = if (isFillBlank) 1 else 2
                if (options.size > minOptionCount) {
                    TextButton(onClick = {
                        val removedIndex = options.lastIndex
                        options.removeAt(removedIndex)
                        correctIndices.remove(removedIndex)
                        // 修正被删索引之后的索引（减1）
                        val updated = correctIndices.map { if (it > removedIndex) it - 1 else it }.toMutableList()
                        correctIndices.clear()
                        correctIndices.addAll(updated)
                    }) {
                        Text(stringResource(R.string.detail_remove_last_option))
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                if (options.size < 8) {
                    TextButton(onClick = {
                        options.add("")
                        // 填空题/简答题新选项自动加入正确索引
                        if (selectedType == QuestionType.FILL_BLANK || selectedType == QuestionType.SHORT_ANSWER) {
                            correctIndices.add(options.lastIndex)
                        }
                    }) {
                        Text(stringResource(R.string.detail_add_option))
                    }
                }
            }
        }
    )
}
