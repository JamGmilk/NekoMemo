package mirujam.nekomemo.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameSanitizerTest {

    @Test
    fun sanitize_replacesInvalidFileNameCharacters() {
        val result = FileNameSanitizer.sanitize("  章节/作业:第一?题库*  ")

        assertEquals("章节_作业_第一_题库", result)
    }

    @Test
    fun sanitize_usesFallbackForBlankName() {
        val result = FileNameSanitizer.sanitize(":/?*", fallback = "Export")

        assertEquals("Export", result)
    }
}
