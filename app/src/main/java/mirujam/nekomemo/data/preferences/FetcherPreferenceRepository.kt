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
class FetcherPreferenceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val FETCHER_GUIDE_DISMISSED = booleanPreferencesKey("fetcher_guide_dismissed")
    }

    val isGuideDismissed: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[FETCHER_GUIDE_DISMISSED] ?: false
    }

    suspend fun dismissGuide() {
        dataStore.edit { preferences ->
            preferences[FETCHER_GUIDE_DISMISSED] = true
        }
    }
}
