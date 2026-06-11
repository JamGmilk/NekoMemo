package mirujam.nekomemo.ui.test

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mirujam.nekomemo.R
import mirujam.nekomemo.domain.model.Question
import mirujam.nekomemo.data.preferences.TestPreferenceRepository
import mirujam.nekomemo.data.repository.QuestionRepository
import mirujam.nekomemo.ui.model.QuestionUiModel
import mirujam.nekomemo.ui.model.ScoreModel
import mirujam.nekomemo.ui.model.UiText
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: QuestionRepository,
    testPreferenceRepository: TestPreferenceRepository
) : ViewModel() {

    private val bankId: Long = savedStateHandle["bankId"] ?: -1L
    private val questionCount: Int = savedStateHandle["questionCount"] ?: 0
    private val shuffleQuestions: Boolean = savedStateHandle["shuffleQuestions"] ?: false
    private val shuffleOptions: Boolean = savedStateHandle["shuffleOptions"] ?: false

    private val questions: StateFlow<List<Question>> = repository.getQuestionsForBank(bankId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questionUiModels: StateFlow<List<QuestionUiModel>> = questions.map { domainQuestions ->
        val models = QuestionUiModel.fromDomainModels(domainQuestions)
        if (shuffleOptions) {
            models.map { model ->
                val shuffledOptions = model.options.shuffled()
                val newCorrectIndices = model.correctIndices.mapNotNull { oldIdx ->
                    if (oldIdx in model.options.indices) {
                        shuffledOptions.indexOf(model.options[oldIdx])
                    } else null
                }
                model.copy(options = shuffledOptions, correctIndices = newCorrectIndices)
            }
        } else {
            models
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val directAnswer: StateFlow<Boolean> = testPreferenceRepository.directAnswer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _shuffledQuestions = MutableStateFlow<List<QuestionUiModel>>(emptyList())

    private val _isShuffled = MutableStateFlow(shuffleQuestions)

    private val _bankTitle = MutableStateFlow<UiText>(UiText.StringResource(R.string.test_mode_title))
    val bankTitle: StateFlow<UiText> = _bankTitle.asStateFlow()

    private val _selectedAnswers = MutableStateFlow(emptyMap<Int, Set<Int>>())
    val selectedAnswers: StateFlow<Map<Int, Set<Int>>> = _selectedAnswers.asStateFlow()

    private val _revealedQuestions = MutableStateFlow(emptySet<Int>())
    val revealedQuestions: StateFlow<Set<Int>> = _revealedQuestions.asStateFlow()

    private val _isFinished = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished.asStateFlow()

    private val _isReviewing = MutableStateFlow(false)
    val isReviewing: StateFlow<Boolean> = _isReviewing.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    init {
        viewModelScope.launch {
            val bank = repository.getBankById(bankId)
            _bankTitle.value = bank?.title?.let { UiText.DynamicString(it) }
                ?: UiText.StringResource(R.string.test_mode_title)
        }

        viewModelScope.launch {
            try {
                val models = questionUiModels.first { it.isNotEmpty() }
                _isLoading.value = false
                if (shuffleQuestions) {
                    _shuffledQuestions.value = models.shuffled()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading questions for test")
                _isLoading.value = false
            }
        }
    }

    val activeQuestions: StateFlow<List<QuestionUiModel>> = combine(
        _shuffledQuestions,
        _isShuffled,
        questionUiModels
    ) { shuffled, isShuffled, models ->
        val source = if (isShuffled && shuffled.isNotEmpty()) {
            shuffled
        } else {
            models
        }
        val count = if (questionCount > 0) questionCount else source.size
        source.take(count)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleAnswer(questionIndex: Int, optionIndex: Int, isSingleChoice: Boolean = false) {
        val shouldReveal = directAnswer.value
        _selectedAnswers.update { map ->
            val current = map[questionIndex] ?: emptySet()
            val newSet = if (optionIndex in current) {
                current - optionIndex
            } else if (isSingleChoice) {
                setOf(optionIndex)
            } else {
                current + optionIndex
            }
            map.toMutableMap().apply { this[questionIndex] = newSet }
        }
        if (shouldReveal && isSingleChoice) {
            revealAnswer(questionIndex)
        }
    }

    fun revealAnswer(questionIndex: Int) {
        _revealedQuestions.update { it.toMutableSet().apply { add(questionIndex) } }
    }

    fun nextQuestion(total: Int) {
        if (_currentIndex.value < total - 1) {
            _currentIndex.value += 1
        }
    }

    fun previousQuestion() {
        if (_currentIndex.value > 0) {
            _currentIndex.value -= 1
        }
    }

    fun finishTest() {
        _isFinished.value = true
    }

    fun startReview() {
        _isReviewing.value = true
        _currentIndex.value = 0
    }

    fun exitReview() {
        _isReviewing.value = false
    }

    fun resetTest() {
        _selectedAnswers.value = emptyMap()
        _revealedQuestions.value = emptySet()
        _currentIndex.value = 0
        _isFinished.value = false
        _isReviewing.value = false
        if (_isShuffled.value) {
            _shuffledQuestions.value = questionUiModels.value.shuffled()
        } else {
            _shuffledQuestions.value = emptyList()
        }
    }

    fun calculateScore(questions: List<QuestionUiModel>): ScoreModel {
        return ScoreModel.calculate(questions, _selectedAnswers.value)
    }
}
