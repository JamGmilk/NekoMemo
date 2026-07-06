package mirujam.nekomemo.domain.model

import androidx.compose.runtime.Immutable
import mirujam.nekomemo.data.repository.CategoryRepository

@Immutable
data class Category(
    val id: Long = 0,
    val name: String
) {
    val isDefault: Boolean get() = name == CategoryRepository.DEFAULT_CATEGORY_NAME
}
