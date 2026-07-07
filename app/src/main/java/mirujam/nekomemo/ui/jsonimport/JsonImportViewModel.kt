package mirujam.nekomemo.ui.jsonimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mirujam.nekomemo.R
import mirujam.nekomemo.domain.model.ExtractedQuestion
import mirujam.nekomemo.domain.model.ExtractedQuestionBank
import mirujam.nekomemo.domain.model.ExtractedQuestionBankSerializer
import mirujam.nekomemo.domain.model.QuestionType
import mirujam.nekomemo.domain.validator.DataValidator
import mirujam.nekomemo.ui.model.UiText
import mirujam.nekomemo.ui.shared.SharedDataStore
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class JsonImportViewModel @Inject constructor(
    private val sharedDataStore: SharedDataStore
) : ViewModel() {

    /** 文本输入大小上限（2MB），与 Screen 端一致，防止 TextField 渲染 ANR */
    private val maxInputSize = 2 * 1024 * 1024

    data class JsonImportUiState(
        val jsonText: String = "",
        val isParsing: Boolean = false,
        val errorMessage: UiText? = null,
        val snackbarMessage: UiText? = null,
        val navigateToExtract: Boolean = false
    )

    private val _uiState = MutableStateFlow(JsonImportUiState())
    val uiState: StateFlow<JsonImportUiState> = _uiState.asStateFlow()

    fun setJsonText(text: String) {
        // 限制文本大小，防止超大文本注入 TextField 导致渲染 ANR
        val limitedText = if (text.length > maxInputSize) {
            Timber.w("Input text truncated from ${text.length} to $maxInputSize chars")
            text.take(maxInputSize)
        } else {
            text
        }
        _uiState.value = _uiState.value.copy(jsonText = limitedText, errorMessage = null)
    }

    fun showSnackbar(message: UiText) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun onNavigatedToExtract() {
        _uiState.value = _uiState.value.copy(navigateToExtract = false)
    }

    /**
     * 从 SharedDataStore 读取并清除 ExtractScreen 保存成功后的反馈消息。
     * 与 FetcherViewModel 的模式一致：Extract 保存成功后会 onBack() 返回本页面，
     * 本页面通过此方法获取反馈消息并在 Snackbar 中显示。
     */
    suspend fun getAndClearSaveResult(): String? {
        val result = sharedDataStore.getSaveResult()
        if (result != null) {
            sharedDataStore.clearSaveResult()
        }
        return result
    }

    fun parseAndProceed() {
        val jsonString = _uiState.value.jsonText
        if (jsonString.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = UiText.StringResource(R.string.json_import_error_empty)
            )
            return
        }

        if (jsonString.length > maxInputSize) {
            _uiState.value = _uiState.value.copy(
                errorMessage = UiText.StringResource(R.string.json_import_error_text_too_long)
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isParsing = true, errorMessage = null)
            try {
                val normalizedJson = withContext(Dispatchers.Default) {
                    parseAndNormalize(jsonString)
                }
                if (normalizedJson != null) {
                    Timber.d("JSON parsed successfully, storing in SharedDataStore, length=${normalizedJson.length}")
                    sharedDataStore.setExtractedJson(normalizedJson)
                    _uiState.value = _uiState.value.copy(isParsing = false, navigateToExtract = true)
                } else {
                    Timber.w("JSON parsing failed for input length=${jsonString.length}")
                    _uiState.value = _uiState.value.copy(
                        isParsing = false,
                        errorMessage = UiText.StringResource(R.string.json_import_error_invalid)
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing JSON")
                _uiState.value = _uiState.value.copy(
                    isParsing = false,
                    errorMessage = UiText.StringResource(R.string.json_import_error_invalid)
                )
            }
        }
    }

    /**
     * 解析 JSON 并规范化为 ExtractedQuestionBank 的 JSON 格式，存入 SharedDataStore 供 ExtractScreen 使用。
     *
     * 兼容两种格式：
     * 1. 抓取格式（ExtractedQuestionBankSerializer）：{ name, questions: [{ type, content, options, correctAnswer, correctIndices }] }
     * 2. 导出格式（BankExportImportUseCase）：{ version, nekomemo: { title, category, questions: [{ text, options, correctIndices, type }] } }
     */
    private fun parseAndNormalize(jsonString: String): String? {
        // 预处理：去除 BOM、提取首个 { 和最后一个 } 之间的内容，
        // 容错用户误加的前后空白、Markdown 代码围栏（```json ... ```）、注释文字等
        val normalized = extractJsonContent(jsonString)
        Timber.d("extractJsonContent: ${jsonString.length} -> ${normalized.length} chars")

        // 先尝试抓取格式
        val bank = ExtractedQuestionBankSerializer.fromJson(normalized)
        if (bank != null && bank.questions.isNotEmpty()) {
            Timber.d("Parsed as extract format: name='${bank.name}', questions=${bank.questions.size}, category='${bank.category}'")
            return ExtractedQuestionBankSerializer.toJson(bank)
        }

        // 尝试导出格式
        val exportBank = parseExportFormat(normalized)
        if (exportBank != null && exportBank.questions.isNotEmpty()) {
            Timber.d("Parsed as export format: name='${exportBank.name}', questions=${exportBank.questions.size}, category='${exportBank.category}'")
            return ExtractedQuestionBankSerializer.toJson(exportBank)
        }

        Timber.w("Failed to parse JSON in either format")
        return null
    }

    /**
     * 预处理 JSON 文本，提高容错性：
     * 1. 去除 UTF-8 BOM 字符
     * 2. 提取首个 { 和最后一个 } 之间的内容（或 [ ... ]）
     *    - 处理 Markdown 代码围栏：```json\n{...}\n```
     *    - 处理用户误加的前后文字：注释、序号、说明文字等
     *    - 纯空格/换行虽然解析器本身能处理，这里也一并 trim
     *
     * 安全性：JSON 对象的首字符必然是 {，尾字符必然是 }，
     * 字符串内部的大括号不会成为整个文本的第一个/最后一个，因此截取是安全的。
     */
    private fun extractJsonContent(input: String): String {
        if (input.isBlank()) return input

        // 去除 UTF-8 BOM
        var s = input
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1)
        }

        // 查找对象格式 { ... }
        val firstBrace = s.indexOf('{')
        val lastBrace = s.lastIndexOf('}')

        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return s.substring(firstBrace, lastBrace + 1)
        }

        // 查找数组格式 [ ... ]
        val firstBracket = s.indexOf('[')
        val lastBracket = s.lastIndexOf(']')

        if (firstBracket >= 0 && lastBracket > firstBracket) {
            return s.substring(firstBracket, lastBracket + 1)
        }

        // 未找到 JSON 结构，返回 trim 后的内容让解析器报错
        return s.trim()
    }

    private fun parseExportFormat(jsonString: String): ExtractedQuestionBank? {
        return try {
            val root = JSONObject(jsonString)
            // 导出格式有 "nekomemo" 包装，也可能直接是题库对象
            val bankJson = root.optJSONObject("nekomemo") ?: root

            val name = bankJson.optString("title", bankJson.optString("name", "Imported Bank"))
                .ifBlank { "Imported Bank" }

            val category = bankJson.optString("category", "").takeIf { it.isNotBlank() }

            val questionsArray = bankJson.optJSONArray("questions")
                ?: return ExtractedQuestionBank(name, emptyList())

            val validQuestions = mutableListOf<ExtractedQuestion>()
            var skippedCount = 0

            for (i in 0 until questionsArray.length()) {
                try {
                    val qJson = questionsArray.getJSONObject(i)

                    // text（导出格式）或 content（抓取格式）作为 fallback
                    val content = DataValidator.sanitizeContent(
                        qJson.optString("text", qJson.optString("content", ""))
                    )
                    if (content.isBlank()) {
                        skippedCount++
                        continue
                    }

                    val rawOptionsArray = qJson.optJSONArray("options")
                    val rawOptions = if (rawOptionsArray != null) {
                        (0 until rawOptionsArray.length()).mapNotNull { j ->
                            try { rawOptionsArray.getString(j) } catch (e: Exception) { null }
                        }
                    } else {
                        emptyList()
                    }
                    val options = DataValidator.sanitizeOptions(rawOptions)
                    if (options.isEmpty()) {
                        skippedCount++
                        continue
                    }

                    val correctIndices = parseCorrectIndices(qJson, options)
                    val type = QuestionType.fromLegacyString(
                        qJson.optString("type", "SINGLE_CHOICE").ifBlank { "SINGLE_CHOICE" }
                    )

                    validQuestions.add(
                        ExtractedQuestion(
                            type = type,
                            content = content,
                            options = options,
                            correctAnswer = qJson.optString("correctAnswer", ""),
                            correctIndices = correctIndices
                        )
                    )
                } catch (e: Exception) {
                    Timber.w(e, "parseExportFormat: Error parsing question at index $i")
                    skippedCount++
                }
            }

            ExtractedQuestionBank(name, validQuestions, category = category, skippedCount = skippedCount)
        } catch (e: Exception) {
            Timber.e(e, "parseExportFormat: Failed to parse as export format")
            null
        }
    }

    private fun parseCorrectIndices(qJson: JSONObject, options: List<String>): List<Int> {
        val indicesArray = qJson.optJSONArray("correctIndices")
        if (indicesArray != null && indicesArray.length() > 0) {
            val indices = (0 until indicesArray.length()).mapNotNull { i ->
                try { indicesArray.getInt(i) } catch (e: Exception) { null }
            }
            return DataValidator.validateCorrectIndices(indices, options)
        }

        val legacyIndex = qJson.optInt("correctIndex", -1)
        if (legacyIndex >= 0) {
            return DataValidator.validateCorrectIndices(listOf(legacyIndex), options)
        }

        return listOf(0)
    }
}
