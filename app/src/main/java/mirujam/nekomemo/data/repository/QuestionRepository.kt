package mirujam.nekomemo.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mirujam.nekomemo.data.local.NekoMemoDatabase
import mirujam.nekomemo.data.local.dao.QuestionBankDao
import mirujam.nekomemo.data.local.dao.QuestionDao
import mirujam.nekomemo.data.local.entity.QuestionCountByBank
import mirujam.nekomemo.data.mapper.toDomainBankModels
import mirujam.nekomemo.data.mapper.toDomainModel
import mirujam.nekomemo.data.mapper.toDomainQuestionModels
import mirujam.nekomemo.data.mapper.toEntity
import mirujam.nekomemo.domain.model.Question
import mirujam.nekomemo.domain.model.QuestionBank
import mirujam.nekomemo.domain.model.QuestionType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionRepository @Inject constructor(
    private val questionBankDao: QuestionBankDao,
    private val questionDao: QuestionDao,
    private val database: NekoMemoDatabase
) {

    fun getAllBanks(): Flow<List<QuestionBank>> =
        questionBankDao.getAllBanks().map { it.toDomainBankModels() }

    suspend fun getBankById(id: Long): QuestionBank? =
        questionBankDao.getBankById(id)?.toDomainModel()

    fun getBankByIdFlow(id: Long): Flow<QuestionBank?> =
        questionBankDao.getBankByIdFlow(id).map { it?.toDomainModel() }

    suspend fun insertBank(bank: QuestionBank): Long =
        questionBankDao.insertBank(bank.toEntity())

    suspend fun updateBank(bank: QuestionBank) =
        questionBankDao.updateBank(bank.toEntity())

    suspend fun deleteBank(bank: QuestionBank) =
        questionBankDao.deleteBank(bank.toEntity())

    fun getBankCount(): Flow<Int> =
        questionBankDao.getBankCount()

    fun getTotalQuestionCount(): Flow<Int> =
        questionDao.getTotalQuestionCount()

    fun getQuestionCountsByBank(): Flow<List<QuestionCountByBank>> =
        questionDao.getQuestionCountsByBank()

    fun getQuestionCountForBank(bankId: Long): Flow<Int> =
        questionDao.getQuestionCountForBank(bankId)

    fun getQuestionsForBank(bankId: Long): Flow<List<Question>> =
        questionDao.getQuestionsForBank(bankId).map { it.toDomainQuestionModels() }

    /** 统一查询：query 为空时返回所有题目，非空时按文本搜索 */
    fun queryQuestionsForBank(bankId: Long, query: String): Flow<List<Question>> =
        questionDao.queryQuestionsForBank(bankId, query).map { it.toDomainQuestionModels() }

    suspend fun getQuestionsForBankSync(bankId: Long): List<Question> =
        questionDao.getQuestionsForBankSync(bankId).toDomainQuestionModels()

    suspend fun getQuestionById(id: Long): Question? =
        questionDao.getQuestionById(id)?.toDomainModel()

    suspend fun insertQuestions(questions: List<Question>) {
        if (questions.isNotEmpty()) {
            questionDao.insertAll(questions.map { it.toEntity() })
        }
    }

    suspend fun createBankWithQuestions(bank: QuestionBank, questions: List<Question>): Long =
        database.withTransaction {
            val bankId = questionBankDao.insertBank(bank.toEntity())
            if (questions.isNotEmpty()) {
                questionDao.insertAll(questions.map { it.copy(questionBankId = bankId).toEntity() })
            }
            bankId
        }

    suspend fun updateQuestion(
        id: Long,
        questionBankId: Long,
        text: String,
        options: List<String>,
        correctIndices: List<Int>,
        type: QuestionType,
        isFavorite: Boolean = false
    ) {
        questionDao.updateQuestion(
            Question(id, questionBankId, text, options, correctIndices, type, isFavorite).toEntity()
        )
    }

    suspend fun setFavorite(questionId: Long, isFavorite: Boolean) {
        questionDao.setFavorite(questionId, isFavorite)
    }

    fun getFavoriteQuestionsForBank(bankId: Long): Flow<List<Question>> =
        questionDao.getFavoriteQuestionsForBank(bankId).map { it.toDomainQuestionModels() }

    fun getTotalFavoriteCount(): Flow<Int> =
        questionDao.getTotalFavoriteCount()

    fun getFavoriteCountForBank(bankId: Long): Flow<Int> =
        questionDao.getFavoriteCountForBank(bankId)

    suspend fun appendQuestionsToBank(bankId: Long, questions: List<Question>) {
        if (questions.isEmpty()) return
        database.withTransaction {
            questionDao.insertAll(
                questions.map { it.copy(id = 0, questionBankId = bankId).toEntity() }
            )
        }
    }

    /**
     * Appends all questions from [sourceBankId] into [targetBankId].
     * When [deleteSource] is true, deletes the source bank afterwards.
     */
    suspend fun mergeBanks(sourceBankId: Long, targetBankId: Long, deleteSource: Boolean): Boolean {
        if (sourceBankId == targetBankId) return false
        return database.withTransaction {
            val source = questionBankDao.getBankById(sourceBankId) ?: return@withTransaction false
            val target = questionBankDao.getBankById(targetBankId) ?: return@withTransaction false
            val questions = questionDao.getQuestionsForBankSync(source.id)
            if (questions.isNotEmpty()) {
                questionDao.insertAll(
                    questions.map { it.copy(id = 0, questionBankId = target.id) }
                )
            }
            if (deleteSource) {
                questionBankDao.deleteBank(source)
            }
            true
        }
    }

    suspend fun deleteQuestion(question: Question) =
        questionDao.deleteQuestion(question.toEntity())

    suspend fun deleteAllData() = database.withTransaction {
        questionDao.deleteAll()
        questionBankDao.deleteAll()
    }

    suspend fun duplicateBank(bankId: Long): Long {
        return database.withTransaction {
            val originalBank = questionBankDao.getBankById(bankId) ?: return@withTransaction -1L
            val newBankId = questionBankDao.insertBank(
                originalBank.copy(
                    id = 0,
                    title = "${originalBank.title} (Copy)",
                    createdAt = System.currentTimeMillis()
                )
            )
            val questions = questionDao.getQuestionsForBankSync(bankId)
            if (questions.isNotEmpty()) {
                questionDao.insertAll(questions.map { it.copy(id = 0, questionBankId = newBankId) })
            }
            newBankId
        }
    }
}
