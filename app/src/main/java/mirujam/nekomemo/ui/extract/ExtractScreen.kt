package mirujam.nekomemo.ui.extract

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import mirujam.nekomemo.R
import mirujam.nekomemo.domain.model.ExtractedQuestion
import mirujam.nekomemo.domain.model.QuestionType
import mirujam.nekomemo.navigation.Route
import mirujam.nekomemo.ui.component.AppTopBar
import mirujam.nekomemo.ui.component.DialogWithIcon
import mirujam.nekomemo.ui.component.displayName
import mirujam.nekomemo.ui.theme.AppShapes
import mirujam.nekomemo.ui.theme.ButtonShapes
import timber.log.Timber

@Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractScreen(
    onBack: () -> Unit,
    viewModel: ExtractViewModel = hiltViewModel()
) {
    val questionBank by viewModel.questionBank.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveResult by viewModel.saveResult.collectAsStateWithLifecycle()
    val isSaveSuccess by viewModel.isSaveSuccess.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            val jsonData = viewModel.loadFromSharedDataStore()
            if (jsonData != null) {
                Timber.d("Received JSON from SharedDataStore, length: ${jsonData.length}")
                viewModel.initFromJson(jsonData)
                val cleared = viewModel.clearSharedDataStore()
                if (cleared) {
                    Timber.d("Cleared SharedDataStore successfully")
                } else {
                    Timber.w("Failed to clear SharedDataStore")
                }
            } else {
                Timber.w("No JSON data found in SharedDataStore")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading data from SharedDataStore")
        }
    }

    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var bankTitle by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf(0L) }
    var categoryExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(questionBank?.name) {
        if (questionBank != null && bankTitle.isBlank()) {
            bankTitle = questionBank!!.name
            Timber.d("Auto-filled bank title: '${questionBank!!.name}'")
        }
    }

    LaunchedEffect(categories) {
        if (categories.isNotEmpty()) {
            if (selectedCategoryId == 0L) {
                selectedCategoryId = categories.first().id
            } else if (!categories.any { it.id == selectedCategoryId }) {
                selectedCategoryId = categories.first().id
            }
        }
    }

    LaunchedEffect(isSaveSuccess) {
        if (isSaveSuccess) {
            saveResult?.let {
                viewModel.setSaveResult(it.asString(context))
            }
            onBack()
            viewModel.onNavigatedBack()
        }
    }

    val selectedCategory = remember(selectedCategoryId, categories) {
        categories.find { it.id == selectedCategoryId }
    }

    if (showSaveDialog) {
        DialogWithIcon(
            onDismiss = { showSaveDialog = false },
            icon = Icons.Outlined.SaveAlt,
            title = stringResource(R.string.extract_save_dialog_title),
            confirmText = stringResource(R.string.common_save),
            onConfirm = {
                viewModel.saveQuestions(bankTitle, selectedCategoryId)
                showSaveDialog = false
            },
            confirmEnabled = bankTitle.isNotBlank() && !isSaving,
            isLoading = isSaving,
            dismissText = stringResource(R.string.common_cancel),
            dismissEnabled = !isSaving,
            content = {
                Column {
                    OutlinedTextField(
                        value = bankTitle,
                        onValueChange = { bankTitle = it },
                        label = { Text(stringResource(R.string.extract_bank_title_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.extraSmall,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = !categoryExpanded }
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        val displayName = selectedCategory?.displayName() ?: ""
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .focusable(interactionSource = interactionSource)
                        ) {
                            OutlinedTextFieldDefaults.DecorationBox(
                                value = displayName,
                                innerTextField = {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                enabled = true,
                                singleLine = true,
                                visualTransformation = VisualTransformation.None,
                                interactionSource = interactionSource,
                                label = { Text(stringResource(R.string.extract_category_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                container = {
                                    OutlinedTextFieldDefaults.Container(
                                        enabled = true,
                                        isError = false,
                                        interactionSource = interactionSource,
                                        shape = AppShapes.extraSmall
                                    )
                                }
                            )
                        }
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.displayName()) },
                                    onClick = {
                                        selectedCategoryId = category.id
                                        categoryExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pluralStringResource(R.plurals.extract_save_summary, questionBank?.questions?.size ?: 0, questionBank?.questions?.size ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(Route.Extract.titleResId),
                onNavigationClick = onBack
            )
        }
    ) { paddingValues ->
        val bank = questionBank
        if (bank == null || bank.questions.isEmpty()) {
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
                        text = stringResource(R.string.extract_no_questions),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
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
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = bank.name.ifBlank { stringResource(R.string.extract_untitled_bank) },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = pluralStringResource(R.plurals.extract_questions_extracted, bank.questions.size, bank.questions.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (bank.unsupportedTypeCount > 0 || bank.skippedCount > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val skipMessages = mutableListOf<String>()
                                    if (bank.unsupportedTypeCount > 0) {
                                        skipMessages.add(pluralStringResource(R.plurals.extract_skipped_unsupported, bank.unsupportedTypeCount, bank.unsupportedTypeCount))
                                    }
                                    if (bank.skippedCount > 0) {
                                        skipMessages.add(pluralStringResource(R.plurals.extract_skipped_no_answer, bank.skippedCount, bank.skippedCount))
                                    }
                                    Text(
                                        text = skipMessages.joinToString("; "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(bank.questions, key = { index, _ -> index }, contentType = { _, _ -> "question" }) { index, question ->
                        ExtractedQuestionCard(
                            index = index,
                            question = question,
                            modifier = Modifier.animateItem()
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                Button(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = ButtonShapes
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SaveAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.extract_save_to_library))
                }
            }
        }
    }
}

@Composable
private fun ExtractedQuestionCard(
    index: Int,
    question: ExtractedQuestion,
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${index + 1}. ${question.content}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            question.options.forEachIndexed { optIndex, option ->
                val isCorrect = optIndex in question.correctIndices
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
                    } else if (question.type != QuestionType.FILL_BLANK && question.type != QuestionType.SHORT_ANSWER) {
                        Icon(
                            imageVector = Icons.Outlined.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val prefix = if (question.type == QuestionType.FILL_BLANK || question.type == QuestionType.SHORT_ANSWER) {
                        "(${optIndex + 1})"
                    } else {
                        ('A' + optIndex).toString() + "."
                    }
                    Text(
                        text = "$prefix $option",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (question.correctAnswer.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.extract_correct_answer, question.correctAnswer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
