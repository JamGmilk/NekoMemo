package mirujam.nekomemo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import mirujam.nekomemo.domain.model.QuestionType

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = QuestionBankEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionBankId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("questionBankId")]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val questionBankId: Long,
    val text: String,
    val options: String,
    val correctIndices: String,
    val type: QuestionType,
    val isFavorite: Boolean = false
)
