package mirujam.nekomemo.domain.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerNormalizerTest {

    @Test
    fun normalize_trimsAndLowercases() {
        assertTrue(AnswerNormalizer.answersMatch("  Hello ", "hello"))
    }

    @Test
    fun normalize_convertsFullWidth() {
        assertTrue(AnswerNormalizer.answersMatch("ＡＢＣ", "abc"))
    }

    @Test
    fun normalize_ignoresPunctuationAndSpaces() {
        assertTrue(AnswerNormalizer.answersMatch("心力衰竭，细胞", "心力衰竭细胞"))
        assertTrue(AnswerNormalizer.answersMatch("heart-failure", "heartfailure"))
    }

    @Test
    fun fillBlanksMatch_comparesPositionally() {
        assertTrue(
            AnswerNormalizer.fillBlanksMatch(
                listOf("水肿", "硬化"),
                listOf("水肿", "硬化")
            )
        )
        assertFalse(
            AnswerNormalizer.fillBlanksMatch(
                listOf("水肿"),
                listOf("水肿", "硬化")
            )
        )
        assertFalse(
            AnswerNormalizer.fillBlanksMatch(
                listOf("水肿", "坏死"),
                listOf("水肿", "硬化")
            )
        )
    }
}
