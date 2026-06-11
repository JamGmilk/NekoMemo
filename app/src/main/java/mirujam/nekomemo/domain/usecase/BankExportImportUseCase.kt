package mirujam.nekomemo.domain.usecase

import timber.log.Timber
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

    suspend fun exportBankToJson(bankId: Long): String? {
        val bank = repository.getBankById(bankId) ?: return null
        val questions = repository.getQuestionsForBankSync(bankId)

        val json = JSONObject()
        json.put("title", bank.title)
        json.put("categoryId", bank.categoryId)

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
        return wrapper.toString(2)
    }

    suspend fun importBankFromJson(jsonString: String): Long {
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

        val bankId = repository.insertBank(
            QuestionBank(title = title, categoryId = categoryId)
        )

        val questionsArray = bankJson.optJSONArray("questions")
        if (questionsArray == null) {
            Timber.d("No questions array found, returning empty bank with id=$bankId")
            return bankId
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
                val question = validateAndCreateQuestion(qJson, bankId, i)
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

        if (validQuestions.isNotEmpty()) {
            Timber.d("Inserting ${validQuestions.size} valid questions ($skippedCount skipped)")
            repository.insertQuestions(validQuestions)
        } else if (skippedCount > 0) {
            Timber.w("All $skippedCount questions were invalid, created empty bank")
        }

        Timber.d("Import completed: bankId=$bankId, questions=${validQuestions.size}, skipped=$skippedCount")
        return bankId
    }

    private fun validateAndCreateQuestion(qJson: JSONObject, bankId: Long, index: Int): Question? {
        val rawText = qJson.optString("text", "")
        val text = DataValidator.sanitizeString(rawText, DataValidator.MAX_TEXT_LENGTH, "")

        if (text.isBlank()) {
            Timber.d("Question $index: Empty or invalid text, skipping")
            return null
        }

        val optionsArray = qJson.optJSONArray("options")
        val options = if (optionsArray != null) {
            parseAndValidateOptions(optionsArray)
        } else {
            emptyList()
        }

        if (options.size < DataValidator.MIN_OPTIONS_COUNT) {
            Timber.d("Question $index: Insufficient options (${options.size} < ${DataValidator.MIN_OPTIONS_COUNT}), skipping")
            return null
        }

        // Parse correctIndices - support both new (array) and legacy (single int) formats
        val correctIndices = parseCorrectIndices(qJson, options)

        return Question(
            questionBankId = bankId,
            text = text,
            options = options,
            correctIndices = correctIndices,
            type = QuestionType.fromLegacyString(qJson.optString("type", "SINGLE_CHOICE").ifBlank { "SINGLE_CHOICE" })
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
        val categoryId = bankJson.optLong("categoryId", 0L)
        if (categoryId > 0) {
            return categoryId
        }

        val categoryName = DataValidator.validateCategory(bankJson.optString("category", CategoryRepository.DEFAULT_CATEGORY_NAME))
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
