package mirujam.nekomemo.ui.detail

import timber.log.Timber
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mirujam.nekomemo.domain.model.Category
import mirujam.nekomemo.data.repository.CategoryRepository
import mirujam.nekomemo.data.repository.QuestionRepository
import mirujam.nekomemo.data.repository.QuestionStatsRepository
import mirujam.nekomemo.domain.model.Question
import mirujam.nekomemo.domain.model.QuestionBank
import mirujam.nekomemo.domain.model.QuestionType
import mirujam.nekomemo.domain.model.TestSession
import mirujam.nekomemo.domain.usecase.BankExportImportUseCase
import mirujam.nekomemo.ui.model.QuestionUiModel
import mirujam.nekomemo.ui.shared.ExportDelegate
import mirujam.nekomemo.ui.shared.ExportState
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class BankDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: QuestionRepository,
    private val categoryRepository: CategoryRepository,
    private val statsRepository: QuestionStatsRepository,
    bankExportImportUseCase: BankExportImportUseCase
) : ViewModel() {

    private val bankId: Long = savedStateHandle["bankId"] ?: -1L
    val bankIdValue: Long get() = bankId

    private val exportDelegate = ExportDelegate(viewModelScope, bankExportImportUseCase)
    val exportState: StateFlow<ExportState> = exportDelegate.exportState

    val currentBank: StateFlow<QuestionBank?> = repository.getBankByIdFlow(bankId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bankTitle: StateFlow<String> = currentBank
        .map { it?.title ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val bankCategoryId: StateFlow<Long> = currentBank
        .map { it?.categoryId ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()

    private val _showAddQuestionDialog = MutableStateFlow(false)
    val showAddQuestionDialog: StateFlow<Boolean> = _showAddQuestionDialog.asStateFlow()

    private val _editingQuestion = MutableStateFlow<Question?>(null)
    val editingQuestion: StateFlow<Question?> = _editingQuestion.asStateFlow()

    private val _showDeleteConfirmDialog = MutableStateFlow(false)
    val showDeleteConfirmDialog: StateFlow<Boolean> = _showDeleteConfirmDialog.asStateFlow()

    private val _showDeleteBankConfirmDialog = MutableStateFlow(false)
    val showDeleteBankConfirmDialog: StateFlow<Boolean> = _showDeleteBankConfirmDialog.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredQuestions: StateFlow<List<QuestionUiModel>?> = _searchQuery
        .debounce { query -> if (query.isBlank()) 0L else 300L }
        .flatMapLatest { query ->
            repository.queryQuestionsForBank(bankId, query).map { list ->
                list.map { QuestionUiModel.fromDomainModel(it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val wrongBookCount: StateFlow<Int> = statsRepository.getWrongBookCountForBank(bankId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favoriteCount: StateFlow<Int> = repository.getFavoriteCountForBank(bankId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val resumableSession: StateFlow<TestSession?> = statsRepository.getSessionFlow(bankId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var pendingDeleteQuestion: Question? = null

    fun deleteQuestion(questionId: Long) {
        viewModelScope.launch {
            val question = repository.getQuestionById(questionId) ?: return@launch
            pendingDeleteQuestion = question
            _showDeleteConfirmDialog.value = true
        }
    }

    fun deleteQuestion(question: QuestionUiModel) {
        pendingDeleteQuestion = Question(
            id = question.id,
            questionBankId = bankId,
            text = question.text,
            options = question.options,
            correctIndices = question.correctIndices,
            type = question.type,
            isFavorite = question.isFavorite
        )
        _showDeleteConfirmDialog.value = true
    }

    fun confirmDeleteQuestion() {
        val question = pendingDeleteQuestion ?: return
        viewModelScope.launch {
            try {
                repository.deleteQuestion(question)
                Timber.d("Deleted question ${question.id}")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting question")
            } finally {
                _showDeleteConfirmDialog.value = false
                pendingDeleteQuestion = null
            }
        }
    }

    fun dismissDeleteConfirmDialog() {
        _showDeleteConfirmDialog.value = false
        pendingDeleteQuestion = null
    }

    fun showDeleteBankDialog() {
        _showDeleteBankConfirmDialog.value = true
    }

    fun dismissDeleteBankDialog() {
        _showDeleteBankConfirmDialog.value = false
    }

    fun confirmDeleteBank(onDeleted: () -> Unit) {
        val bank = currentBank.value ?: return
        viewModelScope.launch {
            try {
                repository.deleteBank(bank)
                Timber.d("Deleted bank ${bank.id}")
                onDeleted()
            } catch (e: Exception) {
                Timber.e(e, "Error deleting bank")
            } finally {
                _showDeleteBankConfirmDialog.value = false
            }
        }
    }

    fun prepareExport() {
        exportDelegate.prepareExport(bankId, currentBank.value?.title ?: "")
    }

    fun clearExportState() {
        exportDelegate.clearExportState()
    }

    fun showEditDialog() {
        _showEditDialog.value = true
    }

    fun dismissEditDialog() {
        _showEditDialog.value = false
    }

    fun updateBank(title: String, categoryId: Long) {
        viewModelScope.launch {
            currentBank.value?.let { bank ->
                val updated = bank.copy(title = title, categoryId = categoryId)
                repository.updateBank(updated)
                _showEditDialog.value = false
            }
        }
    }

    fun showAddQuestionDialog() {
        _showAddQuestionDialog.value = true
    }

    fun dismissAddQuestionDialog() {
        _showAddQuestionDialog.value = false
    }

    fun addQuestion(text: String, options: List<String>, correctIndices: List<Int>, type: QuestionType) {
        viewModelScope.launch {
            val question = Question(
                questionBankId = bankId,
                text = text,
                options = options,
                correctIndices = correctIndices,
                type = type
            )
            repository.insertQuestions(listOf(question))
            _showAddQuestionDialog.value = false
        }
    }

    fun showEditQuestionDialog(questionId: Long) {
        viewModelScope.launch {
            val question = repository.getQuestionById(questionId) ?: return@launch
            _editingQuestion.value = question
        }
    }

    fun dismissEditQuestionDialog() {
        _editingQuestion.value = null
    }

    fun updateQuestion(questionId: Long, text: String, options: List<String>, correctIndices: List<Int>, type: QuestionType) {
        viewModelScope.launch {
            try {
                val existing = repository.getQuestionById(questionId)
                repository.updateQuestion(
                    questionId,
                    bankId,
                    text,
                    options,
                    correctIndices,
                    type,
                    isFavorite = existing?.isFavorite ?: false
                )
                _editingQuestion.value = null
            } catch (e: Exception) {
                Timber.e(e, "Error updating question")
            }
        }
    }

    fun toggleFavorite(questionId: Long, currentlyFavorite: Boolean) {
        viewModelScope.launch {
            repository.setFavorite(questionId, !currentlyFavorite)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
