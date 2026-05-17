package mirujam.nekomemo.domain.usecase

import mirujam.nekomemo.data.model.ExtractedQuestion
import mirujam.nekomemo.data.model.ExtractedQuestionBank
import mirujam.nekomemo.domain.validator.DataValidator
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import android.util.Log

object HtmlParserUseCase {

    private const val TAG = "HtmlParser"

    private val NUMBER_PREFIX_REGEX = Regex("^\\d+\\.\\s*")
    private val CORRECT_ANSWER_REGEX = Regex("正确答案[:\\s]*([A-Ha-h])")
    private val LETTER_REGEX = Regex("[A-Ha-h]")

    fun parse(html: String): ExtractedQuestionBank {
        Log.d(TAG, "Starting parse, HTML length: ${html.length}")
        val startTime = System.currentTimeMillis()

        val doc: Document = try {
            Jsoup.parse(html)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse HTML", e)
            return ExtractedQuestionBank("", emptyList())
        }

        val bankName = doc.select("h2.mark_title").text().trim()
        Log.d(TAG, "Bank name: '$bankName'")

        val questionDivs = doc.select("div.questionLi")
        val totalQuestions = questionDivs.size
        Log.d(TAG, "Found $totalQuestions question div(s)")

        if (totalQuestions == 0) {
            Log.w(TAG, "No questions found!")
            return ExtractedQuestionBank(bankName, emptyList())
        }

        val questions = mutableListOf<ExtractedQuestion>()
        var skippedCount = 0
        var unsupportedTypeCount = 0
        var processedCount = 0

        for ((index, div) in questionDivs.withIndex()) {
            try {
                val type = parseQuestionType(div)

                if (type != "Single Choice") {
                    Log.d(TAG, "Skipping question $index: unsupported type '$type'")
                    unsupportedTypeCount++
                    continue
                }

                val content = DataValidator.sanitizeContent(parseQuestionContent(div))
                val options = parseOptions(div)
                val correctAnswer = parseCorrectAnswer(div)
                val correctIndex = letterToIndex(correctAnswer)

                if (content.isNotBlank() && options.isNotEmpty() && correctAnswer.isNotBlank()) {
                    questions.add(
                        ExtractedQuestion(
                            type = type,
                            content = content,
                            options = options,
                            correctAnswer = correctAnswer,
                            correctIndex = correctIndex
                        )
                    )
                    processedCount++
                } else {
                    skippedCount++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing question $index: ${e.message}")
                skippedCount++
            }

            if (index % 50 == 0 && index > 0) {
                Log.d(TAG, "Progress: $index/$totalQuestions processed")
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Parse complete in ${elapsed}ms. Valid: $processedCount/$totalQuestions, Skipped (no answer): $skippedCount, Unsupported type: $unsupportedTypeCount")

        return ExtractedQuestionBank(
            name = bankName,
            questions = questions,
            skippedCount = skippedCount,
            unsupportedTypeCount = unsupportedTypeCount
        )
    }

    private fun parseQuestionType(div: org.jsoup.nodes.Element): String {
        val typeSpan = div.select("span.colorShallow").first()
        val typeText = typeSpan?.text()?.trim() ?: return "Unknown"
        return when {
            typeText.contains("单选题") -> "Single Choice"
            typeText.contains("多选题") -> "Multiple Choice"
            typeText.contains("判断题") -> "True/False"
            typeText.contains("填空题") -> "Fill in the Blank"
            typeText.contains("简答题") -> "Short Answer"
            else -> "Unknown"
        }
    }

    private fun parseQuestionContent(div: org.jsoup.nodes.Element): String {
        val contentSpan = div.select("span.qtContent").first()
        if (contentSpan != null) {
            return contentSpan.text().trim()
        }

        val h3 = div.select("h3.mark_name").first() ?: return ""
        val typeSpan = h3.select("span.colorShallow").first()
        if (typeSpan != null) {
            val fullText = h3.text().trim()
            val typeText = typeSpan.text().trim()
            val contentStart = fullText.indexOf(typeText)
            if (contentStart >= 0) {
                val afterType = fullText.substring(contentStart + typeText.length).trim()
                return NUMBER_PREFIX_REGEX.replace(afterType, "").trim()
            }
        }
        return h3.text().trim()
    }

    private fun parseOptions(div: org.jsoup.nodes.Element): List<String> {
        val optionLis = div.select("ul.mark_letter li")
        if (optionLis.isNotEmpty()) {
            return optionLis.mapNotNull { li ->
                val text = li.text().trim()
                text.takeIf { it.isNotBlank() }
            }
        }

        val answerDivs = div.select("div.stem_answer div.answerBg")
        if (answerDivs.isNotEmpty()) {
            return answerDivs.mapNotNull { answerDiv ->
                try {
                    val letterSpan = answerDiv.select("span.num_option").first()
                    val textDiv = answerDiv.select("div.answer_p").first()
                    val letter = letterSpan?.text()?.trim() ?: ""
                    val text = textDiv?.text()?.trim() ?: ""
                    when {
                        letter.isNotBlank() && text.isNotBlank() -> "$letter. $text"
                        text.isNotBlank() -> text
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }

        return emptyList()
    }

    private fun parseCorrectAnswer(div: org.jsoup.nodes.Element): String {
        val correctSpan = div.select("span.rightAnswerContent").first()
        if (correctSpan != null) {
            return normalizeAnswerLetter(correctSpan.text())
        }

        val greenSpan = div.select("span.colorGreen").first()
        if (greenSpan != null) {
            val text = greenSpan.text().trim()
            val match = CORRECT_ANSWER_REGEX.find(text)
            if (match != null) {
                return normalizeAnswerLetter(match.groupValues[1])
            }
            val letterMatch = LETTER_REGEX.find(text)
            if (letterMatch != null) {
                return normalizeAnswerLetter(letterMatch.value)
            }
        }

        return ""
    }

    private fun normalizeAnswerLetter(answer: String): String {
        return LETTER_REGEX.find(answer)?.value?.uppercase().orEmpty()
    }

    private fun letterToIndex(letter: String): Int {
        if (letter.isBlank()) return 0
        val index = "ABCDEFGH".indexOf(letter.uppercase())
        return if (index >= 0) index else 0
    }
}

fun decodeHtmlFromJs(raw: String?): String {
    if (raw == null) {
        Log.w("HtmlParser", "decodeHtmlFromJs: input is null")
        return ""
    }
    
    if (raw.isBlank()) {
        Log.w("HtmlParser", "decodeHtmlFromJs: input is blank")
        return ""
    }
    
    val trimmedInput = raw.trim()
    
    return try {
        val decoded = org.json.JSONObject("{\"v\":$trimmedInput}").getString("v")
        
        if (decoded.isBlank()) {
            Log.w("HtmlParser", "decodeHtmlFromJs: decoded result is blank for input length=${trimmedInput.length}")
            return ""
        }
        
        Log.d("HtmlParser", "decodeHtmlFromJs: successfully decoded ${decoded.length} chars from input ${trimmedInput.length} chars")
        decoded
    } catch (e: org.json.JSONException) {
        Log.e("HtmlParser", "decodeHtmlFromJs: JSON parsing failed for input (length=${trimmedInput.length}, preview=${trimmedInput.take(100)})", e)
        
        try {
            val fallbackDecoded = raw.replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\")
            
            if (fallbackDecoded != raw) {
                Log.d("HtmlParser", "decodeHtmlFromJs: fallback decoding succeeded, result length=${fallbackDecoded.length}")
                fallbackDecoded
            } else {
                Log.w("HtmlParser", "decodeHtmlFromJs: both JSON and fallback decoding failed, returning sanitized input")
                raw.take(10000)
            }
        } catch (e2: Exception) {
            Log.e("HtmlParser", "decodeHtmlFromJs: fallback decoding also failed", e2)
            raw.take(10000)
        }
    } catch (e: Exception) {
        Log.e("HtmlParser", "decodeHtmlFromJs: unexpected error for input (length=${raw.length})", e)
        raw.take(10000)
    }
}
