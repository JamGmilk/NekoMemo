package mirujam.nekomemo.ui.test

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import mirujam.nekomemo.R
import mirujam.nekomemo.domain.model.QuestionType
import mirujam.nekomemo.ui.component.AppTopBar
import mirujam.nekomemo.ui.model.QuestionUiModel
import mirujam.nekomemo.ui.theme.AppShapes
import mirujam.nekomemo.ui.theme.ButtonShapes
import mirujam.nekomemo.ui.theme.ProgressIndicatorShapes

@Composable
fun TestScreen(
    onBack: () -> Unit,
    viewModel: TestViewModel = hiltViewModel()
) {
    val currentIndex by viewModel.currentIndex.collectAsState()
    val bankTitle by viewModel.bankTitle.collectAsState()
    val context = LocalContext.current
    val selectedAnswers by viewModel.selectedAnswers.collectAsState()
    val revealedQuestions by viewModel.revealedQuestions.collectAsState()
    val isFinished by viewModel.isFinished.collectAsState()
    val isReviewing by viewModel.isReviewing.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val directAnswer by viewModel.directAnswer.collectAsState()
    val questionUiModels by viewModel.questionUiModels.collectAsState()

    val questions = remember(questionUiModels) {
        viewModel.getActiveQuestions()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (isReviewing) stringResource(R.string.test_review_answers) else bankTitle.asString(context),
                onNavigationClick = if (isReviewing) {
                    { viewModel.exitReview() }
                } else {
                    onBack
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
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
        } else if (questions.isEmpty()) {
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
        } else if (isFinished && !isReviewing) {
            ScoreSummary(
                viewModel = viewModel,
                questions = questions,
                selectedAnswers = selectedAnswers,
                modifier = Modifier.padding(paddingValues)
            )
        } else {
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
                        val localizedType = when (question.type) {
                            QuestionType.SINGLE_CHOICE -> stringResource(R.string.question_type_single)
                            QuestionType.MULTIPLE_CHOICE -> stringResource(R.string.question_type_multiple)
                            QuestionType.TRUE_FALSE -> stringResource(R.string.question_type_true_false)
                            QuestionType.FILL_BLANK -> stringResource(R.string.question_type_fill_blank)
                            QuestionType.SHORT_ANSWER -> stringResource(R.string.question_type_short_answer)
                        }
                        Text(
                            text = localizedType,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = stringResource(R.string.test_question_progress, currentIndex + 1, questions.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                    val selectedSet = selectedAnswers[currentIndex] ?: emptySet()
                    val isRevealed = isReviewMode || currentIndex in revealedQuestions

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

                        val isSingleChoice = question.type == QuestionType.SINGLE_CHOICE || question.type == QuestionType.TRUE_FALSE
                        val isFillBlank = question.type == QuestionType.FILL_BLANK

                        if (isFillBlank) {
                            // 填空题：点击显示/隐藏答案文本，序号始终可见
                            val revealedOptions = selectedAnswers[currentIndex] ?: emptySet()
                            question.options.forEachIndexed { optionIndex, answer ->
                                val isRevealed = optionIndex in revealedOptions
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(AppShapes.small)
                                        .background(
                                            if (isRevealed)
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else
                                                MaterialTheme.colorScheme.surface
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isRevealed)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outlineVariant,
                                            shape = AppShapes.small
                                        )
                                        .clickable {
                                            viewModel.toggleAnswer(currentIndex, optionIndex)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "(${optionIndex + 1})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = if (isRevealed) answer else "···",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isRevealed) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        } else {
                            // 单选题 / 多选题：可点击选项
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
                                        .border(
                                            width = 1.dp,
                                            color = borderColor,
                                            shape = AppShapes.small
                                        )
                                        .clickable {
                                            if (!isRevealed) {
                                                viewModel.toggleAnswer(currentIndex, optionIndex, isSingleChoice)
                                            }
                                        }
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
                                    val optionLetter = ('A' + optionIndex).toString()
                                    Text(
                                        text = "$optionLetter. $option",
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

                        val showCheckButton = !isFillBlank && !isRevealed && selectedSet.isNotEmpty() &&
                            (!directAnswer || !isSingleChoice)
                        if (showCheckButton) {
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

@Composable
private fun ScoreSummary(
    viewModel: TestViewModel,
    questions: List<QuestionUiModel>,
    selectedAnswers: Map<Int, Set<Int>>,
    modifier: Modifier = Modifier
) {
    val score = remember(questions, selectedAnswers) {
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
                    text = pluralStringResource(R.plurals.test_score_summary, score.total, score.correct, score.total),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.small,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${score.correct}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.test_correct),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.small,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${score.wrong}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(R.string.test_wrong),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = AppShapes.small,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${score.unanswered}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = stringResource(R.string.test_skipped),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
