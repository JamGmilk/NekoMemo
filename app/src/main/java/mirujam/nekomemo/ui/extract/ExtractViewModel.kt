package mirujam.nekomemo.ui.extract

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mirujam.nekomemo.R
import mirujam.nekomemo.data.local.entity.CategoryEntity
import mirujam.nekomemo.data.repository.CategoryRepository
import mirujam.nekomemo.data.repository.QuestionRepository
import mirujam.nekomemo.domain.model.ExtractedQuestionBank
import mirujam.nekomemo.domain.model.ExtractedQuestionBankSerializer
import mirujam.nekomemo.domain.model.Question
import mirujam.nekomemo.domain.model.QuestionBank
import mirujam.nekomemo.ui.model.UiText
import mirujam.nekomemo.ui.shared.SharedDataStore
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ExtractViewModel @Inject constructor(
    private val repository: QuestionRepository,
    private val categoryRepository: CategoryRepository,
    private val sharedDataStore: SharedDataStore
) : ViewModel() {

    private val _questionBankFlow = MutableStateFlow<ExtractedQuestionBank?>(null)
    val questionBank: StateFlow<ExtractedQuestionBank?> = _questionBankFlow.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveResult = MutableStateFlow<UiText?>(null)
    val saveResult: StateFlow<UiText?> = _saveResult.asStateFlow()

    private val _isSaveSuccess = MutableStateFlow(false)
    val isSaveSuccess: StateFlow<Boolean> = _isSaveSuccess.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            categoryRepository.ensureDefaultCategory()
        }
    }

    fun initFromJson(jsonData: String?) {
        Timber.d("initFromJson() called with data length: ${jsonData?.length ?: 0}")
        if (jsonData != null) {
            val parsed = ExtractedQuestionBankSerializer.fromJson(jsonData)
            Timber.d("Parsed question bank: name='${parsed?.name}', questions=${parsed?.questions?.size ?: 0}")
            _questionBankFlow.value = parsed
        } else {
            Timber.w("initFromJson() called with null jsonData!")
        }
    }

    fun saveQuestions(bankTitle: String, categoryId: Long) {
        val bank = _questionBankFlow.value
        if (bank == null) {
            Timber.w("saveQuestions() called but questionBank is null!")
            return
        }

        Timber.d("Saving ${bank.questions.size} questions with title='$bankTitle'")
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val bankId = repository.insertBank(
                    QuestionBank(
                        title = bankTitle,
                        categoryId = categoryId
                    )
                )
                val questions = bank.questions.map { q ->
                    Question(
                        questionBankId = bankId,
                        text = q.content,
                        options = q.options,
                        correctIndices = q.correctIndices
                    )
                }
                repository.insertQuestions(questions)
                Timber.d("Successfully saved ${questions.size} questions")
                _saveResult.value = UiText.PluralStringResource(R.plurals.extract_save_success, questions.size, arrayOf(questions.size))
                _isSaveSuccess.value = true
            } catch (e: Exception) {
                Timber.e(e, "Error saving questions: ${e.message}")
                _saveResult.value = UiText.StringResource(
                    R.string.extract_save_error,
                    arrayOf(e.message ?: "")
                )
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun onNavigatedBack() {
        _isSaveSuccess.value = false
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    suspend fun loadFromSharedDataStore(): String? {
        return sharedDataStore.getExtractedJson()
    }

    suspend fun clearSharedDataStore(): Boolean {
        return sharedDataStore.clearExtractedJson()
    }
}
