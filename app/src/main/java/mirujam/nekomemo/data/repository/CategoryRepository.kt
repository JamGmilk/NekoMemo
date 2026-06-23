package mirujam.nekomemo.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mirujam.nekomemo.data.local.dao.CategoryDao
import mirujam.nekomemo.data.local.dao.QuestionBankDao
import mirujam.nekomemo.data.local.entity.CategoryEntity
import mirujam.nekomemo.data.mapper.toDomainModel
import mirujam.nekomemo.data.mapper.toDomainCategoryModels
import mirujam.nekomemo.domain.model.Category
import mirujam.nekomemo.domain.validator.DataValidator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val questionBankDao: QuestionBankDao
) {
    companion object {
        const val DEFAULT_CATEGORY_NAME = "GENERAL"
    }

    fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllCategories().map { it.toDomainCategoryModels() }

    suspend fun getAllCategoriesSync(): List<Category> =
        categoryDao.getAllCategoriesSync().map { it.toDomainModel() }

    suspend fun getCategoryById(id: Long): Category? =
        categoryDao.getCategoryById(id)?.toDomainModel()

    suspend fun getCategoryByName(name: String): Category? =
        categoryDao.getCategoryByName(name)?.toDomainModel()

    fun isReservedCategoryName(name: String): Boolean {
        return name.uppercase() == DEFAULT_CATEGORY_NAME
    }

    suspend fun addCategory(name: String): Result<Long> {
        val trimmedName = name.trim()
        if (!DataValidator.isCategoryValid(trimmedName)) {
            return Result.failure(IllegalArgumentException("Invalid category name"))
        }
        if (isReservedCategoryName(trimmedName)) {
            return Result.failure(IllegalArgumentException("Cannot use reserved category name"))
        }
        val existing = categoryDao.getCategoryByName(trimmedName)
        if (existing != null) {
            return Result.success(existing.id)
        }
        val id = categoryDao.insertCategory(CategoryEntity(name = trimmedName))
        return Result.success(id)
    }

    suspend fun renameCategory(categoryId: Long, newName: String): Result<Unit> {
        val trimmedNewName = newName.trim()
        if (!DataValidator.isCategoryValid(trimmedNewName)) {
            return Result.failure(IllegalArgumentException("Invalid category name"))
        }
        if (isReservedCategoryName(trimmedNewName)) {
            return Result.failure(IllegalArgumentException("Cannot use reserved category name"))
        }
        val existingWithNewName = categoryDao.getCategoryByName(trimmedNewName)
        if (existingWithNewName != null && existingWithNewName.id != categoryId) {
            return Result.failure(IllegalArgumentException("Category name already exists"))
        }
        val category = categoryDao.getCategoryById(categoryId) ?: return Result.failure(IllegalArgumentException("Category not found"))
        if (category.name == DEFAULT_CATEGORY_NAME) {
            return Result.failure(IllegalArgumentException("Cannot rename default category"))
        }
        categoryDao.updateCategory(category.copy(name = trimmedNewName))
        return Result.success(Unit)
    }

    suspend fun deleteCategory(categoryId: Long): Result<Unit> {
        val category = categoryDao.getCategoryById(categoryId) ?: return Result.failure(IllegalStateException("Category not found"))
        if (category.name == DEFAULT_CATEGORY_NAME) {
            return Result.failure(IllegalStateException("Cannot delete default category"))
        }
        val general = categoryDao.getCategoryByName(DEFAULT_CATEGORY_NAME)
        if (general != null && general.id != categoryId) {
            questionBankDao.reassignCategory(oldCategoryId = categoryId, newCategoryId = general.id)
        }
        categoryDao.deleteCategory(category)
        return Result.success(Unit)
    }

    suspend fun getBankCountByCategoryId(categoryId: Long): Int =
        categoryDao.getBankCountByCategoryId(categoryId)

    suspend fun ensureDefaultCategory() {
        if (categoryDao.getCategoryCount() == 0) {
            categoryDao.insertCategory(CategoryEntity(name = DEFAULT_CATEGORY_NAME))
        }
    }
}
