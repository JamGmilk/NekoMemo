package mirujam.nekomemo.data.local

import androidx.room.TypeConverter
import mirujam.nekomemo.domain.model.QuestionType

class Converters {

    @TypeConverter
    fun fromQuestionType(value: QuestionType): Int = value.code

    @TypeConverter
    fun toQuestionType(value: Int): QuestionType = QuestionType.fromCode(value)
}
