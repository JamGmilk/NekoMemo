package mirujam.nekomemo.data.local

import org.json.JSONArray
import timber.log.Timber

object IntListJsonConverter {

    fun fromIntList(value: List<Int>): String {
        val array = JSONArray()
        value.forEach { array.put(it) }
        return array.toString()
    }

    fun toIntList(value: String): List<Int> {
        if (value.isBlank()) return emptyList()
        return try {
            val array = JSONArray(value)
            (0 until array.length()).map { array.getInt(it) }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse int list JSON")
            emptyList()
        }
    }
}
