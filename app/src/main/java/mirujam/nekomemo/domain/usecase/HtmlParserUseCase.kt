package mirujam.nekomemo.domain.usecase

import mirujam.nekomemo.domain.model.ExtractedQuestion
import mirujam.nekomemo.domain.model.ExtractedQuestionBank
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import timber.log.Timber

import javax.inject.Inject

class HtmlParserUseCase @Inject constructor() {

    companion object {
        private val NUMBER_PREFIX_REGEX = Regex("^\\d+\\.\\s*")
        private val LETTER_PREFIX_REGEX = Regex("^[A-Ha-h]\\.\\s*")
        private val CORRECT_ANSWER_REGEX = Regex("正确答案[:\\s]*([A-Ha-h]+)")
        private val LETTER_REGEX = Regex("[A-Ha-h]")
    }

    fun parse(html: String): ExtractedQuestionBank {
        Timber.d("Starting parse, HTML length: ${html.length}")
        val startTime = System.currentTimeMillis()

        val doc: Document = try {
            Jsoup.parse(html)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse HTML")
            return ExtractedQuestionBank("", emptyList())
        }

        val bankName = doc.select("h2.mark_title").text().trim()
        Timber.d("Bank name: '$bankName'")

        val questionDivs = doc.select("div.questionLi")
        val totalQuestions = questionDivs.size
        Timber.d("Found $totalQuestions question div(s)")

        if (totalQuestions == 0) {
            Timber.w("No questions found!")
            return ExtractedQuestionBank(bankName, emptyList())
        }

        val questions = mutableListOf<ExtractedQuestion>()
        var skippedCount = 0
        var unsupportedTypeCount = 0
        var processedCount = 0

        for ((index, div) in questionDivs.withIndex()) {
            try {
                val type = parseQuestionType(div)

                when (type) {
                    "Single Choice", "Multiple Choice" -> {
                        val content = ExtractedQuestion.sanitizeContent(parseQuestionContent(div))
                        val options = parseOptions(div)
                        val correctAnswer = parseCorrectAnswer(div)
                        val correctIndices = lettersToIndices(correctAnswer)

                        if (content.isNotBlank() && options.isNotEmpty() && correctAnswer.isNotBlank()) {
                            questions.add(
                                ExtractedQuestion(
                                    type = type,
                                    content = content,
                                    options = options,
                                    correctAnswer = correctAnswer,
                                    correctIndices = correctIndices
                                )
                            )
                            processedCount++
                        } else {
                            skippedCount++
                        }
                    }
                    "Fill in the Blank" -> {
                        val content = ExtractedQuestion.sanitizeContent(parseQuestionContent(div))
                        val fillAnswers = parseFillBlankAnswers(div)
                        val correctAnswer = fillAnswers.joinToString("; ")

                        if (content.isNotBlank() && fillAnswers.isNotEmpty()) {
                            questions.add(
                                ExtractedQuestion(
                                    type = type,
                                    content = content,
                                    options = fillAnswers,
                                    correctAnswer = correctAnswer,
                                    correctIndices = fillAnswers.indices.toList()
                                )
                            )
                            processedCount++
                        } else {
                            skippedCount++
                        }
                    }
                    else -> {
                        Timber.d("Skipping question $index: unsupported type '$type'")
                        unsupportedTypeCount++
                    }
                }
            } catch (e: Exception) {
                Timber.w("Error parsing question $index: ${e.message}")
                skippedCount++
            }

            if (index % 50 == 0 && index > 0) {
                Timber.d("Progress: $index/$totalQuestions processed")
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Timber.d("Parse complete in ${elapsed}ms. Valid: $processedCount/$totalQuestions, Skipped (no answer): $skippedCount, Unsupported type: $unsupportedTypeCount")

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
                val cleanText = LETTER_PREFIX_REGEX.replace(text, "")
                cleanText.takeIf { it.isNotBlank() }
            }
        }

        val answerDivs = div.select("div.stem_answer div.answerBg")
        if (answerDivs.isNotEmpty()) {
            return answerDivs.mapNotNull { answerDiv ->
                try {
                    val textDiv = answerDiv.select("div.answer_p").first()
                    val text = textDiv?.text()?.trim() ?: ""
                    val cleanText = LETTER_PREFIX_REGEX.replace(text, "")
                    cleanText.takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse answer div")
                    null
                }
            }
        }

        return emptyList()
    }

    private fun parseCorrectAnswer(div: org.jsoup.nodes.Element): String {
        val correctSpan = div.select("span.rightAnswerContent").first()
        if (correctSpan != null) {
            return correctSpan.text().trim()
        }

        val greenSpan = div.select("span.colorGreen").first()
        if (greenSpan != null) {
            val text = greenSpan.text().trim()
            val match = CORRECT_ANSWER_REGEX.find(text)
            if (match != null) {
                return match.groupValues[1].uppercase()
            }
            val letterMatch = LETTER_REGEX.find(text)
            if (letterMatch != null) {
                return letterMatch.value.uppercase()
            }
        }

        return ""
    }

    private fun parseFillBlankAnswers(div: org.jsoup.nodes.Element): List<String> {
        val rightAnswerDds = div.select("dl.mark_fill dd.rightAnswerContent")
        if (rightAnswerDds.isNotEmpty()) {
            return rightAnswerDds.mapNotNull { dd ->
                val text = dd.text().trim()
                    .replace(Regex("^\\(\\d+\\)\\s*"), "")
                text.takeIf { it.isNotBlank() }
            }
        }
        return emptyList()
    }

    private fun lettersToIndices(letters: String): List<Int> {
        if (letters.isBlank()) return emptyList()
        return letters.mapNotNull { ch ->
            val index = "ABCDEFGH".indexOf(ch.uppercaseChar())
            if (index >= 0) index else null
        }
    }

    fun decodeHtmlFromJs(raw: String?): String {
        if (raw == null) {
            Timber.w("decodeHtmlFromJs: input is null")
            return ""
        }
        
        if (raw.isBlank()) {
            Timber.w("decodeHtmlFromJs: input is blank")
            return ""
        }
        
        val trimmedInput = raw.trim()
        
        return try {
            val decoded = org.json.JSONObject("{\"v\":$trimmedInput}").getString("v")
            
            if (decoded.isBlank()) {
                Timber.w("decodeHtmlFromJs: decoded result is blank for input length=${trimmedInput.length}")
                return ""
            }
            
            Timber.d("decodeHtmlFromJs: successfully decoded ${decoded.length} chars from input ${trimmedInput.length} chars")
            decoded
        } catch (e: org.json.JSONException) {
            Timber.e(e, "decodeHtmlFromJs: JSON parsing failed for input (length=${trimmedInput.length}, preview=${trimmedInput.take(100)})")
            
            try {
                val fallbackDecoded = raw.replace("\\\"", "\"")
                    .replace("\\'", "'")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\")
                
                if (fallbackDecoded != raw) {
                    Timber.d("decodeHtmlFromJs: fallback decoding succeeded, result length=${fallbackDecoded.length}")
                    fallbackDecoded
                } else {
                    Timber.w("decodeHtmlFromJs: both JSON and fallback decoding failed, returning sanitized input")
                    raw.take(10000)
                }
            } catch (e2: Exception) {
                Timber.e(e2, "decodeHtmlFromJs: fallback decoding also failed")
                raw.take(10000)
            }
        } catch (e: Exception) {
            Timber.e(e, "decodeHtmlFromJs: unexpected error for input (length=${raw.length})")
            raw.take(10000)
        }
    }
}
