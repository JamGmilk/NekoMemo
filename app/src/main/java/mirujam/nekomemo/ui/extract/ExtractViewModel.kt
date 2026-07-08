package mirujam.nekomemo.ui.extract

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mirujam.nekomemo.R
import mirujam.nekomemo.data.repository.CategoryRepository
import mirujam.nekomemo.data.repository.QuestionRepository
import mirujam.nekomemo.domain.model.Category
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

    /**
     * 从 JSON 导入的分类名称，若与数据库中的分类名不重复则排在列表首位。
     * 为 null 时表示无导入分类名，列表保持原样。
     */
    private val _importedCategoryName = MutableStateFlow<String?>(null)

    /**
     * 建议的分类 ID。Screen 监听此值，当导入分类就绪时自动选中它。
     * 值为 null 表示无需自动选中，由 Screen 的默认逻辑处理。
     */
    private val _suggestedCategoryId = MutableStateFlow<Long?>(null)
    val suggestedCategoryId: StateFlow<Long?> = _suggestedCategoryId.asStateFlow()

    /**
     * 合并后的分类列表：导入分类名（如存在且不重复）排在首位，其余按数据库顺序排列。
     */
    val categories: StateFlow<List<Category>> = combine(
        categoryRepository.getAllCategories(),
        _importedCategoryName
    ) { dbCategories, importedName ->
        reorderCategories(dbCategories, importedName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            categoryRepository.ensureDefaultCategory()
        }
    }

    suspend fun initFromJson(jsonData: String?) {
        Timber.d("initFromJson() called with data length: ${jsonData?.length ?: 0}")
        if (jsonData != null) {
            val parsed = withContext(Dispatchers.Default) {
                ExtractedQuestionBankSerializer.fromJson(jsonData)
            }
            Timber.d("Parsed question bank: name='${parsed?.name}', questions=${parsed?.questions?.size ?: 0}, category='${parsed?.category}'")

            // 提取导入的分类名，用于优先展示
            val importedCategory = parsed?.category?.trim()?.takeIf { it.isNotBlank() }
            _importedCategoryName.value = importedCategory
            if (importedCategory != null) {
                // 查询 DB 中是否已有同名分类，使用真实 ID；否则用虚拟 ID（-1L）
                val existingCategory = categoryRepository.getCategoryByName(importedCategory)
                _suggestedCategoryId.value = existingCategory?.id ?: VIRTUAL_IMPORTED_CATEGORY_ID
            }

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

        Timber.d("Saving ${bank.questions.size} questions with title='$bankTitle', categoryId=$categoryId")
        viewModelScope.launch {
            _isSaving.value = true
            try {
                // 若分类尚不存在于数据库（导入分类），先自动创建
                val resolvedCategoryId = resolveCategoryId(categoryId)

                val questions = bank.questions.map { q ->
                    Question(
                        questionBankId = 0,
                        text = q.content,
                        options = q.options,
                        correctIndices = q.correctIndices,
                        type = q.type
                    )
                }
                val bankId = repository.createBankWithQuestions(
                    QuestionBank(
                        title = bankTitle,
                        categoryId = resolvedCategoryId
                    ),
                    questions
                )
                Timber.d("Successfully saved ${questions.size} questions, bankId=$bankId")
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

    /**
     * 解析分类 ID：若对应分类不在数据库中（虚拟 ID），自动创建后返回真实 ID。
     * 虚拟 ID 使用负数区分，由 reorderCategories 创建。
     */
    private suspend fun resolveCategoryId(categoryId: Long): Long {
        if (categoryId > 0) return categoryId

        // 虚拟 ID：从 _importedCategoryName 获取名称并创建
        val importedName = _importedCategoryName.value
        if (importedName != null && categoryId < 0) {
            Timber.d("Auto-creating imported category: '$importedName'")
            val result = categoryRepository.addCategory(importedName)
            return result.getOrElse {
                Timber.e(it, "Failed to auto-create category, falling back to default")
                // 获取默认分类的 ID
                val defaultCategory = categoryRepository.getCategoryByName(Category.DEFAULT_CATEGORY_NAME)
                return defaultCategory?.id ?: 1L
            }
        }

        return 1L // fallback
    }

    fun onNavigatedBack() {
        _isSaveSuccess.value = false
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    fun loadFromSharedDataStore(): String? {
        return sharedDataStore.getExtractedJson()
    }

    suspend fun setSaveResult(message: String): Boolean {
        return sharedDataStore.setSaveResult(message)
    }

    fun clearSharedDataStore(): Boolean {
        sharedDataStore.clearExtractedJson()
        return true
    }

    companion object {
        /** 虚拟分类 ID，用于标识导入分类（尚未存在于数据库中） */
        private const val VIRTUAL_IMPORTED_CATEGORY_ID = -1L
    }
}

/**
 * 将导入分类名排在列表首位，若已存在于数据库中则移到首位，否则创建一个虚拟分类条目。
 */
private fun reorderCategories(
    dbCategories: List<Category>,
    importedName: String?
): List<Category> {
    if (importedName == null || importedName.isBlank()) return dbCategories

    // 查找数据库是否已有同名分类
    val existing = dbCategories.find { it.name.equals(importedName, ignoreCase = true) }
    if (existing != null) {
        // 已存在：移到首位
        return listOf(existing) + dbCategories.filter { it.id != existing.id }
    }

    // 不存在：创建虚拟分类条目排在首位
    val virtualCategory = Category(
        id = -1L, // 负数 ID 标识为虚拟分类，保存时自动创建
        name = importedName
    )
    return listOf(virtualCategory) + dbCategories
}