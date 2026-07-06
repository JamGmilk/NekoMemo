package mirujam.nekomemo.ui.fetcher

import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mirujam.nekomemo.R
import mirujam.nekomemo.domain.usecase.HtmlParserUseCase
import mirujam.nekomemo.ui.model.UiText
import mirujam.nekomemo.domain.model.ExtractedQuestionBankSerializer
import mirujam.nekomemo.ui.shared.SharedDataStore
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class FetcherViewModel @Inject constructor(
    private val sharedDataStore: SharedDataStore,
    private val htmlParserUseCase: HtmlParserUseCase
) : ViewModel() {

    companion object {
        private const val PARSE_TIMEOUT_MS = 30_000L
        
        const val MAX_HTML_SIZE = 2 * 1024 * 1024
        const val MAX_RESULT_JSON_SIZE = 5 * 1024 * 1024
        const val WARNING_HTML_SIZE = 500_000
    }

    private val _uiState = MutableStateFlow(FetcherUiState())

    val uiState: StateFlow<FetcherUiState> = _uiState.asStateFlow()

    private var parseJob: Job? = null

    fun setCurrentUrl(url: String) {
        _uiState.value = _uiState.value.copy(currentUrl = url, urlInput = url)
    }

    fun parseHtml(html: String) {
        if (html.isBlank()) {
            Timber.w("parseHtml: HTML is blank")
            _uiState.value = _uiState.value.copy(
                parseResult = UiText.StringResource(R.string.fetcher_error_empty_html),
                isParsing = false
            )
            return
        }
        
        val htmlSize = html.length
        when {
            htmlSize > MAX_HTML_SIZE -> {
                val sizeInMB = htmlSize / 1024.0 / 1024.0
                Timber.e("parseHtml: HTML too large ($htmlSize chars > ${MAX_HTML_SIZE / 1024 / 1024}MB), rejecting")
                _uiState.value = _uiState.value.copy(
                    parseResult = UiText.StringResource(R.string.fetcher_error_page_too_large, arrayOf(sizeInMB)),
                    isParsing = false
                )
                return
            }
            htmlSize > WARNING_HTML_SIZE -> {
                Timber.w("parseHtml: Large HTML detected ($htmlSize chars), will optimize")
            }
            else -> {
                Timber.d("parseHtml: Parsing HTML of size $htmlSize chars")
            }
        }

        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isParsing = true, parseResult = null)
            
            try {
                val safeHtml = if (htmlSize > WARNING_HTML_SIZE) {
                    Timber.d("parseHtml: Truncating HTML from $htmlSize to $MAX_HTML_SIZE for safety")
                    html.take(MAX_HTML_SIZE)
                } else {
                    html
                }
                
                val result = withTimeoutOrNull(PARSE_TIMEOUT_MS.milliseconds) {
                    withContext(Dispatchers.Default) {
                        htmlParserUseCase.parse(safeHtml)
                    }
                } ?: run {
                    Timber.e("Parsing timed out after ${PARSE_TIMEOUT_MS}ms")
                    _uiState.value = _uiState.value.copy(
                        parseResult = UiText.StringResource(R.string.fetcher_error_timeout),
                        isParsing = false
                    )
                    return@launch
                }

                if (result.questions.isEmpty()) {
                    Timber.w("No questions found in parsed result!")
                    _uiState.value = _uiState.value.copy(
                        parseResult = UiText.StringResource(R.string.fetcher_error_no_questions),
                        isParsing = false
                    )
                } else {
                    var limitedResult = result
                    var jsonString = ExtractedQuestionBankSerializer.toJson(result)
                    val jsonSize = jsonString.length
                    
                    if (jsonSize > MAX_RESULT_JSON_SIZE) {
                        Timber.w("parseHtml: JSON result too large ($jsonSize chars > ${MAX_RESULT_JSON_SIZE / 1024 / 1024}MB), reducing questions")
                        var questions = result.questions
                        while (questions.isNotEmpty()) {
                            questions = questions.dropLast((questions.size * 0.5).toInt().coerceAtLeast(1))
                            limitedResult = result.copy(questions = questions)
                            jsonString = ExtractedQuestionBankSerializer.toJson(limitedResult)
                            if (jsonString.length <= MAX_RESULT_JSON_SIZE) break
                        }
                        if (jsonString.length > MAX_RESULT_JSON_SIZE) {
                            Timber.e("parseHtml: Even after reducing questions, JSON still too large, creating minimal structure")
                            jsonString = createMinimalJson(result.questions.size)
                        } else {
                            Timber.w("parseHtml: Reduced questions from ${result.questions.size} to ${questions.size} to fit size limit")
                        }
                    }
                    
                    Timber.d("Parse success! Questions: ${limitedResult.questions.size}, JSON size: ${jsonString.length} chars")
                    
                    _uiState.value = _uiState.value.copy(
                        extractedJson = jsonString,
                        navigateToExtract = true,
                        isParsing = false
                    )
                    
                    logMemoryUsage()
                }
            } catch (e: OutOfMemoryError) {
                Timber.e(e, "parseHtml: OOM error during parsing!")
                _uiState.value = _uiState.value.copy(
                    parseResult = UiText.StringResource(R.string.fetcher_error_oom),
                    isParsing = false
                )
                
                releaseMemory()
                
            } catch (e: Exception) {
                Timber.e(e, "Error parsing HTML")
                _uiState.value = _uiState.value.copy(
                    parseResult = UiText.StringResource(R.string.fetcher_error_generic, arrayOf(e.message ?: "Unknown error")),
                    isParsing = false
                )
            }
        }
    }

    fun getExtractedJson(): String? {
        val json = _uiState.value.extractedJson
        Timber.d("getExtractedJson() called, returning JSON with length: ${json?.length ?: 0}")
        return json
    }

    fun saveToSharedDataStore(json: String): Boolean {
        sharedDataStore.setExtractedJson(json)
        return true
    }

    fun onNavigatedToExtract() {
        Timber.d("onNavigatedToExtract() called")
        _uiState.value = _uiState.value.copy(navigateToExtract = false)
        
        viewModelScope.launch {
            delay(1000.milliseconds)
            val currentJson = _uiState.value.extractedJson
            if (currentJson != null) {
                Timber.d("onNavigatedToExtract: Clearing extracted JSON (${currentJson.length} chars) to free memory")
                _uiState.value = _uiState.value.copy(extractedJson = null)
                
                releaseMemory()
            }
        }
    }

    private fun createMinimalJson(questionCount: Int): String {
        return try {
            val json = org.json.JSONObject()
            json.put("name", "Partial Result ($questionCount questions)")
            json.put("skippedCount", questionCount)
            json.put("unsupportedTypeCount", 0)
            json.put("questions", org.json.JSONArray())
            json.toString()
        } catch (e: Exception) {
            Timber.w(e, "Failed to create minimal JSON")
            "{\"name\":\"Partial Result\",\"questions\":[]}"
        }
    }
    
    private fun logMemoryUsage() {
        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            val maxMemory = runtime.maxMemory() / 1024 / 1024
            Timber.d("Memory usage: ${usedMemory}MB / ${maxMemory}MB")
            
            if (usedMemory > maxMemory * 0.8) {
                Timber.w("High memory usage detected! Consider clearing data.")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to log memory usage")
        }
    }
    
    fun clearResult() {
        _uiState.value = _uiState.value.copy(parseResult = null)
    }

    fun releaseMemory() {
        Timber.d("releaseMemory() called - clearing extractedJson and large objects")
        _uiState.value = _uiState.value.copy(
            extractedJson = null,
            urlInput = ""
        )
    }

    suspend fun decodeHtml(raw: String?): String = withContext(Dispatchers.Default) {
        htmlParserUseCase.decodeHtmlFromJs(raw)
    }

    suspend fun getAndClearSaveResult(): String? {
        val result = sharedDataStore.getSaveResult()
        sharedDataStore.clearSaveResult()
        return result
    }
}
