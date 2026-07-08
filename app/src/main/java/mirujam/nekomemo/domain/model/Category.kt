package mirujam.nekomemo.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Category(
    val id: Long = 0,
    val name: String
) {
    companion object {
        const val DEFAULT_CATEGORY_NAME = "GENERAL"
    }

    val isDefault: Boolean get() = name == DEFAULT_CATEGORY_NAME
}
