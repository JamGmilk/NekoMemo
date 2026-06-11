package mirujam.nekomemo.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import mirujam.nekomemo.R
import mirujam.nekomemo.domain.model.Category
import mirujam.nekomemo.domain.model.QuestionBank
import mirujam.nekomemo.data.repository.CategoryRepository
import mirujam.nekomemo.data.repository.QuestionRepository
import mirujam.nekomemo.domain.usecase.BankExportImportUseCase
import mirujam.nekomemo.ui.model.UiText
import mirujam.nekomemo.ui.shared.ExportDelegate
import mirujam.nekomemo.ui.shared.ExportState
import timber.log.Timber
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: QuestionRepository,
    private val categoryRepository: CategoryRepository,
    private val bankExportImportUseCase: BankExportImportUseCase
) : ViewModel() {

    private val exportDelegate = ExportDelegate(viewModelScope, bankExportImportUseCase)
    val exportState: StateFlow<ExportState> = exportDelegate.exportState

    val banks: StateFlow<List<QuestionBank>> = repository.getAllBanks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val categoryMap: StateFlow<Map<Long, String>> = categories.map { list ->
        list.associate { it.id to it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE_DESC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    val filteredBanks: StateFlow<List<QuestionBank>> = combine(
        banks, _searchQuery.debounce(300), _sortMode, categoryMap
    ) { bankList, query, sort, catMap ->
        val filtered = if (query.isBlank()) bankList
        else bankList.filter { bank ->
            bank.title.contains(query, ignoreCase = true) ||
            catMap[bank.categoryId]?.contains(query, ignoreCase = true) == true
        }
        when (sort) {
            SortMode.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
            SortMode.DATE_ASC -> filtered.sortedBy { it.createdAt }
            SortMode.TITLE_ASC -> filtered.sortedWith(Collator.getInstance(Locale.getDefault()))
            SortMode.TITLE_DESC -> filtered.sortedWith(Collator.getInstance(Locale.getDefault())).reversed()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questionCounts: StateFlow<Map<Long, Int>> = repository.getQuestionCountsByBank()
        .map { list -> list.associate { it.bankId to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _snackbarMessage = MutableStateFlow<UiText?>(null)
    val snackbarMessage: StateFlow<UiText?> = _snackbarMessage.asStateFlow()

    private val _showDeleteConfirmDialog = MutableStateFlow(false)
    val showDeleteConfirmDialog: StateFlow<Boolean> = _showDeleteConfirmDialog.asStateFlow()

    private val _showEditBankDialog = MutableStateFlow(false)
    val showEditBankDialog: StateFlow<Boolean> = _showEditBankDialog.asStateFlow()

    private val _editingBank = MutableStateFlow<QuestionBank?>(null)
    val editingBank: StateFlow<QuestionBank?> = _editingBank.asStateFlow()

    private var pendingDeleteBank: QuestionBank? = null

    init {
        viewModelScope.launch {
            categoryRepository.ensureDefaultCategory()
        }
    }

    fun deleteBank(bank: QuestionBank) {
        pendingDeleteBank = bank
        _showDeleteConfirmDialog.value = true
    }

    fun confirmDeleteBank() {
        val bank = pendingDeleteBank ?: return
        viewModelScope.launch {
            try {
                repository.deleteBank(bank)
                _snackbarMessage.value = UiText.StringResource(R.string.library_delete_success, arrayOf(bank.title))
                Timber.d("Deleted bank: ${bank.title}")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting bank")
                _snackbarMessage.value = UiText.StringResource(R.string.library_delete_error, arrayOf(e.message ?: "Unknown error"))
            } finally {
                _showDeleteConfirmDialog.value = false
                pendingDeleteBank = null
            }
        }
    }

    fun dismissDeleteConfirmDialog() {
        _showDeleteConfirmDialog.value = false
        pendingDeleteBank = null
    }

    fun showEditBankDialog(bank: QuestionBank) {
        _editingBank.value = bank
        _showEditBankDialog.value = true
    }

    fun dismissEditBankDialog() {
        _showEditBankDialog.value = false
        _editingBank.value = null
    }

    fun updateEditedBank(title: String, categoryId: Long) {
        val bank = _editingBank.value ?: return
        viewModelScope.launch {
            try {
                val updated = bank.copy(title = title, categoryId = categoryId)
                repository.updateBank(updated)
                _snackbarMessage.value = UiText.StringResource(R.string.library_edit_success, arrayOf(bank.title))
            } catch (e: Exception) {
                _snackbarMessage.value = UiText.StringResource(R.string.library_edit_error, arrayOf(e.message ?: "Unknown error"))
            } finally {
                _showEditBankDialog.value = false
                _editingBank.value = null
            }
        }
    }

    fun duplicateBank(bank: QuestionBank) {
        viewModelScope.launch {
            val newId = bankExportImportUseCase.duplicateBank(bank.id)
            _snackbarMessage.value = if (newId > 0) {
                UiText.StringResource(R.string.library_duplicate_success)
            } else {
                UiText.StringResource(R.string.library_duplicate_failed)
            }
        }
    }

    fun prepareExport(bank: QuestionBank) {
        exportDelegate.prepareExport(bank.id, bank.title)
    }

    fun clearExportState() {
        exportDelegate.clearExportState()
    }

    fun onExportError(message: String) {
        clearExportState()
        _snackbarMessage.value = UiText.DynamicString(message)
    }

    fun importBank(jsonString: String) {
        viewModelScope.launch {
            try {
                val bankId = bankExportImportUseCase.importBankFromJson(jsonString)
                _snackbarMessage.value = if (bankId > 0) {
                    UiText.StringResource(R.string.library_import_success)
                } else {
                    UiText.StringResource(R.string.library_import_failed)
                }
            } catch (e: Exception) {
                _snackbarMessage.value = UiText.StringResource(R.string.library_import_error, arrayOf(e.message ?: "Unknown error"))
            }
        }
    }

    fun onImportError(message: String) {
        _snackbarMessage.value = UiText.DynamicString(message)
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }
}
