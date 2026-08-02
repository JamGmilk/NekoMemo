package mirujam.nekomemo.ui.settings

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mirujam.nekomemo.R
import mirujam.nekomemo.domain.model.Category
import mirujam.nekomemo.data.preferences.TestPreferenceRepository
import mirujam.nekomemo.data.preferences.ThemeMode
import mirujam.nekomemo.data.preferences.ThemePreferenceRepository
import mirujam.nekomemo.data.repository.CategoryRepository
import mirujam.nekomemo.data.repository.QuestionRepository
import mirujam.nekomemo.data.repository.QuestionStatsRepository
import mirujam.nekomemo.ui.model.UiText
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

sealed class CategoryOperationResult {
    data class Added(val name: String) : CategoryOperationResult()
    data class Renamed(val name: String) : CategoryOperationResult()
    data class Deleted(val name: String) : CategoryOperationResult()
    data class Error(val message: UiText) : CategoryOperationResult()
}

private fun categoryErrorMessage(@StringRes resId: Int, vararg args: Any): CategoryOperationResult.Error =
    CategoryOperationResult.Error(UiText.StringResource(resId, arrayOf(*args)))

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: QuestionRepository,
    private val categoryRepository: CategoryRepository,
    private val themePreferenceRepository: ThemePreferenceRepository,
    private val testPreferenceRepository: TestPreferenceRepository,
    private val statsRepository: QuestionStatsRepository
) : ViewModel() {

    val bankCount: StateFlow<Int> = repository.getBankCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalQuestionCount: StateFlow<Int> = repository.getTotalQuestionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalAttemptCount: StateFlow<Int> = statsRepository.getTotalAttemptCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalWrongBookCount: StateFlow<Int> = statsRepository.getTotalWrongBookCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalFavoriteCount: StateFlow<Int> = repository.getTotalFavoriteCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val themeMode: StateFlow<ThemeMode> = themePreferenceRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val directAnswer: StateFlow<Boolean> = testPreferenceRepository.directAnswer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoNextOnCorrect: StateFlow<Boolean> = testPreferenceRepository.autoNextOnCorrect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .map { list -> sortCategoriesWithGeneralFirst(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bankCountsByCategory: StateFlow<Map<Long, Int>> = categoryRepository.getBankCountsByCategory()
        .map { list -> list.associate { it.categoryId to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _categoryError = MutableStateFlow<UiText?>(null)
    val categoryError: StateFlow<UiText?> = _categoryError.asStateFlow()

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
            val err = categoryErrorMessage(R.string.settings_category_empty_name)
            _categoryError.value = err.message
            viewModelScope.launch {
                _categoryEvent.send(err)
            }
            return
        }
        viewModelScope.launch {
            val result = categoryRepository.addCategory(trimmedName)
            result.onSuccess {
                _categoryError.value = null
                _categoryEvent.send(CategoryOperationResult.Added(trimmedName))
            }.onFailure { error ->
                val err = error.toCategoryError()
                _categoryError.value = err.message
                _categoryEvent.send(err)
            }
        }
    }

    fun renameCategory(categoryId: Long, newName: String) {
        val trimmedNewName = newName.trim()
        if (trimmedNewName.isBlank()) {
            val err = categoryErrorMessage(R.string.settings_category_empty_name)
            _categoryError.value = err.message
            viewModelScope.launch {
                _categoryEvent.send(err)
            }
            return
        }
        viewModelScope.launch {
            val result = categoryRepository.renameCategory(categoryId, trimmedNewName)
            result.onSuccess {
                _categoryError.value = null
                _categoryEvent.send(CategoryOperationResult.Renamed(trimmedNewName))
            }.onFailure { error ->
                val err = error.toCategoryError()
                _categoryError.value = err.message
                _categoryEvent.send(err)
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
                val err = error.toCategoryError()
                _categoryError.value = err.message
                _categoryEvent.send(err)
            }
        }
    }

    fun clearCategoryError() {
        _categoryError.value = null
    }

    private fun Throwable.toCategoryError(): CategoryOperationResult.Error {
        val message = this.message.orEmpty()
        @StringRes val resId = when {
            message.contains("default category", ignoreCase = true) -> R.string.settings_delete_category_default_error
            message.contains("existing banks", ignoreCase = true) -> R.string.settings_delete_category_error
            message.contains("reserved", ignoreCase = true) -> R.string.settings_category_reserved_name
            message.contains("already exists", ignoreCase = true) -> R.string.settings_category_name_exists
            message.contains("not found", ignoreCase = true) -> R.string.settings_category_name_exists
            else -> R.string.settings_category_name_exists
        }
        return categoryErrorMessage(resId)
    }

    companion object {
        @SuppressLint("ConstantLocale")
        private val nameCollator: Collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }

        private val nameComparator: Comparator<Category> = Comparator { a, b ->
            nameCollator.compare(a.name, b.name)
        }

        fun sortCategoriesWithGeneralFirst(list: List<Category>): List<Category> {
            val (general, others) = list.partition { it.isDefault }
            return general + others.sortedWith(nameComparator)
        }
    }
}
