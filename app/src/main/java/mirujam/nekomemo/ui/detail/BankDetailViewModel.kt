package mirujam.nekomemo.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mirujam.nekomemo.data.local.Converters
import mirujam.nekomemo.data.local.entity.QuestionBankEntity
import mirujam.nekomemo.data.local.entity.QuestionEntity
import mirujam.nekomemo.data.repository.QuestionRepository
import mirujam.nekomemo.domain.usecase.BankExportImportUseCase
import mirujam.nekomemo.ui.model.CachedQuestion
import mirujam.nekomemo.util.FileNameSanitizer
import javax.inject.Inject

@HiltViewModel
class BankDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: QuestionRepository,
    private val converters: Converters,
    private val bankExportImportUseCase: BankExportImportUseCase
) : ViewModel() {

    private val bankId: Long = savedStateHandle["bankId"] ?: -1L

    val questions: StateFlow<List<QuestionEntity>> = repository.getQuestionsForBank(bankId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedQuestions: StateFlow<List<CachedQuestion>> = questions.map { entityList ->
        android.util.Log.d("BankDetailViewModel", "Caching ${entityList.size} questions (parsing JSON once)")
        entityList.map { entity ->
            CachedQuestion.fromEntity(
                entity = entity,
                optionList = converters.toStringList(entity.options)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _bankTitle = MutableStateFlow("")
    val bankTitle: StateFlow<String> = _bankTitle.asStateFlow()

    private val _bankCategory = MutableStateFlow("")
    val bankCategory: StateFlow<String> = _bankCategory.asStateFlow()

    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()

    private val _showAddQuestionDialog = MutableStateFlow(false)
    val showAddQuestionDialog: StateFlow<Boolean> = _showAddQuestionDialog.asStateFlow()

    private val _editingQuestionId = MutableStateFlow<Long?>(null)
    val editingQuestionId: StateFlow<Long?> = _editingQuestionId.asStateFlow()

    private val _exportJson = MutableStateFlow<String?>(null)
    val exportJson: StateFlow<String?> = _exportJson.asStateFlow()

    private val _exportFileName = MutableStateFlow("")
    val exportFileName: StateFlow<String> = _exportFileName.asStateFlow()

    private val _showDeleteConfirmDialog = MutableStateFlow(false)
    val showDeleteConfirmDialog: StateFlow<Boolean> = _showDeleteConfirmDialog.asStateFlow()

    private val _showDeleteBankConfirmDialog = MutableStateFlow(false)
    val showDeleteBankConfirmDialog: StateFlow<Boolean> = _showDeleteBankConfirmDialog.asStateFlow()

    private var pendingDeleteQuestion: QuestionEntity? = null

    private var currentBank: QuestionBankEntity? = null

    init {
        viewModelScope.launch {
            val bank = repository.getBankById(bankId)
            bank?.let {
                currentBank = it
                _bankTitle.value = it.title
                _bankCategory.value = it.category
            }
        }
    }

    fun toOptionList(optionsJson: String): List<String> {
        return converters.toStringList(optionsJson)
    }

    fun deleteQuestion(question: QuestionEntity) {
        pendingDeleteQuestion = question
        _showDeleteConfirmDialog.value = true
    }

    fun confirmDeleteQuestion() {
        val question = pendingDeleteQuestion ?: return
        viewModelScope.launch {
            try {
                repository.deleteQuestion(question)
                android.util.Log.d("BankDetailViewModel", "Deleted question ${question.id}")
            } catch (e: Exception) {
                android.util.Log.e("BankDetailViewModel", "Error deleting question", e)
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
        val bank = currentBank ?: return
        viewModelScope.launch {
            try {
                repository.deleteBank(bank)
            } catch (e: Exception) {
                android.util.Log.e("BankDetailViewModel", "Error deleting bank", e)
            } finally {
                _showDeleteBankConfirmDialog.value = false
            }
        }
    }

    fun prepareExport() {
        viewModelScope.launch {
            val json = bankExportImportUseCase.exportBankToJson(bankId)
            _exportJson.value = json
            _exportFileName.value = "${FileNameSanitizer.sanitize(_bankTitle.value)}.nekomemo.json"
        }
    }

    fun clearExportState() {
        _exportJson.value = null
        _exportFileName.value = ""
    }

    fun showEditDialog() {
        _showEditDialog.value = true
    }

    fun dismissEditDialog() {
        _showEditDialog.value = false
    }

    fun updateBank(title: String, category: String) {
        viewModelScope.launch {
            currentBank?.let { bank ->
                val updated = bank.copy(title = title, category = category)
                repository.updateBank(updated)
                _bankTitle.value = title
                _bankCategory.value = category
                _showEditDialog.value = false
                currentBank = updated
            }
        }
    }

    fun showAddQuestionDialog() {
        _showAddQuestionDialog.value = true
    }

    fun dismissAddQuestionDialog() {
        _showAddQuestionDialog.value = false
    }

    fun addQuestion(text: String, options: List<String>, correctIndex: Int) {
        viewModelScope.launch {
            val entity = QuestionEntity(
                questionBankId = bankId,
                text = text,
                options = converters.fromStringList(options),
                correctIndex = correctIndex
            )
            repository.insertQuestions(listOf(entity))
            _showAddQuestionDialog.value = false
        }
    }

    fun showEditQuestionDialog(question: QuestionEntity) {
        _editingQuestionId.value = question.id
    }

    fun dismissEditQuestionDialog() {
        _editingQuestionId.value = null
    }

    fun updateQuestion(questionId: Long, text: String, options: List<String>, correctIndex: Int) {
        viewModelScope.launch {
            val existing = repository.getQuestionById(questionId) ?: return@launch
            val updated = existing.copy(
                text = text,
                options = converters.fromStringList(options),
                correctIndex = correctIndex
            )
            
            val success = repository.updateQuestionWithVersionCheck(
                id = questionId,
                text = text,
                options = converters.fromStringList(options),
                correctIndex = correctIndex,
                expectedVersion = existing.version
            )
            
            if (success) {
                _editingQuestionId.value = null
            } else {
                android.util.Log.w("BankDetailViewModel", "Update failed: version conflict for question $questionId")
            }
        }
    }
}
