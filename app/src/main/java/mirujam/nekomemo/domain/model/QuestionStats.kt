package mirujam.nekomemo.domain.model

data class QuestionStats(
    val questionId: Long,
    val attemptCount: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lastPracticedAt: Long = 0L,
    val inWrongBook: Boolean = false
)

data class BankMasteryInfo(
    val bankId: Long,
    val attemptCount: Int,
    val correctCount: Int,
    val wrongBookCount: Int,
    val masteryPercent: Int?
)

data class TestSession(
    val bankId: Long,
    val questionIds: List<Long>,
    val currentIndex: Int = 0,
    val selectedAnswers: Map<Int, Set<Int>> = emptyMap(),
    val textAnswers: Map<Int, List<String>> = emptyMap(),
    val revealedQuestions: Set<Int> = emptySet(),
    val shuffleQuestions: Boolean = false,
    val shuffleOptions: Boolean = false,
    val practiceMode: PracticeMode = PracticeMode.ALL,
    val typesFilter: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
