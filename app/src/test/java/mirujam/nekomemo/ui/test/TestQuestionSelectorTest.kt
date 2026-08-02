package mirujam.nekomemo.ui.test

import mirujam.nekomemo.domain.model.PracticeMode
import mirujam.nekomemo.domain.model.Question
import mirujam.nekomemo.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestQuestionSelectorTest {

    private fun q(
        id: Long,
        type: QuestionType = QuestionType.SINGLE_CHOICE,
        favorite: Boolean = false
    ) = Question(
        id = id,
        questionBankId = 1,
        text = "Q$id",
        options = listOf("A", "B"),
        correctIndices = listOf(0),
        type = type,
        isFavorite = favorite
    )

    @Test
    fun applyQuestionCount_takesRequestedAmount() {
        val models = TestQuestionSelector.select(
            questions = listOf(q(1), q(2), q(3), q(4), q(5)),
            practiceMode = PracticeMode.ALL,
            typeCodes = emptySet(),
            wrongIds = emptySet(),
            questionCount = 2,
            shuffleQuestions = false,
            shuffleOptions = false
        )
        assertEquals(2, models.size)
        assertEquals(1L, models[0].id)
        assertEquals(2L, models[1].id)
    }

    @Test
    fun applyQuestionCount_zeroMeansAll() {
        val models = TestQuestionSelector.select(
            questions = listOf(q(1), q(2), q(3)),
            practiceMode = PracticeMode.ALL,
            typeCodes = emptySet(),
            wrongIds = emptySet(),
            questionCount = 0,
            shuffleQuestions = false,
            shuffleOptions = false
        )
        assertEquals(3, models.size)
    }

    @Test
    fun select_filtersWrongAndFavoriteAndType() {
        val questions = listOf(
            q(1, QuestionType.SINGLE_CHOICE, favorite = true),
            q(2, QuestionType.MULTIPLE_CHOICE, favorite = true),
            q(3, QuestionType.FILL_BLANK, favorite = false),
            q(4, QuestionType.SINGLE_CHOICE, favorite = false)
        )

        val wrongOnly = TestQuestionSelector.select(
            questions = questions,
            practiceMode = PracticeMode.WRONG,
            typeCodes = emptySet(),
            wrongIds = setOf(2L, 3L),
            questionCount = 0,
            shuffleQuestions = false,
            shuffleOptions = false
        )
        assertEquals(listOf(2L, 3L), wrongOnly.map { it.id })

        val favorites = TestQuestionSelector.select(
            questions = questions,
            practiceMode = PracticeMode.FAVORITE,
            typeCodes = setOf(QuestionType.SINGLE_CHOICE.code),
            wrongIds = emptySet(),
            questionCount = 0,
            shuffleQuestions = false,
            shuffleOptions = false
        )
        assertEquals(listOf(1L), favorites.map { it.id })
    }

    @Test
    fun parseAndEncodeTypeCodes_roundTrip() {
        val encoded = TestQuestionSelector.encodeTypeCodes(
            setOf(QuestionType.SINGLE_CHOICE, QuestionType.FILL_BLANK)
        )
        val parsed = TestQuestionSelector.parseTypeCodes(encoded)
        assertTrue(QuestionType.SINGLE_CHOICE.code in parsed)
        assertTrue(QuestionType.FILL_BLANK.code in parsed)
        assertEquals(2, parsed.size)
    }
}
