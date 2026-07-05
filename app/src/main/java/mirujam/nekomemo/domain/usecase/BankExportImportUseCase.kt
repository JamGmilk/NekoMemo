package mirujam.nekomemo.domain.usecase

import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mirujam.nekomemo.data.repository.CategoryRepository
import mirujam.nekomemo.data.repository.QuestionRepository
import mirujam.nekomemo.domain.model.Question
import mirujam.nekomemo.domain.model.QuestionBank
import mirujam.nekomemo.domain.model.QuestionType
import mirujam.nekomemo.domain.validator.DataValidator
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankExportImportUseCase @Inject constructor(
    private val repository: QuestionRepository,
    private val categoryRepository: CategoryRepository
) {

    companion object {
        private const val FORMAT_VERSION = 1
        private const val KEY_VERSION = "version"
        private const val KEY_NEKOMEMO = "nekomemo"
    }

    suspend fun exportBankToJson(bankId: Long): String? = withContext(Dispatchers.Default) {
        val bank = repository.getBankById(bankId) ?: return@withContext null
        val questions = repository.getQuestionsForBankSync(bankId)

        val categoryName = categoryRepository.getCategoryById(bank.categoryId)?.name
            ?: CategoryRepository.DEFAULT_CATEGORY_NAME

        val json = JSONObject()
        json.put("title", bank.title)
        json.put("category", categoryName)

        val questionsArray = JSONArray()
        questions.forEach { q ->
            val qJson = JSONObject()
            qJson.put("text", q.text)
            qJson.put("options", JSONArray(q.options))
            qJson.put("correctIndices", JSONArray(q.correctIndices))
            qJson.put("type", q.type.name)
            questionsArray.put(qJson)
        }
        json.put("questions", questionsArray)

        val wrapper = JSONObject()
        wrapper.put(KEY_VERSION, FORMAT_VERSION)
        wrapper.put(KEY_NEKOMEMO, json)
        wrapper.toString(2)
    }

    suspend fun importBankFromJson(jsonString: String): Long = withContext(Dispatchers.Default) {
        Timber.d("Starting import, JSON size: ${jsonString.length} bytes")

        if (jsonString.isBlank()) {
            Timber.w("Import failed: Empty JSON string")
            throw IllegalArgumentException("JSON string is empty")
        }

        if (jsonString.length > DataValidator.MAX_JSON_SIZE) {
            Timber.w("Import failed: JSON too large (${jsonString.length} > ${DataValidator.MAX_JSON_SIZE})")
            throw IllegalArgumentException("JSON size exceeds maximum limit of ${DataValidator.MAX_JSON_SIZE / 1024 / 1024}MB")
        }

        val wrapper: JSONObject
        try {
            wrapper = JSONObject(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "Import failed: Invalid JSON format")
            throw IllegalArgumentException("Invalid JSON format: ${e.message}")
        }

        val bankJson = wrapper.optJSONObject(KEY_NEKOMEMO) ?: wrapper

        val version = wrapper.optInt(KEY_VERSION, 0)
        if (version > FORMAT_VERSION) {
            Timber.w("Import warning: file version $version is newer than supported $FORMAT_VERSION, attempting best-effort import")
        }

        val title = DataValidator.validateTitle(bankJson.optString("title", "Imported Bank"))

        if (title.isBlank()) {
            Timber.w("Import failed: Invalid title after sanitization")
            throw IllegalArgumentException("Invalid bank title")
        }

        val categoryId = resolveCategoryId(bankJson)

        Timber.d("Creating bank with title='$title', categoryId=$categoryId")

        val questionsArray = bankJson.optJSONArray("questions")
        if (questionsArray == null) {
            Timber.d("No questions array found, creating empty bank")
            return@withContext repository.createBankWithQuestions(
                QuestionBank(title = title, categoryId = categoryId),
                emptyList()
            )
        }

        if (questionsArray.length() > DataValidator.MAX_QUESTIONS_COUNT) {
            Timber.w("Questions count ${questionsArray.length()} exceeds limit ${DataValidator.MAX_QUESTIONS_COUNT}, truncating")
        }

        val validQuestions = mutableListOf<Question>()
        var skippedCount = 0

        val maxQuestions = minOf(questionsArray.length(), DataValidator.MAX_QUESTIONS_COUNT)

        for (i in 0 until maxQuestions) {
            try {
                val qJson = questionsArray.getJSONObject(i)
                val question = validateAndCreateQuestion(qJson, i)
                if (question != null) {
                    validQuestions.add(question)
                } else {
                    skippedCount++
                }
            } catch (e: Exception) {
                Timber.w("Skipping invalid question at index $i: ${e.message}")
                skippedCount++
            }
        }

        val bankId = repository.createBankWithQuestions(
            QuestionBank(title = title, categoryId = categoryId),
            validQuestions
        )

        Timber.d("Import completed: bankId=$bankId, questions=${validQuestions.size}, skipped=$skippedCount")
        bankId
    }

    private fun validateAndCreateQuestion(qJson: JSONObject, index: Int): Question? {
        val rawText = qJson.optString("text", "")
        val text = DataValidator.sanitizeString(rawText, DataValidator.MAX_TEXT_LENGTH, "")

        if (text.isBlank()) {
            Timber.d("Question $index: Empty or invalid text, skipping")
            return null
        }

        val type = QuestionType.fromLegacyString(
            qJson.optString("type", "SINGLE_CHOICE").ifBlank { "SINGLE_CHOICE" }
        )

        val optionsArray = qJson.optJSONArray("options")
        val options = if (optionsArray != null) {
            parseAndValidateOptions(optionsArray)
        } else {
            emptyList()
        }

        // 填空题和简答题不需要选项
        val requiresOptions = type != QuestionType.FILL_BLANK && type != QuestionType.SHORT_ANSWER
        if (requiresOptions && options.size < DataValidator.MIN_OPTIONS_COUNT) {
            Timber.d("Question $index: Insufficient options (${options.size} < ${DataValidator.MIN_OPTIONS_COUNT}), skipping")
            return null
        }

        // Parse correctIndices - support both new (array) and legacy (single int) formats
        val correctIndices = parseCorrectIndices(qJson, options)

        return Question(
            questionBankId = 0,
            text = text,
            options = options,
            correctIndices = correctIndices,
            type = type
        )
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

    private fun parseAndValidateOptions(optionsArray: JSONArray): List<String> {
        val validatedOptions = mutableListOf<String>()

        val maxOptions = minOf(optionsArray.length(), DataValidator.MAX_OPTIONS_COUNT)

        for (i in 0 until maxOptions) {
            try {
                val option = optionsArray.getString(i)
                val sanitizedOption = DataValidator.sanitizeString(option, DataValidator.MAX_OPTION_LENGTH, "")
                if (sanitizedOption.isNotBlank()) {
                    validatedOptions.add(sanitizedOption)
                }
            } catch (e: Exception) {
                Timber.w(e, "Invalid option at index $i, skipping")
            }
        }

        return validatedOptions
    }

    suspend fun duplicateBank(bankId: Long): Long {
        return repository.duplicateBank(bankId)
    }

    private suspend fun resolveCategoryId(bankJson: JSONObject): Long {
        val rawCategory = bankJson.optString("category", "")
        val categoryName = DataValidator.validateCategory(
            rawCategory.ifBlank { CategoryRepository.DEFAULT_CATEGORY_NAME }
        )

        val existingCategory = categoryRepository.getCategoryByName(categoryName)
        if (existingCategory != null) {
            return existingCategory.id
        }

        return categoryRepository.addCategory(categoryName).getOrElse {
            categoryRepository.getCategoryByName(CategoryRepository.DEFAULT_CATEGORY_NAME)?.id
                ?: throw IllegalStateException("Default category not found")
        }
    }
}
