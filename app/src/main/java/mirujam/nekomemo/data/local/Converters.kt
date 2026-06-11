package mirujam.nekomemo.data.local

import androidx.room.TypeConverter
import mirujam.nekomemo.domain.model.QuestionType

class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String = ListJsonConverter.fromStringList(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = ListJsonConverter.toStringList(value)

    @TypeConverter
    fun fromIntList(value: List<Int>): String = IntListJsonConverter.fromIntList(value)

    @TypeConverter
    fun toIntList(value: String): List<Int> = IntListJsonConverter.toIntList(value)

    @TypeConverter
    fun fromQuestionType(value: QuestionType): Int = value.code

    @TypeConverter
    fun toQuestionType(value: Int): QuestionType = QuestionType.fromCode(value)
}
