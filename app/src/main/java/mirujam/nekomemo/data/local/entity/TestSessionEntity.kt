package mirujam.nekomemo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "test_sessions",
    foreignKeys = [
        ForeignKey(
            entity = QuestionBankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bankId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TestSessionEntity(
    @PrimaryKey
    val bankId: Long,
    val questionIdsJson: String,
    val currentIndex: Int = 0,
    val selectedAnswersJson: String = "{}",
    val textAnswersJson: String = "{}",
    val revealedJson: String = "[]",
    val shuffleQuestions: Boolean = false,
    val shuffleOptions: Boolean = false,
    val practiceMode: String = "ALL",
    val typesFilter: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
