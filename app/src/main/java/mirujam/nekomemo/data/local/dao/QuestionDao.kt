package mirujam.nekomemo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mirujam.nekomemo.data.local.entity.QuestionCountByBank
import mirujam.nekomemo.data.local.entity.QuestionEntity

@Dao
interface QuestionDao {

    @Query("SELECT * FROM questions WHERE questionBankId = :bankId ORDER BY id")
    fun getQuestionsForBank(bankId: Long): Flow<List<QuestionEntity>>

    /** 统一查询：query 为空时 LIKE '%%' 匹配所有行，等价于无条件查询 */
    @Query("SELECT * FROM questions WHERE questionBankId = :bankId AND text LIKE '%' || :query || '%' ORDER BY id")
    fun queryQuestionsForBank(bankId: Long, query: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE questionBankId = :bankId ORDER BY id")
    suspend fun getQuestionsForBankSync(bankId: Long): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestionById(id: Long): QuestionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(questions: List<QuestionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(question: QuestionEntity): Long

    @Update
    suspend fun updateQuestion(question: QuestionEntity)

    @Update
    suspend fun updateQuestions(questions: List<QuestionEntity>)

    @Transaction
    suspend fun insertOrUpdateInTransaction(questions: List<QuestionEntity>) {
        val (newQuestions, existingQuestions) = questions.partition { it.id == 0L }
        if (newQuestions.isNotEmpty()) {
            insertAll(newQuestions)
        }
        if (existingQuestions.isNotEmpty()) {
            updateQuestions(existingQuestions)
        }
    }

    @Delete
    suspend fun deleteQuestion(question: QuestionEntity)

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun deleteQuestionById(id: Long)

    @Query("DELETE FROM questions WHERE questionBankId = :bankId")
    suspend fun deleteQuestionsForBank(bankId: Long)

    @Query("DELETE FROM questions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM questions")
    fun getTotalQuestionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM questions WHERE questionBankId = :bankId")
    fun getQuestionCountForBank(bankId: Long): Flow<Int>

    @Query("SELECT questionBankId, COUNT(*) as count FROM questions GROUP BY questionBankId")
    fun getQuestionCountsByBank(): Flow<List<QuestionCountByBank>>

    @Query("SELECT * FROM questions WHERE questionBankId = :bankId AND isFavorite = 1 ORDER BY id")
    fun getFavoriteQuestionsForBank(bankId: Long): Flow<List<QuestionEntity>>

    @Query("SELECT COUNT(*) FROM questions WHERE isFavorite = 1")
    fun getTotalFavoriteCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM questions WHERE questionBankId = :bankId AND isFavorite = 1")
    fun getFavoriteCountForBank(bankId: Long): Flow<Int>

    @Query("UPDATE questions SET isFavorite = :isFavorite WHERE id = :questionId")
    suspend fun setFavorite(questionId: Long, isFavorite: Boolean)
}
