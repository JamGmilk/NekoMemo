package mirujam.nekomemo.data.local.dao

/**
 * POJO for the GROUP BY query result of bank counts per category.
 */
data class CategoryBankCount(
    val categoryId: Long,
    val count: Int
)
