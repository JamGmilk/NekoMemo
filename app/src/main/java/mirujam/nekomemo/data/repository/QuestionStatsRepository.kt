package mirujam.nekomemo.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mirujam.nekomemo.data.local.dao.QuestionStatsDao
import mirujam.nekomemo.data.local.dao.TestSessionDao
import mirujam.nekomemo.data.local.toBankMasteryInfo
import mirujam.nekomemo.data.local.toDomain
import mirujam.nekomemo.data.local.toEntity
import mirujam.nekomemo.domain.model.BankMasteryInfo
import mirujam.nekomemo.domain.model.QuestionStats
import mirujam.nekomemo.domain.model.TestSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionStatsRepository @Inject constructor(
    private val statsDao: QuestionStatsDao,
    private val sessionDao: TestSessionDao
) {

    fun getWrongBookQuestionIdsForBank(bankId: Long): Flow<List<Long>> =
        statsDao.getWrongBookQuestionIdsForBank(bankId)

    fun getWrongBookCountForBank(bankId: Long): Flow<Int> =
        statsDao.getWrongBookCountForBank(bankId)

    fun getTotalWrongBookCount(): Flow<Int> =
        statsDao.getTotalWrongBookCount()

    fun getTotalAttemptCount(): Flow<Int> =
        statsDao.getTotalAttemptCount()

    fun getBankMastery(): Flow<Map<Long, BankMasteryInfo>> =
        statsDao.getBankMasteryRows().map { rows ->
            rows.associate { row ->
                row.bankId to toBankMasteryInfo(
                    bankId = row.bankId,
                    attemptCount = row.attemptCount,
                    correctCount = row.correctCount,
                    wrongBookCount = row.wrongBookCount
                )
            }
        }

    suspend fun recordAttempt(questionId: Long, isCorrect: Boolean, removeFromWrongBookOnCorrect: Boolean) {
        val now = System.currentTimeMillis()
        val existing = statsDao.getStats(questionId)?.toDomain()
        val updated = if (existing == null) {
            QuestionStats(
                questionId = questionId,
                attemptCount = 1,
                correctCount = if (isCorrect) 1 else 0,
                wrongCount = if (isCorrect) 0 else 1,
                lastPracticedAt = now,
                inWrongBook = !isCorrect
            )
        } else {
            existing.copy(
                attemptCount = existing.attemptCount + 1,
                correctCount = existing.correctCount + if (isCorrect) 1 else 0,
                wrongCount = existing.wrongCount + if (isCorrect) 0 else 1,
                lastPracticedAt = now,
                inWrongBook = when {
                    !isCorrect -> true
                    removeFromWrongBookOnCorrect -> false
                    else -> existing.inWrongBook
                }
            )
        }
        statsDao.upsert(updated.toEntity())
    }

    suspend fun removeFromWrongBook(questionId: Long) {
        statsDao.removeFromWrongBook(questionId)
    }

    suspend fun getSession(bankId: Long): TestSession? =
        sessionDao.getSession(bankId)?.toDomain()

    fun getSessionFlow(bankId: Long): Flow<TestSession?> =
        sessionDao.getSessionFlow(bankId).map { it?.toDomain() }

    suspend fun saveSession(session: TestSession) {
        sessionDao.upsert(session.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun clearSession(bankId: Long) {
        sessionDao.clearSession(bankId)
    }
}
