package mirujam.nekomemo.ui.test

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mirujam.nekomemo.R
import mirujam.nekomemo.domain.model.QuestionType
import mirujam.nekomemo.ui.component.AppTopBar
import mirujam.nekomemo.ui.component.DialogWithIcon
import mirujam.nekomemo.ui.model.AnswerResult
import mirujam.nekomemo.ui.model.QuestionUiModel
import mirujam.nekomemo.ui.model.ScoreModel
import mirujam.nekomemo.ui.theme.AppShapes
import mirujam.nekomemo.ui.theme.ButtonShapes
import mirujam.nekomemo.ui.theme.ProgressIndicatorShapes

@Composable
fun TestScreen(
    onBack: () -> Unit,
    viewModel: TestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val directAnswer by viewModel.directAnswer.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val currentIndex = uiState.currentIndex
    val bankTitle = uiState.bankTitle
    val selectedAnswers = uiState.selectedAnswers
    val textAnswers = uiState.textAnswers
    val revealedQuestions = uiState.revealedQuestions
    val isFinished = uiState.isFinished
    val isReviewing = uiState.isReviewing
    val isLoading = uiState.isLoading
    val questions = uiState.questions

    if (uiState.showResumeDialog) {
        DialogWithIcon(
            onDismiss = { viewModel.restartSession() },
            icon = Icons.Outlined.Quiz,
            title = stringResource(R.string.test_resume_title),
            confirmText = stringResource(R.string.test_resume_continue),
            onConfirm = { viewModel.continueSession() },
            dismissText = stringResource(R.string.test_resume_restart),
            content = {
                Text(stringResource(R.string.test_resume_message))
            }
        )
    }

    if (uiState.showAnswerSheet) {
        AnswerSheetDialog(
            questions = questions,
            currentIndex = currentIndex,
            isAnswered = { viewModel.isQuestionAnswered(it) },
            isRevealed = { it in revealedQuestions },
            onSelect = { viewModel.goToQuestion(it) },
            onDismiss = { viewModel.setAnswerSheetVisible(false) }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (isReviewing) {
                    stringResource(R.string.test_review_answers)
                } else {
                    bankTitle.asString(context)
                },
                onNavigationClick = if (isReviewing) {
                    { viewModel.exitReview() }
                } else {
                    onBack
                },
                actions = {
                    if (!isLoading && questions.isNotEmpty() && (!isFinished || isReviewing)) {
                        IconButton(onClick = { viewModel.setAnswerSheetVisible(true) }) {
                            Icon(
                                imageVector = Icons.Outlined.GridView,
                                contentDescription = stringResource(R.string.test_answer_sheet)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            isLoading || uiState.showResumeDialog -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.test_loading_questions),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            questions.isEmpty() -> {
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
                            text = stringResource(R.string.test_no_questions),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            isFinished && !isReviewing -> {
                ScoreSummary(
                    viewModel = viewModel,
                    questions = questions,
                    selectedAnswers = selectedAnswers,
                    textAnswers = textAnswers,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            else -> {
                val isReviewMode = isReviewing
                val targetProgress = (currentIndex + 1).toFloat() / questions.size
                val animatedProgress by animateFloatAsState(
                    targetValue = targetProgress,
                    animationSpec = tween(durationMillis = 300),
                    label = "progressAnimation"
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ProgressIndicatorShapes)
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentIndex in questions.indices) {
                        val question = questions[currentIndex]
                        val selectedSet = selectedAnswers[currentIndex] ?: emptySet()
                        val isRevealed = isReviewMode || currentIndex in revealedQuestions
                        val isSingleChoice =
                            question.type == QuestionType.SINGLE_CHOICE || question.type == QuestionType.TRUE_FALSE
                        val isFillBlank =
                            question.type == QuestionType.FILL_BLANK || question.type == QuestionType.SHORT_ANSWER

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
                                Text(
                                    text = stringResource(question.type.displayNameRes()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = stringResource(
                                    R.string.test_question_progress,
                                    currentIndex + 1,
                                    questions.size
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = AppShapes.large,
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = question.text,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(20.dp))

                                if (isFillBlank) {
                                    FillBlankInputs(
                                        question = question,
                                        answers = textAnswers[currentIndex].orEmpty(),
                                        isRevealed = isRevealed,
                                        enabled = !isRevealed,
                                        onAnswerChange = { blankIndex, value ->
                                            viewModel.updateTextAnswer(currentIndex, blankIndex, value)
                                        }
                                    )
                                } else {
                                    ChoiceOptions(
                                        question = question,
                                        selectedSet = selectedSet,
                                        isRevealed = isRevealed,
                                        isSingleChoice = isSingleChoice,
                                        onToggle = { optionIndex ->
                                            viewModel.toggleAnswer(currentIndex, optionIndex, isSingleChoice)
                                        }
                                    )
                                }

                                val hasFillInput = textAnswers[currentIndex].orEmpty().any { it.isNotBlank() }
                                val showCheckButton = !isRevealed && (
                                    if (isFillBlank) {
                                        hasFillInput
                                    } else {
                                        selectedSet.isNotEmpty() && (!directAnswer || !isSingleChoice)
                                    }
                                )
                                if (showCheckButton) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.revealAnswer(currentIndex) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = ButtonShapes
                                    ) {
                                        Text(text = stringResource(R.string.test_check_answer))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.previousQuestion() },
                            enabled = currentIndex > 0,
                            shape = ButtonShapes
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.test_previous))
                        }

                        if (currentIndex == questions.size - 1 && !isReviewMode) {
                            Button(
                                onClick = { viewModel.finishTest() },
                                shape = ButtonShapes
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Checklist,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stringResource(R.string.test_finish))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.nextQuestion(questions.size) },
                                shape = ButtonShapes
                            ) {
                                Text(text = stringResource(R.string.test_next))
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FillBlankInputs(
    question: QuestionUiModel,
    answers: List<String>,
    isRevealed: Boolean,
    enabled: Boolean,
    onAnswerChange: (Int, String) -> Unit
) {
    val blankCount = when (question.type) {
        QuestionType.SHORT_ANSWER -> 1
        else -> question.options.size.coerceAtLeast(1)
    }
    val result = if (isRevealed) {
        ScoreModel.evaluateQuestion(question, null, answers)
    } else {
        null
    }

    repeat(blankCount) { blankIndex ->
        val value = answers.getOrElse(blankIndex) { "" }
        OutlinedTextField(
            value = value,
            onValueChange = { onAnswerChange(blankIndex, it) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    if (question.type == QuestionType.SHORT_ANSWER) {
                        stringResource(R.string.test_your_answer)
                    } else {
                        stringResource(R.string.test_blank_label, blankIndex + 1)
                    }
                )
            },
            minLines = if (question.type == QuestionType.SHORT_ANSWER) 3 else 1
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

    if (isRevealed) {
        val tone = when (result) {
            AnswerResult.CORRECT -> MaterialTheme.colorScheme.primary
            AnswerResult.WRONG -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            text = when (result) {
                AnswerResult.CORRECT -> stringResource(R.string.test_correct)
                AnswerResult.WRONG -> stringResource(R.string.test_wrong)
                else -> stringResource(R.string.test_skipped)
            },
            color = tone,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.test_reference_answer,
                question.options.joinToString(" / ")
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun ChoiceOptions(
    question: QuestionUiModel,
    selectedSet: Set<Int>,
    isRevealed: Boolean,
    isSingleChoice: Boolean,
    onToggle: (Int) -> Unit
) {
    question.options.forEachIndexed { optionIndex, option ->
        val isSelected = optionIndex in selectedSet
        val isCorrect = optionIndex in question.correctIndices
        val showResult = isRevealed && isCorrect
        val showWrong = isSelected && isRevealed && !isCorrect

        val borderColor = when {
            showResult -> MaterialTheme.colorScheme.primary
            showWrong -> MaterialTheme.colorScheme.error
            isSelected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        }
        val bgColor = when {
            showResult -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            showWrong -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surface
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.small)
                .background(bgColor)
                .border(width = 1.dp, color = borderColor, shape = AppShapes.small)
                .clickable(enabled = !isRevealed) { onToggle(optionIndex) }
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isSingleChoice) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "${'A' + optionIndex}. $option",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isRevealed && showResult) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (isRevealed && showWrong) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerSheetDialog(
    questions: List<QuestionUiModel>,
    currentIndex: Int,
    isAnswered: (Int) -> Boolean,
    isRevealed: (Int) -> Boolean,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    DialogWithIcon(
        onDismiss = onDismiss,
        icon = Icons.Outlined.GridView,
        title = stringResource(R.string.test_answer_sheet),
        confirmText = stringResource(R.string.common_close),
        onConfirm = onDismiss,
        dismissText = null,
        content = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                questions.forEachIndexed { index, _ ->
                    val answered = isAnswered(index)
                    val revealed = isRevealed(index)
                    val container = when {
                        index == currentIndex -> MaterialTheme.colorScheme.primaryContainer
                        revealed -> MaterialTheme.colorScheme.tertiaryContainer
                        answered -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(AppShapes.small)
                            .background(container)
                            .clickable { onSelect(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ScoreSummary(
    viewModel: TestViewModel,
    questions: List<QuestionUiModel>,
    selectedAnswers: Map<Int, Set<Int>>,
    textAnswers: Map<Int, List<String>>,
    modifier: Modifier = Modifier
) {
    val score = remember(questions, selectedAnswers, textAnswers) {
        viewModel.calculateScore(questions)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Checklist,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.test_complete),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "${score.percentage}%",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.test_score_summary,
                        score.total,
                        score.correct,
                        score.total
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ScoreStatCard(
                        value = score.correct,
                        label = stringResource(R.string.test_correct),
                        container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    ScoreStatCard(
                        value = score.wrong,
                        label = stringResource(R.string.test_wrong),
                        container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        valueColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    ScoreStatCard(
                        value = score.unanswered,
                        label = stringResource(R.string.test_skipped),
                        container = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        valueColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.startReview() },
            modifier = Modifier.fillMaxWidth(),
            shape = ButtonShapes
        ) {
            Icon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.test_review_answers))
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { viewModel.resetTest() },
            modifier = Modifier.fillMaxWidth(),
            shape = ButtonShapes
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.test_retake))
        }
    }
}

@Composable
private fun ScoreStatCard(
    value: Int,
    label: String,
    container: androidx.compose.ui.graphics.Color,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = AppShapes.small,
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$value",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
