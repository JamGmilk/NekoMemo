package mirujam.nekomemo.domain.model

import androidx.annotation.StringRes
import mirujam.nekomemo.R

/**
 * 题目类型枚举，数据库存储为 INTEGER (1字节)
 */
enum class QuestionType(val code: Int) {
    SINGLE_CHOICE(1),
    MULTIPLE_CHOICE(2),
    TRUE_FALSE(3),
    FILL_BLANK(4),
    SHORT_ANSWER(5);

    @StringRes
    fun displayNameRes(): Int = when (this) {
        SINGLE_CHOICE -> R.string.question_type_single
        MULTIPLE_CHOICE -> R.string.question_type_multiple
        TRUE_FALSE -> R.string.question_type_true_false
        FILL_BLANK -> R.string.question_type_fill_blank
        SHORT_ANSWER -> R.string.question_type_short_answer
    }

    companion object {
        fun fromCode(code: Int): QuestionType = entries.find { it.code == code } ?: SINGLE_CHOICE

        /**
         * 从旧版字符串 type 解析，用于迁移和导入兼容
         * 支持两种格式：
         * - 枚举 name: "SINGLE_CHOICE", "MULTIPLE_CHOICE", "FILL_BLANK" 等
         * - 旧字符串: "Single Choice", "Multiple Choice", "Fill in the Blank" 等
         */
        fun fromLegacyString(value: String): QuestionType {
            // 先尝试匹配枚举 name
            entries.find { it.name == value }?.let { return it }
            // 再尝试匹配旧字符串格式
            return when {
                value.contains("Single Choice", ignoreCase = true) -> SINGLE_CHOICE
                value.contains("Multiple Choice", ignoreCase = true) -> MULTIPLE_CHOICE
                value.contains("True/False", ignoreCase = true) -> TRUE_FALSE
                value.contains("Fill in the Blank", ignoreCase = true) -> FILL_BLANK
                value.contains("Short Answer", ignoreCase = true) -> SHORT_ANSWER
                else -> SINGLE_CHOICE
            }
        }
    }
}
