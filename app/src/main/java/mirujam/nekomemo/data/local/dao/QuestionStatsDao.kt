package mirujam.nekomemo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import mirujam.nekomemo.data.local.entity.QuestionStatsEntity

@Dao
interface QuestionStatsDao {

    @Query("SELECT * FROM question_stats WHERE questionId = :questionId")
    suspend fun getStats(questionId: Long): QuestionStatsEntity?

    @Query("SELECT * FROM question_stats WHERE questionId IN (:questionIds)")
    suspend fun getStatsForQuestions(questionIds: List<Long>): List<QuestionStatsEntity>

    @Upsert
    suspend fun upsert(stats: QuestionStatsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stats: List<QuestionStatsEntity>)

    @Query("SELECT questionId FROM question_stats WHERE inWrongBook = 1")
    fun getWrongBookQuestionIds(): Flow<List<Long>>

    @Query(
        """
        SELECT qs.questionId FROM question_stats qs
        INNER JOIN questions q ON q.id = qs.questionId
        WHERE qs.inWrongBook = 1 AND q.questionBankId = :bankId
        """
    )
    fun getWrongBookQuestionIdsForBank(bankId: Long): Flow<List<Long>>

    @Query(
        """
        SELECT COUNT(*) FROM question_stats qs
        INNER JOIN questions q ON q.id = qs.questionId
        WHERE qs.inWrongBook = 1 AND q.questionBankId = :bankId
        """
    )
    fun getWrongBookCountForBank(bankId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM question_stats WHERE inWrongBook = 1")
    fun getTotalWrongBookCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(attemptCount), 0) FROM question_stats")
    fun getTotalAttemptCount(): Flow<Int>

    @Query(
        """
        SELECT
            q.questionBankId AS bankId,
            COALESCE(SUM(qs.attemptCount), 0) AS attemptCount,
            COALESCE(SUM(qs.correctCount), 0) AS correctCount,
            COALESCE(SUM(CASE WHEN qs.inWrongBook = 1 THEN 1 ELSE 0 END), 0) AS wrongBookCount
        FROM questions q
        LEFT JOIN question_stats qs ON qs.questionId = q.id
        GROUP BY q.questionBankId
        """
    )
    fun getBankMasteryRows(): Flow<List<BankMasteryRow>>

    @Query("UPDATE question_stats SET inWrongBook = 0 WHERE questionId = :questionId")
    suspend fun removeFromWrongBook(questionId: Long)

    @Query("DELETE FROM question_stats")
    suspend fun deleteAll()
}

data class BankMasteryRow(
    val bankId: Long,
    val attemptCount: Int,
    val correctCount: Int,
    val wrongBookCount: Int
)
