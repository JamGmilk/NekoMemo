package mirujam.nekomemo.domain.model

import mirujam.nekomemo.data.repository.CategoryRepository

data class Category(
    val id: Long = 0,
    val name: String
) {
    val isDefault: Boolean get() = name == CategoryRepository.DEFAULT_CATEGORY_NAME
}
