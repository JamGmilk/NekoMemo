package mirujam.nekomemo.domain.model

enum class PracticeMode {
    ALL,
    WRONG,
    FAVORITE;

    companion object {
        fun fromString(value: String?): PracticeMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: ALL
    }
}
