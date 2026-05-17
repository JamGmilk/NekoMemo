package mirujam.nekomemo.ui.shared

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "SharedDataStore"

        const val MAX_DATA_SIZE = 10 * 1024 * 1024

        private const val FILE_NAME = "nekomemo_extracted_data.json"
        private const val BACKUP_FILE_NAME = "nekomemo_extracted_data_backup.json"
    }

    private val dataFile: File by lazy {
        File(context.filesDir, FILE_NAME)
    }

    private val backupFile: File by lazy {
        File(context.filesDir, BACKUP_FILE_NAME)
    }

    private val mutex = Mutex()

    private val _extractedJson = MutableStateFlow<String?>(null)
    val extractedJson: StateFlow<String?> = _extractedJson.asStateFlow()

    init {
        loadFromFile()
        Log.d(TAG, "Initialized via Hilt. Data file: ${dataFile.absolutePath}")
    }

    suspend fun setExtractedJson(json: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (json.length > MAX_DATA_SIZE) {
                    Log.w(TAG, "JSON size ${json.length} exceeds limit $MAX_DATA_SIZE, truncating")
                }

                val safeJson = json.take(MAX_DATA_SIZE)

                createBackupIfNeeded(dataFile)

                FileOutputStream(dataFile).use { output ->
                    output.write(safeJson.toByteArray(StandardCharsets.UTF_8))
                }

                withContext(Dispatchers.Main) {
                    _extractedJson.value = safeJson
                }

                Log.d(TAG, "Saved JSON to file. Size: ${safeJson.length} chars")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving JSON to file", e)
                restoreFromBackup()
                false
            }
        }
    }

    suspend fun getExtractedJson(): String? {
        val cached = _extractedJson.value
        if (cached != null) {
            return cached
        }

        return withContext(Dispatchers.IO) {
            try {
                if (!dataFile.exists() || dataFile.length() == 0L) {
                    return@withContext null
                }

                val json = FileInputStream(dataFile).bufferedReader()
                    .use { it.readText() }

                withContext(Dispatchers.Main) {
                    _extractedJson.value = json
                }

                Log.d(TAG, "Loaded JSON from file. Size: ${json.length} chars")
                json
            } catch (e: Exception) {
                Log.e(TAG, "Error loading JSON from file", e)
                null
            }
        }
    }

    fun getExtractedJsonSync(): String? {
        return _extractedJson.value
    }

    suspend fun clearExtractedJson(): Boolean {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    if (dataFile.exists()) {
                        val deleted = dataFile.delete()
                        if (!deleted) {
                            Log.w(TAG, "Failed to delete data file")
                        }
                    }

                    cleanupBackup()

                    _extractedJson.value = null

                    Log.d(TAG, "Cleared extracted JSON")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing JSON", e)
                    false
                }
            }
        }
    }

    fun getDataSize(): Long {
        return try {
            dataFile.length()
        } catch (e: Exception) {
            0L
        }
    }

    fun isDataAvailable(): Boolean {
        return try {
            dataFile.exists() && dataFile.length() > 0L
        } catch (e: Exception) {
            false
        }
    }

    suspend fun cleanupOldFiles(): Int {
        return withContext(Dispatchers.IO) {
            var cleanedCount = 0

            try {
                val filesDir = context.filesDir

                filesDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("nekomemo_") &&
                        file.name.endsWith(".json") &&
                        file.name != FILE_NAME &&
                        file.name != BACKUP_FILE_NAME) {

                        val ageMs = System.currentTimeMillis() - file.lastModified()
                        val ageHours = ageMs / (1000 * 60 * 60)

                        if (ageHours > 24 && file.delete()) {
                            cleanedCount++
                            Log.d(TAG, "Cleaned up old file: ${file.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning old files", e)
            }

            cleanedCount
        }
    }

    private fun loadFromFile() {
        try {
            if (!dataFile.exists() || dataFile.length() == 0L) {
                Log.d(TAG, "No existing data file found")
                return
            }

            val json = FileInputStream(dataFile).bufferedReader().use { it.readText() }

            if (json.length > MAX_DATA_SIZE) {
                Log.w(TAG, "Loaded JSON exceeds size limit, truncating")
                _extractedJson.value = json.take(MAX_DATA_SIZE)
            } else {
                _extractedJson.value = json
            }

            Log.d(TAG, "Loaded JSON from file during init. Size: ${json.length} chars")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from file during init", e)
            restoreFromBackupSync()
        }
    }

    private fun createBackupIfNeeded(file: File) {
        try {
            if (file.exists()) {
                file.copyTo(backupFile, overwrite = true)
                Log.d(TAG, "Created backup before writing")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create backup", e)
        }
    }

    private fun restoreFromBackup() {
        try {
            if (backupFile.exists()) {
                backupFile.copyTo(dataFile, overwrite = true)
                Log.d(TAG, "Restored from backup after error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from backup", e)
        }
    }

    private fun restoreFromBackupSync() {
        try {
            if (backupFile.exists()) {
                val json = FileInputStream(backupFile).bufferedReader().use { it.readText() }
                _extractedJson.value = json

                if (dataFile.exists()) {
                    backupFile.copyTo(dataFile, overwrite = true)
                }
                Log.d(TAG, "Restored from backup synchronously")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from backup sync", e)
        }
    }

    private fun cleanupBackup() {
        try {
            if (backupFile.exists()) {
                val deleted = backupFile.delete()
                if (deleted) {
                    Log.d(TAG, "Cleaned up backup file")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning backup", e)
        }
    }
}
