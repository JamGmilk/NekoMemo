package mirujam.nekomemo.ui.library

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
import mirujam.nekomemo.data.local.entity.QuestionBankEntity
import mirujam.nekomemo.data.repository.QuestionRepository
import mirujam.nekomemo.domain.usecase.BankExportImportUseCase
import javax.inject.Inject

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import mirujam.nekomemo.R
import mirujam.nekomemo.util.FileNameSanitizer

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: QuestionRepository,
    private val bankExportImportUseCase: BankExportImportUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val banks: StateFlow<List<QuestionBankEntity>> = repository.getAllBanks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questionCounts: StateFlow<Map<Long, Int>> = repository.getQuestionCountsByBank()
        .map { list -> list.associate { it.bankId to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _exportJson = MutableStateFlow<String?>(null)
    val exportJson: StateFlow<String?> = _exportJson.asStateFlow()

    private val _exportFileName = MutableStateFlow("")
    val exportFileName: StateFlow<String> = _exportFileName.asStateFlow()

    private val _showDeleteConfirmDialog = MutableStateFlow(false)
    val showDeleteConfirmDialog: StateFlow<Boolean> = _showDeleteConfirmDialog.asStateFlow()

    private val _showEditBankDialog = MutableStateFlow(false)
    val showEditBankDialog: StateFlow<Boolean> = _showEditBankDialog.asStateFlow()

    private var editingBank: QuestionBankEntity? = null

    private var pendingDeleteBank: QuestionBankEntity? = null

    fun deleteBank(bank: QuestionBankEntity) {
        pendingDeleteBank = bank
        _showDeleteConfirmDialog.value = true
    }

    fun confirmDeleteBank() {
        val bank = pendingDeleteBank ?: return
        viewModelScope.launch {
            try {
                repository.deleteBank(bank)
                _snackbarMessage.value = context.getString(R.string.library_delete_success, bank.title)
                android.util.Log.d("LibraryViewModel", "Deleted bank: ${bank.title}")
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Error deleting bank", e)
                _snackbarMessage.value = context.getString(R.string.library_delete_error, e.message ?: "Unknown error")
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

    fun showEditBankDialog(bank: QuestionBankEntity) {
        editingBank = bank
        _showEditBankDialog.value = true
    }

    fun dismissEditBankDialog() {
        _showEditBankDialog.value = false
        editingBank = null
    }

    fun updateEditedBank(title: String, category: String) {
        val bank = editingBank ?: return
        viewModelScope.launch {
            try {
                val updated = bank.copy(title = title, category = category)
                repository.updateBank(updated)
                _snackbarMessage.value = context.getString(R.string.library_edit_success, bank.title)
            } catch (e: Exception) {
                _snackbarMessage.value = context.getString(R.string.library_edit_error, e.message ?: "Unknown error")
            } finally {
                _showEditBankDialog.value = false
                editingBank = null
            }
        }
    }

    fun duplicateBank(bank: QuestionBankEntity) {
        viewModelScope.launch {
            val newId = bankExportImportUseCase.duplicateBank(bank.id)
            _snackbarMessage.value = if (newId > 0) {
                context.getString(R.string.library_duplicate_success)
            } else {
                context.getString(R.string.library_duplicate_failed)
            }
        }
    }

    fun prepareExport(bank: QuestionBankEntity) {
        viewModelScope.launch {
            val json = bankExportImportUseCase.exportBankToJson(bank.id)
            _exportJson.value = json
            _exportFileName.value = "${FileNameSanitizer.sanitize(bank.title)}.nekomemo.json"
        }
    }

    fun clearExportState() {
        _exportJson.value = null
        _exportFileName.value = ""
    }

    fun onExportError(message: String) {
        clearExportState()
        _snackbarMessage.value = message
    }

    fun importBank(jsonString: String) {
        viewModelScope.launch {
            try {
                val bankId = bankExportImportUseCase.importBankFromJson(jsonString)
                _snackbarMessage.value = if (bankId > 0) {
                    context.getString(R.string.library_import_success)
                } else {
                    context.getString(R.string.library_import_failed)
                }
            } catch (e: Exception) {
                _snackbarMessage.value = context.getString(R.string.library_import_error, e.message ?: "Unknown error")
            }
        }
    }

    fun onImportError(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
