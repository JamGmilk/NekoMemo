package mirujam.nekomemo.data.local

import mirujam.nekomemo.data.local.entity.QuestionStatsEntity
import mirujam.nekomemo.data.local.entity.TestSessionEntity
import mirujam.nekomemo.domain.model.BankMasteryInfo
import mirujam.nekomemo.domain.model.PracticeMode
import mirujam.nekomemo.domain.model.QuestionStats
import mirujam.nekomemo.domain.model.TestSession
import org.json.JSONArray
import org.json.JSONObject

fun QuestionStatsEntity.toDomain(): QuestionStats = QuestionStats(
    questionId = questionId,
    attemptCount = attemptCount,
    correctCount = correctCount,
    wrongCount = wrongCount,
    lastPracticedAt = lastPracticedAt,
    inWrongBook = inWrongBook
)

fun QuestionStats.toEntity(): QuestionStatsEntity = QuestionStatsEntity(
    questionId = questionId,
    attemptCount = attemptCount,
    correctCount = correctCount,
    wrongCount = wrongCount,
    lastPracticedAt = lastPracticedAt,
    inWrongBook = inWrongBook
)

fun TestSessionEntity.toDomain(): TestSession = TestSession(
    bankId = bankId,
    questionIds = jsonToLongList(questionIdsJson),
    currentIndex = currentIndex,
    selectedAnswers = jsonToSelectedAnswers(selectedAnswersJson),
    textAnswers = jsonToTextAnswers(textAnswersJson),
    revealedQuestions = jsonToIntSet(revealedJson),
    shuffleQuestions = shuffleQuestions,
    shuffleOptions = shuffleOptions,
    practiceMode = PracticeMode.fromString(practiceMode),
    typesFilter = typesFilter,
    updatedAt = updatedAt
)

fun TestSession.toEntity(): TestSessionEntity = TestSessionEntity(
    bankId = bankId,
    questionIdsJson = longListToJson(questionIds),
    currentIndex = currentIndex,
    selectedAnswersJson = selectedAnswersToJson(selectedAnswers),
    textAnswersJson = textAnswersToJson(textAnswers),
    revealedJson = intSetToJson(revealedQuestions),
    shuffleQuestions = shuffleQuestions,
    shuffleOptions = shuffleOptions,
    practiceMode = practiceMode.name,
    typesFilter = typesFilter,
    updatedAt = updatedAt
)

fun toBankMasteryInfo(
    bankId: Long,
    attemptCount: Int,
    correctCount: Int,
    wrongBookCount: Int
): BankMasteryInfo {
    val masteryPercent = if (attemptCount > 0) {
        (correctCount * 100) / attemptCount
    } else {
        null
    }
    return BankMasteryInfo(
        bankId = bankId,
        attemptCount = attemptCount,
        correctCount = correctCount,
        wrongBookCount = wrongBookCount,
        masteryPercent = masteryPercent
    )
}

private fun longListToJson(ids: List<Long>): String {
    val arr = JSONArray()
    ids.forEach { arr.put(it) }
    return arr.toString()
}

private fun jsonToLongList(json: String): List<Long> = try {
    val arr = JSONArray(json)
    buildList {
        for (i in 0 until arr.length()) {
            add(arr.getLong(i))
        }
    }
} catch (_: Exception) {
    emptyList()
}

private fun intSetToJson(values: Set<Int>): String {
    val arr = JSONArray()
    values.sorted().forEach { arr.put(it) }
    return arr.toString()
}

private fun jsonToIntSet(json: String): Set<Int> = try {
    val arr = JSONArray(json)
    buildSet {
        for (i in 0 until arr.length()) {
            add(arr.getInt(i))
        }
    }
} catch (_: Exception) {
    emptySet()
}

private fun selectedAnswersToJson(map: Map<Int, Set<Int>>): String {
    val obj = JSONObject()
    map.forEach { (key, value) ->
        val arr = JSONArray()
        value.sorted().forEach { arr.put(it) }
        obj.put(key.toString(), arr)
    }
    return obj.toString()
}

private fun jsonToSelectedAnswers(json: String): Map<Int, Set<Int>> = try {
    val obj = JSONObject(json)
    buildMap {
        obj.keys().forEach { key ->
            val arr = obj.getJSONArray(key)
            val set = buildSet {
                for (i in 0 until arr.length()) {
                    add(arr.getInt(i))
                }
            }
            put(key.toInt(), set)
        }
    }
} catch (_: Exception) {
    emptyMap()
}

private fun textAnswersToJson(map: Map<Int, List<String>>): String {
    val obj = JSONObject()
    map.forEach { (key, value) ->
        val arr = JSONArray()
        value.forEach { arr.put(it) }
        obj.put(key.toString(), arr)
    }
    return obj.toString()
}

private fun jsonToTextAnswers(json: String): Map<Int, List<String>> = try {
    val obj = JSONObject(json)
    buildMap {
        obj.keys().forEach { key ->
            val arr = obj.getJSONArray(key)
            val list = buildList {
                for (i in 0 until arr.length()) {
                    add(arr.getString(i))
                }
            }
            put(key.toInt(), list)
        }
    }
} catch (_: Exception) {
    emptyMap()
}
