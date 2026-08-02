package mirujam.nekomemo.domain.util

/**
 * Normalizes free-text answers for lenient comparison (fill-blank / short-answer).
 */
object AnswerNormalizer {

    private val PUNCTUATION_REGEX = Regex("[\\s\\p{Punct}，。！？；：、（）【】《》…—·\"']+")

    fun normalize(input: String): String {
        val halfWidth = toHalfWidth(input.trim())
        val lower = halfWidth.lowercase()
        val noPunct = PUNCTUATION_REGEX.replace(lower, "")
        return noPunct
    }

    fun answersMatch(userAnswer: String, expected: String): Boolean =
        normalize(userAnswer) == normalize(expected)

    /**
     * Compares fill-blank answers positionally.
     * Empty expected slots are ignored; all non-empty expected answers must match.
     */
    fun fillBlanksMatch(userAnswers: List<String>, expectedAnswers: List<String>): Boolean {
        if (expectedAnswers.isEmpty()) return false
        if (userAnswers.size < expectedAnswers.size) return false
        return expectedAnswers.indices.all { index ->
            answersMatch(userAnswers.getOrElse(index) { "" }, expectedAnswers[index])
        }
    }

    private fun toHalfWidth(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '\uFF01'..'\uFF5E' -> sb.append((ch.code - 0xFEE0).toChar())
                '\u3000' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
