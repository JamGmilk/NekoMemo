package mirujam.nekomemo.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mirujam.nekomemo.data.local.ListJsonConverter
import mirujam.nekomemo.data.local.NekoMemoDatabase
import mirujam.nekomemo.data.local.dao.QuestionBankDao
import mirujam.nekomemo.data.local.dao.QuestionDao
import mirujam.nekomemo.data.local.entity.QuestionCountByBank
import mirujam.nekomemo.data.local.entity.QuestionEntity
import mirujam.nekomemo.data.mapper.toDomainBankModels
import mirujam.nekomemo.data.mapper.toDomainModel
import mirujam.nekomemo.data.mapper.toDomainQuestionModels
import mirujam.nekomemo.data.mapper.toEntity
import mirujam.nekomemo.domain.model.Question
import mirujam.nekomemo.domain.model.QuestionBank
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

    fun searchQuestionsForBank(bankId: Long, query: String): Flow<List<Question>> =
        questionDao.searchQuestionsForBank(bankId, query).map { it.toDomainQuestionModels() }

    fun getPagedQuestionsForBank(bankId: Long): Flow<PagingData<Question>> =
        Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            pagingSourceFactory = { questionDao.getPagedQuestionsForBank(bankId) }
        ).flow.map { pagingData -> pagingData.map { it.toDomainModel() } }

    suspend fun getQuestionsForBankSync(bankId: Long): List<Question> =
        questionDao.getQuestionsForBankSync(bankId).toDomainQuestionModels()

    suspend fun getQuestionById(id: Long): Question? =
        questionDao.getQuestionById(id)?.toDomainModel()

    suspend fun insertQuestions(questions: List<Question>) {
        if (questions.isNotEmpty()) {
            questionDao.insertAll(questions.map { it.toEntity() })
        }
    }

    suspend fun updateQuestion(id: Long, questionBankId: Long, text: String, options: List<String>, correctIndices: List<Int>) {
        questionDao.updateQuestion(
            QuestionEntity(
                id = id,
                questionBankId = questionBankId,
                text = text,
                options = ListJsonConverter.fromStringList(options),
                correctIndices = mirujam.nekomemo.data.local.IntListJsonConverter.fromIntList(correctIndices)
            )
        )
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
