package mirujam.nekomemo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mirujam.nekomemo.data.local.entity.QuestionBankEntity

@Dao
interface QuestionBankDao {

    @Query("SELECT * FROM question_banks ORDER BY createdAt DESC")
    fun getAllBanks(): Flow<List<QuestionBankEntity>>

    @Query("SELECT * FROM question_banks WHERE id = :id")
    suspend fun getBankById(id: Long): QuestionBankEntity?

    @Query("SELECT * FROM question_banks WHERE id = :id")
    fun getBankByIdFlow(id: Long): Flow<QuestionBankEntity?>

    @Query("SELECT * FROM question_banks WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    fun getBanksByCategoryId(categoryId: Long): Flow<List<QuestionBankEntity>>

    @Query("SELECT COUNT(*) FROM question_banks WHERE categoryId = :categoryId")
    suspend fun getBankCountByCategoryId(categoryId: Long): Int

    @Query("SELECT categoryId, COUNT(*) as count FROM question_banks GROUP BY categoryId")
    fun getBankCountsByCategory(): Flow<List<CategoryBankCount>>

    @Query("UPDATE question_banks SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun reassignCategory(oldCategoryId: Long, newCategoryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBank(bank: QuestionBankEntity): Long

    @Update
    suspend fun updateBank(bank: QuestionBankEntity)

    @Delete
    suspend fun deleteBank(bank: QuestionBankEntity)

    @Query("SELECT COUNT(*) FROM question_banks")
    fun getBankCount(): Flow<Int>

    @Query("DELETE FROM question_banks")
    suspend fun deleteAll()
}
