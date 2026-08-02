package mirujam.nekomemo.ui.test

import mirujam.nekomemo.domain.model.PracticeMode
import mirujam.nekomemo.domain.model.Question
import mirujam.nekomemo.domain.model.QuestionType
import mirujam.nekomemo.ui.model.QuestionUiModel

object TestQuestionSelector {

    fun parseTypeCodes(typesFilter: String): Set<Int> {
        if (typesFilter.isBlank()) return emptySet()
        return typesFilter.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
    }

    fun encodeTypeCodes(types: Set<QuestionType>): String =
        types.map { it.code }.sorted().joinToString(",")

    fun select(
        questions: List<Question>,
        practiceMode: PracticeMode,
        typeCodes: Set<Int>,
        wrongIds: Set<Long>,
        questionCount: Int,
        shuffleQuestions: Boolean,
        shuffleOptions: Boolean
    ): List<QuestionUiModel> {
        var filtered = questions
        if (typeCodes.isNotEmpty()) {
            filtered = filtered.filter { it.type.code in typeCodes }
        }
        filtered = when (practiceMode) {
            PracticeMode.ALL -> filtered
            PracticeMode.WRONG -> filtered.filter { it.id in wrongIds }
            PracticeMode.FAVORITE -> filtered.filter { it.isFavorite }
        }

        var models = filtered.map { QuestionUiModel.fromDomainModel(it) }
        if (shuffleOptions) {
            models = models.map { model ->
                if (model.type == QuestionType.SINGLE_CHOICE || model.type == QuestionType.MULTIPLE_CHOICE) {
                    shuffleOptionsForModel(model)
                } else {
                    model
                }
            }
        }
        if (shuffleQuestions) {
            models = models.shuffled()
        }
        return applyQuestionCount(models, questionCount)
    }

    fun applyQuestionCount(questions: List<QuestionUiModel>, questionCount: Int): List<QuestionUiModel> {
        if (questions.isEmpty()) return emptyList()
        if (questionCount <= 0 || questionCount >= questions.size) return questions
        return questions.take(questionCount)
    }

    fun shuffleOptionsForModel(model: QuestionUiModel): QuestionUiModel {
        val shuffledOptions = model.options.shuffled()
        val newCorrectIndices = model.correctIndices.mapNotNull { oldIdx ->
            if (oldIdx in model.options.indices) {
                shuffledOptions.indexOf(model.options[oldIdx])
            } else {
                null
            }
        }
        return model.copy(options = shuffledOptions, correctIndices = newCorrectIndices)
    }
}
