package mirujam.nekomemo.domain.model

import timber.log.Timber
import mirujam.nekomemo.domain.validator.DataValidator

object ExtractedQuestionBankSerializer {

    fun toJson(bank: ExtractedQuestionBank): String {
        return try {
            val json = org.json.JSONObject()

            json.put("name", bank.name.ifBlank { "Untitled Bank" })
            json.put("skippedCount", bank.skippedCount.coerceAtLeast(0))
            json.put("unsupportedTypeCount", bank.unsupportedTypeCount.coerceAtLeast(0))

            val questionsArray = org.json.JSONArray()

            bank.questions.forEachIndexed { index, q ->
                try {
                    val qJson = org.json.JSONObject()
                    qJson.put("type", q.type.name)
                    qJson.put("content", DataValidator.sanitizeContent(q.content))

                    val sanitizedOptions = DataValidator.sanitizeOptions(q.options)
                    qJson.put("options", org.json.JSONArray(sanitizedOptions))
                    qJson.put("correctAnswer", q.correctAnswer)
                    qJson.put("correctIndices", org.json.JSONArray(DataValidator.validateCorrectIndices(q.correctIndices, sanitizedOptions)))

                    questionsArray.put(qJson)
                } catch (e: Exception) {
                    Timber.e(e, "toJson: Error serializing question at index $index")

                    try {
                        val fallbackJson = org.json.JSONObject()
                        fallbackJson.put("type", "SHORT_ANSWER")
                        fallbackJson.put("content", "Failed to serialize question")
                        fallbackJson.put("options", org.json.JSONArray(emptyList<String>()))
                        fallbackJson.put("correctAnswer", "")
                        fallbackJson.put("correctIndices", org.json.JSONArray(listOf(0)))
                        questionsArray.put(fallbackJson)
                    } catch (e2: Exception) {
                        Timber.e(e2, "toJson: Failed to create fallback question at index $index")
                    }
                }
            }

            json.put("questions", questionsArray)
            val result = json.toString()

            Timber.d("toJson: Successfully serialized ${bank.questions.size} questions, output size=${result.length}")
            result

        } catch (e: Exception) {
            Timber.e(e, "toJson: Fatal error during serialization")

            try {
                val fallbackJson = org.json.JSONObject()
                fallbackJson.put("name", bank.name.ifBlank { "Untitled Bank" })
                fallbackJson.put("skippedCount", bank.skippedCount)
                fallbackJson.put("unsupportedTypeCount", bank.unsupportedTypeCount + bank.questions.size)
                fallbackJson.put("questions", org.json.JSONArray())

                Timber.w("toJson: Returning minimal JSON due to serialization error")
                fallbackJson.toString()
            } catch (e2: Exception) {
                Timber.e(e2, "toJson: Even fallback failed")
                "{\"name\":\"Error\",\"questions\":[]}"
            }
        }
    }

    fun fromJson(jsonString: String): ExtractedQuestionBank? {
        if (jsonString.isBlank()) {
            Timber.w("fromJson: Input string is blank")
            return null
        }

        if (jsonString.length > 50 * 1024 * 1024) {
            Timber.e("fromJson: Input size ${jsonString.length} exceeds 50MB limit")
            return null
        }

        return try {
            val json = org.json.JSONObject(jsonString)

            val name = json.optString("name", "Untitled Bank").ifBlank { "Untitled Bank" }
            val skippedCount = json.optInt("skippedCount", 0).coerceAtLeast(0)
            val unsupportedTypeCount = json.optInt("unsupportedTypeCount", 0).coerceAtLeast(0)

            val questionsArray = json.optJSONArray("questions")

            if (questionsArray == null) {
                Timber.d("fromJson: No 'questions' array found, creating empty bank with name='$name'")
                return ExtractedQuestionBank(name, emptyList(), skippedCount, unsupportedTypeCount)
            }

            if (questionsArray.length() == 0) {
                Timber.d("fromJson: Empty questions array for bank '$name'")
                return ExtractedQuestionBank(name, emptyList(), skippedCount, unsupportedTypeCount)
            }

            val validQuestions = mutableListOf<ExtractedQuestion>()
            var parseErrors = 0

            for (i in 0 until questionsArray.length()) {
                try {
                    val qJson = questionsArray.getJSONObject(i)

                    val rawOptionsArray = qJson.optJSONArray("options")
                    val rawOptions = if (rawOptionsArray != null) {
                        (0 until rawOptionsArray.length()).mapNotNull { j ->
                            try {
                                rawOptionsArray.getString(j)
                            } catch (e: Exception) {
                                Timber.w(e, "fromJson: Failed to read option $j in question $i")
                                null
                            }
                        }.filter { it.isNotBlank() }
                    } else {
                        emptyList()
                    }

                    val sanitizedOptions = DataValidator.sanitizeOptions(rawOptions)
                    val content = DataValidator.sanitizeContent(qJson.optString("content", ""))

                    if (content.isBlank()) {
                        Timber.w("fromJson: Skipping question $i due to blank content")
                        parseErrors++
                        continue
                    }

                    if (sanitizedOptions.isEmpty()) {
                        Timber.w("fromJson: Skipping question $i due to no valid options")
                        parseErrors++
                        continue
                    }

                    // Parse correctIndices - support both new (array) and legacy (single int) formats
                    val correctIndices = parseCorrectIndices(qJson, sanitizedOptions)

                    val question = ExtractedQuestion(
                        type = QuestionType.fromLegacyString(qJson.optString("type", "SINGLE_CHOICE").ifBlank { "SINGLE_CHOICE" }),
                        content = content,
                        options = sanitizedOptions,
                        correctAnswer = qJson.optString("correctAnswer", ""),
                        correctIndices = correctIndices
                    )

                    validQuestions.add(question)

                } catch (e: Exception) {
                    Timber.e(e, "fromJson: Failed to parse question at index $i")
                    parseErrors++
                }
            }

            if (parseErrors > 0) {
                Timber.w("fromJson: Completed with $parseErrors parsing errors out of ${questionsArray.length()} total. Valid questions: ${validQuestions.size}")
            }

            if (validQuestions.isEmpty() && questionsArray.length() > 0) {
                Timber.e("fromJson: All ${questionsArray.length()} questions failed to parse!")
                return ExtractedQuestionBank(name, emptyList(), skippedCount, unsupportedTypeCount + questionsArray.length())
            }

            val result = ExtractedQuestionBank(name, validQuestions, skippedCount, unsupportedTypeCount)
            Timber.d("fromJson: Successfully parsed ${validQuestions.size}/${questionsArray.length()} questions for bank '$name'")
            result

        } catch (e: org.json.JSONException) {
            Timber.e(e, "fromJson: JSON parsing error (input length=${jsonString.length}, preview=${jsonString.take(200)})")
            null
        } catch (e: Exception) {
            Timber.e(e, "fromJson: Unexpected error (input length=${jsonString.length})")
            null
        }
    }

    private fun parseCorrectIndices(qJson: org.json.JSONObject, options: List<String>): List<Int> {
        // Try new format: correctIndices as JSON array
        val indicesArray = qJson.optJSONArray("correctIndices")
        if (indicesArray != null && indicesArray.length() > 0) {
            val indices = (0 until indicesArray.length()).mapNotNull { i ->
                try { indicesArray.getInt(i) } catch (e: Exception) { null }
            }
            return DataValidator.validateCorrectIndices(indices, options)
        }

        // Fallback to legacy format: correctIndex as single int
        val legacyIndex = qJson.optInt("correctIndex", -1)
        if (legacyIndex >= 0) {
            return DataValidator.validateCorrectIndices(listOf(legacyIndex), options)
        }

        return listOf(0)
    }
}
