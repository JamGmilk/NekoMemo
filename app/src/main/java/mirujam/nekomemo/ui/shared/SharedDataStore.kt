package mirujam.nekomemo.ui.shared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared in-memory storage for passing large data between screens.
 *
 * The extracted JSON (up to 5MB) is kept in memory via [MutableStateFlow] instead of
 * DataStore Preferences to avoid serializing the entire preferences file on every read/write.
 *
 * The save result message (small string) is still persisted in DataStore so it survives
 * process death when navigating from Extract back to Fetcher.
 */
@Singleton
class SharedDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val _extractedJson = MutableStateFlow<String?>(null)
    val extractedJson: StateFlow<String?> = _extractedJson.asStateFlow()

    private val SAVE_RESULT_KEY = stringPreferencesKey("save_result")

    private val saveResult: Flow<String?> = dataStore.data.map { preferences ->
        preferences[SAVE_RESULT_KEY]
    }

    fun setExtractedJson(json: String) {
        _extractedJson.value = json
    }

    fun getExtractedJson(): String? = _extractedJson.value

    fun clearExtractedJson() {
        _extractedJson.value = null
    }

    suspend fun setSaveResult(message: String): Boolean {
        return try {
            dataStore.edit { preferences ->
                preferences[SAVE_RESULT_KEY] = message
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist save result")
            false
        }
    }

    suspend fun getSaveResult(): String? {
        return try {
            dataStore.data.first()[SAVE_RESULT_KEY]
        } catch (e: Exception) {
            Timber.e(e, "Failed to read save result")
            null
        }
    }

    suspend fun clearSaveResult(): Boolean {
        return try {
            dataStore.edit { preferences ->
                preferences.remove(SAVE_RESULT_KEY)
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear save result")
            false
        }
    }
}
