package mirujam.nekomemo.ui.test

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mirujam.nekomemo.R
import mirujam.nekomemo.data.preferences.TestPreferenceRepository
import mirujam.nekomemo.data.repository.QuestionRepository
import mirujam.nekomemo.data.repository.QuestionStatsRepository
import mirujam.nekomemo.domain.model.PracticeMode
import mirujam.nekomemo.domain.model.QuestionType
import mirujam.nekomemo.domain.model.TestSession
import mirujam.nekomemo.ui.model.AnswerResult
import mirujam.nekomemo.ui.model.QuestionUiModel
import mirujam.nekomemo.ui.model.ScoreModel
import mirujam.nekomemo.ui.model.UiText
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

private const val AUTO_NEXT_DELAY_MS = 300L
private const val QUESTIONS_LOAD_TIMEOUT_MS = 10_000L
private const val SESSION_SAVE_DEBOUNCE_MS = 400L

data class TestUiState(
    val currentIndex: Int = 0,
    val selectedAnswers: Map<Int, Set<Int>> = emptyMap(),
    val textAnswers: Map<Int, List<String>> = emptyMap(),
    val revealedQuestions: Set<Int> = emptySet(),
    val isFinished: Boolean = false,
    val isReviewing: Boolean = false,
    val isLoading: Boolean = true,
    val showResumeDialog: Boolean = false,
    val showAnswerSheet: Boolean = false,
    val bankTitle: UiText = UiText.StringResource(R.string.test_mode_title),
    val questions: List<QuestionUiModel> = emptyList()
)

@OptIn(FlowPreview::class)
@HiltViewModel
class TestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: QuestionRepository,
    private val statsRepository: QuestionStatsRepository,
    testPreferenceRepository: TestPreferenceRepository
) : ViewModel() {

    private val bankId: Long = savedStateHandle["bankId"] ?: -1L
    private val questionCount: Int = savedStateHandle["questionCount"] ?: 0
    private val shuffleQuestions: Boolean = savedStateHandle["shuffleQuestions"] ?: false
    private val shuffleOptions: Boolean = savedStateHandle["shuffleOptions"] ?: false
    private val practiceMode: PracticeMode =
        PracticeMode.fromString(savedStateHandle["practiceMode"])
    private val typesFilter: String = android.net.Uri.decode(savedStateHandle["types"] ?: "") ?: ""
    private val resumeRequested: Boolean = savedStateHandle["resume"] ?: false

    val directAnswer: StateFlow<Boolean> = testPreferenceRepository.directAnswer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoNextOnCorrect: StateFlow<Boolean> = testPreferenceRepository.autoNextOnCorrect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var autoNextJob: Job? = null
    private var pendingSession: TestSession? = null
    private var sessionActive = false

    private val _uiState = MutableStateFlow(TestUiState())
    val uiState: StateFlow<TestUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val bank = repository.getBankById(bankId)
            _uiState.update {
                it.copy(
                    bankTitle = bank?.title?.let(UiText::DynamicString)
                        ?: UiText.StringResource(R.string.test_mode_title)
                )
            }
        }

        viewModelScope.launch {
            try {
                val existingSession = statsRepository.getSession(bankId)
                if (resumeRequested && existingSession != null && existingSession.questionIds.isNotEmpty()) {
                    restoreSession(existingSession)
                } else if (!resumeRequested && existingSession != null && existingSession.questionIds.isNotEmpty()) {
                    pendingSession = existingSession
                    _uiState.update { it.copy(isLoading = false, showResumeDialog = true) }
                } else {
                    startFreshSession()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error preparing test session")
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        viewModelScope.launch {
            _uiState
                .debounce(SESSION_SAVE_DEBOUNCE_MS)
                .collectLatest { state ->
                    if (!sessionActive || state.isLoading || state.isFinished || state.questions.isEmpty()) {
                        return@collectLatest
                    }
                    persistSession(state)
                }
        }
    }

    fun continueSession() {
        val session = pendingSession ?: return
        pendingSession = null
        viewModelScope.launch {
            _uiState.update { it.copy(showResumeDialog = false, isLoading = true) }
            restoreSession(session)
        }
    }

    fun restartSession() {
        pendingSession = null
        viewModelScope.launch {
            _uiState.update { it.copy(showResumeDialog = false, isLoading = true) }
            statsRepository.clearSession(bankId)
            startFreshSession()
        }
    }

    fun toggleAnswer(questionIndex: Int, optionIndex: Int, isSingleChoice: Boolean = false) {
        val shouldReveal = directAnswer.value
        _uiState.update { state ->
            val current = state.selectedAnswers[questionIndex] ?: emptySet()
            val newSet = if (optionIndex in current) {
                current - optionIndex
            } else if (isSingleChoice) {
                setOf(optionIndex)
            } else {
                current + optionIndex
            }
            state.copy(
                selectedAnswers = state.selectedAnswers.toMutableMap().apply {
                    this[questionIndex] = newSet
                }
            )
        }
        if (shouldReveal && isSingleChoice) {
            revealAnswer(questionIndex)
        }
    }

    fun updateTextAnswer(questionIndex: Int, blankIndex: Int, value: String) {
        _uiState.update { state ->
            val question = state.questions.getOrNull(questionIndex) ?: return@update state
            val blankCount = when (question.type) {
                QuestionType.SHORT_ANSWER -> 1
                else -> question.options.size.coerceAtLeast(1)
            }
            val current = state.textAnswers[questionIndex]
                ?: List(blankCount) { "" }
            val updated = current.toMutableList().also {
                while (it.size < blankCount) it.add("")
                if (blankIndex in it.indices) {
                    it[blankIndex] = value
                }
            }
            state.copy(
                textAnswers = state.textAnswers.toMutableMap().apply {
                    this[questionIndex] = updated
                }
            )
        }
    }

    fun revealAnswer(questionIndex: Int) {
        _uiState.update { it.copy(revealedQuestions = it.revealedQuestions + questionIndex) }
        scheduleAutoNextIfCorrect(questionIndex)
    }

    fun goToQuestion(index: Int) {
        autoNextJob?.cancel()
        _uiState.update { state ->
            if (index in state.questions.indices) {
                state.copy(currentIndex = index, showAnswerSheet = false)
            } else {
                state
            }
        }
    }

    fun setAnswerSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showAnswerSheet = visible) }
    }

    private fun scheduleAutoNextIfCorrect(questionIndex: Int) {
        autoNextJob?.cancel()
        if (!autoNextOnCorrect.value) return
        val state = _uiState.value
        val question = state.questions.getOrNull(questionIndex) ?: return
        val result = ScoreModel.evaluateQuestion(
            question,
            state.selectedAnswers[questionIndex],
            state.textAnswers[questionIndex]
        )
        if (result != AnswerResult.CORRECT) return
        autoNextJob = viewModelScope.launch {
            delay(AUTO_NEXT_DELAY_MS.milliseconds)
            nextQuestion(state.questions.size)
        }
    }

    fun nextQuestion(total: Int) {
        autoNextJob?.cancel()
        _uiState.update { state ->
            if (state.currentIndex < total - 1) {
                state.copy(currentIndex = state.currentIndex + 1)
            } else {
                state
            }
        }
    }

    fun previousQuestion() {
        autoNextJob?.cancel()
        _uiState.update { state ->
            if (state.currentIndex > 0) {
                state.copy(currentIndex = state.currentIndex - 1)
            } else {
                state
            }
        }
    }

    fun finishTest() {
        viewModelScope.launch {
            val state = _uiState.value
            recordStats(state)
            statsRepository.clearSession(bankId)
            sessionActive = false
            _uiState.update { it.copy(isFinished = true, showAnswerSheet = false) }
        }
    }

    fun startReview() {
        _uiState.update {
            it.copy(
                isReviewing = true,
                currentIndex = 0,
                revealedQuestions = it.questions.indices.toSet()
            )
        }
    }

    fun exitReview() {
        _uiState.update { it.copy(isReviewing = false) }
    }

    fun resetTest() {
        autoNextJob?.cancel()
        viewModelScope.launch {
            statsRepository.clearSession(bankId)
            startFreshSession()
        }
    }

    fun calculateScore(questions: List<QuestionUiModel>): ScoreModel {
        val state = _uiState.value
        return ScoreModel.calculate(questions, state.selectedAnswers, state.textAnswers)
    }

    fun isQuestionAnswered(index: Int): Boolean {
        val state = _uiState.value
        val question = state.questions.getOrNull(index) ?: return false
        return when (question.type) {
            QuestionType.FILL_BLANK, QuestionType.SHORT_ANSWER ->
                state.textAnswers[index].orEmpty().any { it.isNotBlank() }
            else -> !state.selectedAnswers[index].isNullOrEmpty()
        }
    }

    private suspend fun startFreshSession() {
        sessionActive = false
        try {
            val prepared = withTimeout(QUESTIONS_LOAD_TIMEOUT_MS.milliseconds) {
                withContext(Dispatchers.Default) {
                    val allQuestions = repository.getQuestionsForBankSync(bankId)
                    val wrongIds = statsRepository.getWrongBookQuestionIdsForBank(bankId).first().toSet()
                    TestQuestionSelector.select(
                        questions = allQuestions,
                        practiceMode = practiceMode,
                        typeCodes = TestQuestionSelector.parseTypeCodes(typesFilter),
                        wrongIds = wrongIds,
                        questionCount = questionCount,
                        shuffleQuestions = shuffleQuestions,
                        shuffleOptions = shuffleOptions
                    )
                }
            }
            sessionActive = prepared.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isFinished = false,
                    isReviewing = false,
                    currentIndex = 0,
                    selectedAnswers = emptyMap(),
                    textAnswers = emptyMap(),
                    revealedQuestions = emptySet(),
                    questions = prepared,
                    showResumeDialog = false
                )
            }
            if (prepared.isNotEmpty()) {
                persistSession(_uiState.value)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading questions for test")
            _uiState.update { it.copy(isLoading = false, questions = emptyList()) }
        }
    }

    private suspend fun restoreSession(session: TestSession) {
        try {
            val questionsById = repository.getQuestionsForBankSync(bankId).associateBy { it.id }
            val models = session.questionIds.mapNotNull { id ->
                questionsById[id]?.let { QuestionUiModel.fromDomainModel(it) }
            }
            if (models.isEmpty()) {
                statsRepository.clearSession(bankId)
                startFreshSession()
                return
            }
            sessionActive = true
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isFinished = false,
                    isReviewing = false,
                    currentIndex = session.currentIndex.coerceIn(0, models.lastIndex),
                    selectedAnswers = session.selectedAnswers,
                    textAnswers = session.textAnswers,
                    revealedQuestions = session.revealedQuestions,
                    questions = models,
                    showResumeDialog = false
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error restoring test session")
            startFreshSession()
        }
    }

    private suspend fun persistSession(state: TestUiState) {
        if (bankId <= 0 || state.questions.isEmpty()) return
        statsRepository.saveSession(
            TestSession(
                bankId = bankId,
                questionIds = state.questions.map { it.id },
                currentIndex = state.currentIndex,
                selectedAnswers = state.selectedAnswers,
                textAnswers = state.textAnswers,
                revealedQuestions = state.revealedQuestions,
                shuffleQuestions = shuffleQuestions,
                shuffleOptions = shuffleOptions,
                practiceMode = practiceMode,
                typesFilter = typesFilter
            )
        )
    }

    private suspend fun recordStats(state: TestUiState) {
        val removeOnCorrect = practiceMode == PracticeMode.WRONG
        state.questions.forEachIndexed { index, question ->
            val result = ScoreModel.evaluateQuestion(
                question,
                state.selectedAnswers[index],
                state.textAnswers[index]
            )
            when (result) {
                AnswerResult.CORRECT ->
                    statsRepository.recordAttempt(question.id, isCorrect = true, removeFromWrongBookOnCorrect = removeOnCorrect)
                AnswerResult.WRONG ->
                    statsRepository.recordAttempt(question.id, isCorrect = false, removeFromWrongBookOnCorrect = false)
                AnswerResult.UNANSWERED -> Unit
            }
        }
    }
}
