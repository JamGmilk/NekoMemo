package mirujam.nekomemo.ui.model

import mirujam.nekomemo.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreModelTest {

    @Test
    fun calculate_countsCorrectWrongAndUnanswered() {
        val questions = listOf(
            QuestionUiModel(id = 1, text = "q1", options = listOf("A", "B"), correctIndices = listOf(0), type = QuestionType.SINGLE_CHOICE),
            QuestionUiModel(id = 2, text = "q2", options = listOf("A", "B"), correctIndices = listOf(1), type = QuestionType.SINGLE_CHOICE),
            QuestionUiModel(id = 3, text = "q3", options = listOf("A", "B"), correctIndices = listOf(1), type = QuestionType.SINGLE_CHOICE)
        )

        val score = ScoreModel.calculate(
            questions = questions,
            selectedAnswers = mapOf(0 to setOf(0), 1 to setOf(0))
        )

        assertEquals(1, score.correct)
        assertEquals(1, score.wrong)
        assertEquals(1, score.unanswered)
        assertEquals(3, score.total)
        assertEquals(33, score.percentage)
    }

    @Test
    fun calculate_handlesEmptyQuestions() {
        val score = ScoreModel.calculate(emptyList(), emptyMap())

        assertEquals(0, score.correct)
        assertEquals(0, score.wrong)
        assertEquals(0, score.unanswered)
        assertEquals(0, score.total)
        assertEquals(0, score.percentage)
    }

    @Test
    fun calculate_handlesMultipleChoice() {
        val questions = listOf(
            QuestionUiModel(id = 1, text = "q1", options = listOf("A", "B", "C"), correctIndices = listOf(0, 2), type = QuestionType.MULTIPLE_CHOICE),
            QuestionUiModel(id = 2, text = "q2", options = listOf("A", "B"), correctIndices = listOf(0, 1), type = QuestionType.MULTIPLE_CHOICE)
        )

        val score1 = ScoreModel.calculate(
            questions = questions,
            selectedAnswers = mapOf(0 to setOf(0, 2), 1 to setOf(0, 1))
        )
        assertEquals(2, score1.correct)

        val score2 = ScoreModel.calculate(
            questions = questions,
            selectedAnswers = mapOf(0 to setOf(0))
        )
        assertEquals(0, score2.correct)
        assertEquals(1, score2.wrong)
        assertEquals(1, score2.unanswered)
    }

    @Test
    fun calculate_fillBlankUsesLenientTextAnswers() {
        val questions = listOf(
            QuestionUiModel(
                id = 1,
                text = "fill",
                options = listOf("水肿", "硬化"),
                correctIndices = listOf(0, 1),
                type = QuestionType.FILL_BLANK
            ),
            QuestionUiModel(
                id = 2,
                text = "short",
                options = listOf("Virchow三联征"),
                correctIndices = listOf(0),
                type = QuestionType.SHORT_ANSWER
            ),
            QuestionUiModel(
                id = 3,
                text = "blank unanswered",
                options = listOf("答案"),
                correctIndices = listOf(0),
                type = QuestionType.FILL_BLANK
            )
        )

        val score = ScoreModel.calculate(
            questions = questions,
            selectedAnswers = emptyMap(),
            textAnswers = mapOf(
                0 to listOf("水肿", "硬化"),
                1 to listOf("virchow 三联征"),
                2 to listOf("")
            )
        )

        assertEquals(2, score.correct)
        assertEquals(0, score.wrong)
        assertEquals(1, score.unanswered)
    }

    @Test
    fun calculate_fillBlankWrongWhenMismatch() {
        val questions = listOf(
            QuestionUiModel(
                id = 1,
                text = "fill",
                options = listOf("水肿"),
                correctIndices = listOf(0),
                type = QuestionType.FILL_BLANK
            )
        )
        val score = ScoreModel.calculate(
            questions = questions,
            selectedAnswers = emptyMap(),
            textAnswers = mapOf(0 to listOf("坏死"))
        )
        assertEquals(0, score.correct)
        assertEquals(1, score.wrong)
    }
}
