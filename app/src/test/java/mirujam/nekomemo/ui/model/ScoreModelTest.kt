package mirujam.nekomemo.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreModelTest {

    @Test
    fun calculate_countsCorrectWrongAndUnanswered() {
        val questions = listOf(
            QuestionUiModel(id = 1, text = "q1", options = listOf("A", "B"), correctIndex = 0),
            QuestionUiModel(id = 2, text = "q2", options = listOf("A", "B"), correctIndex = 1),
            QuestionUiModel(id = 3, text = "q3", options = listOf("A", "B"), correctIndex = 1)
        )

        val score = ScoreModel.calculate(
            questions = questions,
            selectedAnswers = mapOf(0 to 0, 1 to 0)
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
}
