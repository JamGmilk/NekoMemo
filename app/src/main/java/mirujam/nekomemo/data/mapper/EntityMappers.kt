package mirujam.nekomemo.data.mapper

import mirujam.nekomemo.data.local.IntListJsonConverter
import mirujam.nekomemo.data.local.ListJsonConverter
import mirujam.nekomemo.data.local.entity.QuestionBankEntity
import mirujam.nekomemo.data.local.entity.QuestionEntity
import mirujam.nekomemo.domain.model.Question
import mirujam.nekomemo.domain.model.QuestionBank

fun QuestionBankEntity.toDomainModel(): QuestionBank = QuestionBank(
    id = id,
    title = title,
    categoryId = categoryId,
    createdAt = createdAt
)

fun QuestionBank.toEntity(): QuestionBankEntity = QuestionBankEntity(
    id = id,
    title = title,
    categoryId = categoryId,
    createdAt = createdAt
)

fun QuestionEntity.toDomainModel(): Question = Question(
    id = id,
    questionBankId = questionBankId,
    text = text,
    options = ListJsonConverter.toStringList(options),
    correctIndices = IntListJsonConverter.toIntList(correctIndices),
    type = type
)

fun Question.toEntity(): QuestionEntity = QuestionEntity(
    id = id,
    questionBankId = questionBankId,
    text = text,
    options = ListJsonConverter.fromStringList(options),
    correctIndices = IntListJsonConverter.fromIntList(correctIndices),
    type = type
)

fun List<QuestionBankEntity>.toDomainBankModels(): List<QuestionBank> = map { it.toDomainModel() }

fun List<QuestionEntity>.toDomainQuestionModels(): List<Question> = map { it.toDomainModel() }
