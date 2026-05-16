package mirujam.nekomemo.domain.validator

import org.junit.Assert.assertEquals
import org.junit.Test

class DataValidatorTest {

    @Test
    fun sanitizeOptions_trimsLimitsAndDropsBlankOptions() {
        val options = listOf(" A ", "", "B", " ".repeat(3), "C")

        val sanitized = DataValidator.sanitizeOptions(options)

        assertEquals(listOf("A", "B", "C"), sanitized)
    }

    @Test
    fun validateCorrectIndex_clampsToAvailableOptions() {
        val options = listOf("A", "B", "C")

        assertEquals(0, DataValidator.validateCorrectIndex(-1, options))
        assertEquals(2, DataValidator.validateCorrectIndex(10, options))
        assertEquals(1, DataValidator.validateCorrectIndex(1, options))
    }

    @Test
    fun validateCorrectIndex_returnsZeroWhenOptionsAreEmpty() {
        assertEquals(0, DataValidator.validateCorrectIndex(10, emptyList()))
    }
}
