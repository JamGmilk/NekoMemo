package mirujam.nekomemo.domain.model

/**
 * 题目类型枚举，数据库存储为 INTEGER (1字节)
 */
enum class QuestionType(val code: Int) {
    SINGLE_CHOICE(1),
    MULTIPLE_CHOICE(2),
    TRUE_FALSE(3),
    FILL_BLANK(4),
    SHORT_ANSWER(5);

    companion object {
        fun fromCode(code: Int): QuestionType = entries.find { it.code == code } ?: SINGLE_CHOICE

        /**
         * 从旧版字符串 type 解析，用于迁移和导入兼容
         */
        fun fromLegacyString(value: String): QuestionType = when {
            value.contains("Single Choice", ignoreCase = true) -> SINGLE_CHOICE
            value.contains("Multiple Choice", ignoreCase = true) -> MULTIPLE_CHOICE
            value.contains("True/False", ignoreCase = true) -> TRUE_FALSE
            value.contains("Fill in the Blank", ignoreCase = true) -> FILL_BLANK
            value.contains("Short Answer", ignoreCase = true) -> SHORT_ANSWER
            else -> SINGLE_CHOICE
        }
    }
}
