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

    suspend fun isDefaultCategory(categoryId: Long): Boolean {
        val category = categoryDao.getCategoryById(categoryId) ?: return false
        return category.name == DEFAULT_CATEGORY_NAME
    }

    fun getDefaultCategoryName(): String = DEFAULT_CATEGORY_NAME

    suspend fun isReservedCategoryName(name: String): Boolean {
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

    suspend fun updateCategory(oldName: String, newName: String): Result<Unit> {
        val trimmedNewName = newName.trim()
        if (!DataValidator.isCategoryValid(trimmedNewName)) {
            return Result.failure(IllegalArgumentException("Invalid category name"))
        }
        if (isReservedCategoryName(trimmedNewName)) {
            return Result.failure(IllegalArgumentException("Cannot use reserved category name"))
        }
        val existing = categoryDao.getCategoryByName(trimmedNewName)
        if (existing != null) {
            return Result.failure(IllegalArgumentException("Category name already exists"))
        }
        val oldCategory = categoryDao.getCategoryByName(oldName) ?: return Result.failure(IllegalArgumentException("Category not found"))
        categoryDao.updateCategory(oldCategory.copy(name = trimmedNewName))
        return Result.success(Unit)
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
        val category = categoryDao.getCategoryById(categoryId) ?: return Result.failure(IllegalArgumentException("Category not found"))
        if (category.name == DEFAULT_CATEGORY_NAME) {
            return Result.failure(IllegalStateException("Cannot delete default category"))
        }
        val bankCount = categoryDao.getBankCountByCategoryId(categoryId)
        if (bankCount > 0) {
            return Result.failure(IllegalStateException("Cannot delete category with existing banks"))
        }
        categoryDao.deleteCategory(category)
        return Result.success(Unit)
    }

    suspend fun canDeleteCategory(categoryId: Long): Boolean {
        val category = categoryDao.getCategoryById(categoryId) ?: return false
        if (category.name == DEFAULT_CATEGORY_NAME) {
            return false
        }
        val bankCount = categoryDao.getBankCountByCategoryId(categoryId)
        return bankCount == 0
    }

    suspend fun getBankCountByCategoryId(categoryId: Long): Int =
        categoryDao.getBankCountByCategoryId(categoryId)

    suspend fun ensureDefaultCategory() {
        if (categoryDao.getCategoryCount() == 0) {
            categoryDao.insertCategory(CategoryEntity(name = DEFAULT_CATEGORY_NAME))
        }
    }
}
