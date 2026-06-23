package mirujam.nekomemo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mirujam.nekomemo.domain.model.Category
import mirujam.nekomemo.data.preferences.TestPreferenceRepository
import mirujam.nekomemo.data.preferences.ThemeMode
import mirujam.nekomemo.data.preferences.ThemePreferenceRepository
import mirujam.nekomemo.data.repository.CategoryRepository
import mirujam.nekomemo.data.repository.QuestionRepository
import javax.inject.Inject

sealed class CategoryOperationResult {
    data class Added(val name: String) : CategoryOperationResult()
    data class Renamed(val name: String) : CategoryOperationResult()
    data class Deleted(val name: String) : CategoryOperationResult()
    data class Error(val message: String) : CategoryOperationResult()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: QuestionRepository,
    private val categoryRepository: CategoryRepository,
    private val themePreferenceRepository: ThemePreferenceRepository,
    private val testPreferenceRepository: TestPreferenceRepository
) : ViewModel() {

    val bankCount: StateFlow<Int> = repository.getBankCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalQuestionCount: StateFlow<Int> = repository.getTotalQuestionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val themeMode: StateFlow<ThemeMode> = themePreferenceRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val directAnswer: StateFlow<Boolean> = testPreferenceRepository.directAnswer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoNextOnCorrect: StateFlow<Boolean> = testPreferenceRepository.autoNextOnCorrect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _categoryError = MutableStateFlow<String?>(null)
    val categoryError: StateFlow<String?> = _categoryError.asStateFlow()

    private val _categoryEvent = Channel<CategoryOperationResult>(Channel.BUFFERED)
    val categoryEvent = _categoryEvent.receiveAsFlow()

    init {
        viewModelScope.launch {
            categoryRepository.ensureDefaultCategory()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferenceRepository.setThemeMode(mode)
        }
    }

    fun setDirectAnswer(enabled: Boolean) {
        viewModelScope.launch {
            testPreferenceRepository.setDirectAnswer(enabled)
        }
    }

    fun setAutoNextOnCorrect(enabled: Boolean) {
        viewModelScope.launch {
            testPreferenceRepository.setAutoNextOnCorrect(enabled)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.deleteAllData()
        }
    }

    fun addCategory(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _categoryError.value = "Category name cannot be empty"
            viewModelScope.launch {
                _categoryEvent.send(CategoryOperationResult.Error("Category name cannot be empty"))
            }
            return
        }
        viewModelScope.launch {
            val result = categoryRepository.addCategory(trimmedName)
            result.onSuccess {
                _categoryError.value = null
                _categoryEvent.send(CategoryOperationResult.Added(trimmedName))
            }.onFailure { error ->
                _categoryError.value = error.message
                _categoryEvent.send(CategoryOperationResult.Error(error.message ?: "Unknown error"))
            }
        }
    }

    fun renameCategory(categoryId: Long, newName: String) {
        val trimmedNewName = newName.trim()
        if (trimmedNewName.isBlank()) {
            _categoryError.value = "Category name cannot be empty"
            viewModelScope.launch {
                _categoryEvent.send(CategoryOperationResult.Error("Category name cannot be empty"))
            }
            return
        }
        viewModelScope.launch {
            val result = categoryRepository.renameCategory(categoryId, trimmedNewName)
            result.onSuccess {
                _categoryError.value = null
                _categoryEvent.send(CategoryOperationResult.Renamed(trimmedNewName))
            }.onFailure { error ->
                _categoryError.value = error.message
                _categoryEvent.send(CategoryOperationResult.Error(error.message ?: "Unknown error"))
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            val category = categoryRepository.getCategoryById(categoryId)
            val oldName = category?.name ?: ""
            val result = categoryRepository.deleteCategory(categoryId)
            result.onSuccess {
                _categoryError.value = null
                _categoryEvent.send(CategoryOperationResult.Deleted(oldName))
            }.onFailure { error ->
                _categoryError.value = error.message
                _categoryEvent.send(CategoryOperationResult.Error(error.message ?: "Unknown error"))
            }
        }
    }

    fun clearCategoryError() {
        _categoryError.value = null
    }
}
