package mirujam.nekomemo.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TestPreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val DIRECT_ANSWER_KEY = booleanPreferencesKey("direct_answer")
        val AUTO_NEXT_ON_CORRECT_KEY = booleanPreferencesKey("auto_next_on_correct")
    }

    val directAnswer: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DIRECT_ANSWER_KEY] ?: false
    }

    val autoNextOnCorrect: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_NEXT_ON_CORRECT_KEY] ?: false
    }

    suspend fun setDirectAnswer(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DIRECT_ANSWER_KEY] = enabled
        }
    }

    suspend fun setAutoNextOnCorrect(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_NEXT_ON_CORRECT_KEY] = enabled
        }
    }
}
