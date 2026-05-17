package mirujam.nekomemo.domain.usecase

import android.util.Log
import mirujam.nekomemo.data.local.Converters
import mirujam.nekomemo.data.local.entity.QuestionBankEntity
import mirujam.nekomemo.data.local.entity.QuestionEntity
import mirujam.nekomemo.data.local.dao.QuestionBankDao
import mirujam.nekomemo.data.local.dao.QuestionDao
import mirujam.nekomemo.domain.validator.DataValidator
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankExportImportUseCase @Inject constructor(
    private val questionBankDao: QuestionBankDao,
    private val questionDao: QuestionDao,
    private val converters: Converters
) {

    companion object {
        private const val TAG = "BankExportImport"
    }

    suspend fun exportBankToJson(bankId: Long): String? {
        val bank = questionBankDao.getBankById(bankId) ?: return null
        val questions = questionDao.getQuestionsForBankSync(bankId)

        val json = JSONObject()
        json.put("title", bank.title)
        json.put("category", bank.category)

        val questionsArray = JSONArray()
        questions.forEach { q ->
            val qJson = JSONObject()
            qJson.put("text", q.text)
            qJson.put("options", JSONArray(converters.toStringList(q.options)))
            qJson.put("correctIndex", q.correctIndex)
            questionsArray.put(qJson)
        }
        json.put("questions", questionsArray)

        val wrapper = JSONObject()
        wrapper.put("nekomemo", json)
        return wrapper.toString(2)
    }

    suspend fun importBankFromJson(jsonString: String): Long {
        Log.d(TAG, "Starting import, JSON size: ${jsonString.length} bytes")

        if (jsonString.isBlank()) {
            Log.w(TAG, "Import failed: Empty JSON string")
            throw IllegalArgumentException("JSON string is empty")
        }

        if (jsonString.length > DataValidator.MAX_JSON_SIZE) {
            Log.w(TAG, "Import failed: JSON too large (${jsonString.length} > ${DataValidator.MAX_JSON_SIZE})")
            throw IllegalArgumentException("JSON size exceeds maximum limit of ${DataValidator.MAX_JSON_SIZE / 1024 / 1024}MB")
        }

        val wrapper: JSONObject
        try {
            wrapper = JSONObject(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: Invalid JSON format", e)
            throw IllegalArgumentException("Invalid JSON format: ${e.message}")
        }

        val bankJson = wrapper.optJSONObject("nekomemo") ?: wrapper

        val title = DataValidator.validateTitle(
            bankJson.optString("title").ifBlank {
                bankJson.optString("name", "Imported Bank")
            }
        )
        val category = DataValidator.validateCategory(bankJson.optString("category", "General"))

        if (title.isBlank()) {
            Log.w(TAG, "Import failed: Invalid title after sanitization")
            throw IllegalArgumentException("Invalid bank title")
        }

        Log.d(TAG, "Creating bank with title='$title', category='$category'")

        val bankId = questionBankDao.insertBank(
            QuestionBankEntity(title = title, category = category)
        )

        val questionsArray = bankJson.optJSONArray("questions")
        if (questionsArray == null) {
            Log.d(TAG, "No questions array found, returning empty bank with id=$bankId")
            return bankId
        }

        if (questionsArray.length() > DataValidator.MAX_QUESTIONS_COUNT) {
            Log.w(TAG, "Questions count ${questionsArray.length()} exceeds limit ${DataValidator.MAX_QUESTIONS_COUNT}, truncating")
        }

        val validEntities = mutableListOf<QuestionEntity>()
        var skippedCount = 0

        val maxQuestions = minOf(questionsArray.length(), DataValidator.MAX_QUESTIONS_COUNT)

        for (i in 0 until maxQuestions) {
            try {
                val qJson = questionsArray.getJSONObject(i)
                val entity = validateAndCreateQuestion(qJson, bankId, i)
                if (entity != null) {
                    validEntities.add(entity)
                } else {
                    skippedCount++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping invalid question at index $i: ${e.message}")
                skippedCount++
            }
        }

        if (validEntities.isNotEmpty()) {
            Log.d(TAG, "Inserting ${validEntities.size} valid questions ($skippedCount skipped)")
            questionDao.insertAll(validEntities)
        } else if (skippedCount > 0) {
            Log.w(TAG, "All $skippedCount questions were invalid, created empty bank")
        }

        Log.d(TAG, "Import completed: bankId=$bankId, questions=${validEntities.size}, skipped=$skippedCount")
        return bankId
    }

    private fun validateAndCreateQuestion(qJson: JSONObject, bankId: Long, index: Int): QuestionEntity? {
        val rawText = qJson.optString("text").ifBlank {
            qJson.optString("content", "")
        }
        val text = DataValidator.sanitizeString(rawText, DataValidator.MAX_TEXT_LENGTH, "")

        if (text.isBlank()) {
            Log.d(TAG, "Question $index: Empty or invalid text, skipping")
            return null
        }

        val optionsArray = qJson.optJSONArray("options")
        val options = if (optionsArray != null) {
            parseAndValidateOptions(optionsArray)
        } else {
            emptyList()
        }

        if (options.size < DataValidator.MIN_OPTIONS_COUNT) {
            Log.d(TAG, "Question $index: Insufficient options (${options.size} < ${DataValidator.MIN_OPTIONS_COUNT}), skipping")
            return null
        }

        var correctIndex = qJson.optInt("correctIndex", 0)

        correctIndex = DataValidator.validateCorrectIndex(correctIndex, options)

        return QuestionEntity(
            questionBankId = bankId,
            text = text,
            options = converters.fromStringList(options),
            correctIndex = correctIndex
        )
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
                Log.d(TAG, "Invalid option at index $i, skipping")
            }
        }

        return validatedOptions
    }

    suspend fun duplicateBank(bankId: Long): Long {
        val originalBank = questionBankDao.getBankById(bankId) ?: return -1L
        val newBankId = questionBankDao.insertBank(
            originalBank.copy(
                id = 0,
                title = "${originalBank.title} (Copy)",
                createdAt = System.currentTimeMillis()
            )
        )
        val questions = questionDao.getQuestionsForBankSync(bankId)
        if (questions.isNotEmpty()) {
            questionDao.insertAll(questions.map { it.copy(id = 0, questionBankId = newBankId) })
        }
        return newBankId
    }
}
