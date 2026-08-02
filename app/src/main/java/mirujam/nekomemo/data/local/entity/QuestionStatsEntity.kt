package mirujam.nekomemo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "question_stats",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class QuestionStatsEntity(
    @PrimaryKey
    val questionId: Long,
    val attemptCount: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lastPracticedAt: Long = 0L,
    val inWrongBook: Boolean = false
)
