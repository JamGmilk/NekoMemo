package mirujam.nekomemo.util

object FileNameSanitizer {
    private val INVALID_FILE_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    fun sanitize(name: String, fallback: String = "NekoMemo"): String {
        val sanitized = INVALID_FILE_CHARS
            .replace(name.trim(), "_")
            .replace(Regex("_+"), "_")
            .trim('.', ' ', '_')

        return sanitized.ifBlank { fallback }
    }
}
