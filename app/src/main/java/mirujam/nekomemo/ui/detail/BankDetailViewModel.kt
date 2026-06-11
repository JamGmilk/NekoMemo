package mirujam.nekomemo.ui.detail

import timber.log.Timber
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mirujam.nekomemo.data.local.entity.CategoryEntity
import mirujam.nekomemo.data.repository.CategoryRepository
import mirujam.nekomemo.data.repository.QuestionRepository
import mirujam.nekomemo.domain.model.Question
import mirujam.nekomemo.domain.model.QuestionBank
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
    bankExportImportUseCase: BankExportImportUseCase
) : ViewModel() {

    private val bankId: Long = savedStateHandle["bankId"] ?: -1L
    val bankIdValue: Long get() = bankId

    private val exportDelegate = ExportDelegate(viewModelScope, bankExportImportUseCase)
    val exportState: StateFlow<ExportState> = exportDelegate.exportState

    val pagedQuestions: Flow<PagingData<QuestionUiModel>> = repository.getPagedQuestionsForBank(bankId)
        .map { pagingData -> pagingData.map { QuestionUiModel.fromDomainModel(it) } }
        .cachedIn(viewModelScope)

    private val _questionCount = MutableStateFlow(0)
    val questionCount: StateFlow<Int> = _questionCount.asStateFlow()

    private val _bankTitle = MutableStateFlow("")
    val bankTitle: StateFlow<String> = _bankTitle.asStateFlow()

    private val _bankCategoryId = MutableStateFlow(0L)
    val bankCategoryId: StateFlow<Long> = _bankCategoryId.asStateFlow()

    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()

    private val _showAddQuestionDialog = MutableStateFlow(false)
    val showAddQuestionDialog: StateFlow<Boolean> = _showAddQuestionDialog.asStateFlow()

    private val _editingQuestionId = MutableStateFlow<Long?>(null)
    val editingQuestionId: StateFlow<Long?> = _editingQuestionId.asStateFlow()

    private val _editingQuestion = MutableStateFlow<Question?>(null)
    val editingQuestion: StateFlow<Question?> = _editingQuestion.asStateFlow()

    private val _showDeleteConfirmDialog = MutableStateFlow(false)
    val showDeleteConfirmDialog: StateFlow<Boolean> = _showDeleteConfirmDialog.asStateFlow()

    private val _showDeleteBankConfirmDialog = MutableStateFlow(false)
    val showDeleteBankConfirmDialog: StateFlow<Boolean> = _showDeleteBankConfirmDialog.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredQuestions: StateFlow<List<QuestionUiModel>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                repository.searchQuestionsForBank(bankId, query).map { list ->
                    list.map { QuestionUiModel.fromDomainModel(it) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var pendingDeleteQuestion: Question? = null

    private val _currentBank = MutableStateFlow<QuestionBank?>(null)

    init {
        viewModelScope.launch {
            val bank = repository.getBankById(bankId)
            bank?.let {
                _currentBank.value = it
                _bankTitle.value = it.title
                _bankCategoryId.value = it.categoryId
            }
        }
        viewModelScope.launch {
            repository.getQuestionCountForBank(bankId).collect { count ->
                _questionCount.value = count
            }
        }
    }

    fun deleteQuestion(questionId: Long) {
        viewModelScope.launch {
            val question = repository.getQuestionById(questionId) ?: return@launch
            pendingDeleteQuestion = question
            _showDeleteConfirmDialog.value = true
        }
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

    fun confirmDeleteBank() {
        val bank = _currentBank.value ?: return
        viewModelScope.launch {
            try {
                repository.deleteBank(bank)
            } catch (e: Exception) {
                Timber.e(e, "Error deleting bank")
            } finally {
                _showDeleteBankConfirmDialog.value = false
            }
        }
    }

    fun prepareExport() {
        exportDelegate.prepareExport(bankId, _bankTitle.value)
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
            _currentBank.value?.let { bank ->
                val updated = bank.copy(title = title, categoryId = categoryId)
                repository.updateBank(updated)
                _bankTitle.value = title
                _bankCategoryId.value = categoryId
                _showEditDialog.value = false
                _currentBank.value = updated
            }
        }
    }

    fun showAddQuestionDialog() {
        _showAddQuestionDialog.value = true
    }

    fun dismissAddQuestionDialog() {
        _showAddQuestionDialog.value = false
    }

    fun addQuestion(text: String, options: List<String>, correctIndices: List<Int>) {
        viewModelScope.launch {
            val question = Question(
                questionBankId = bankId,
                text = text,
                options = options,
                correctIndices = correctIndices
            )
            repository.insertQuestions(listOf(question))
            _showAddQuestionDialog.value = false
        }
    }

    fun showEditQuestionDialog(questionId: Long) {
        viewModelScope.launch {
            val question = repository.getQuestionById(questionId) ?: return@launch
            _editingQuestion.value = question
            _editingQuestionId.value = questionId
        }
    }

    fun dismissEditQuestionDialog() {
        _editingQuestionId.value = null
        _editingQuestion.value = null
    }

    fun updateQuestion(questionId: Long, text: String, options: List<String>, correctIndices: List<Int>) {
        viewModelScope.launch {
            try {
                repository.updateQuestion(questionId, bankId, text, options, correctIndices)
                _editingQuestionId.value = null
                _editingQuestion.value = null
            } catch (e: Exception) {
                Timber.e(e, "Error updating question")
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
