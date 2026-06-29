package mirujam.nekomemo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mirujam.nekomemo.data.local.MigrationErrorStore
import mirujam.nekomemo.data.local.NekoMemoDatabase
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class NekoMemoApplication : Application() {

    @Inject
    lateinit var database: NekoMemoDatabase

    @Inject
    lateinit var migrationErrorStore: MigrationErrorStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Trigger database opening in background to detect migration errors early
        // without blocking the main thread
        appScope.launch {
            try {
                database.openHelper.writableDatabase
            } catch (e: Exception) {
                migrationErrorStore.recordError(e.message ?: "Unknown migration error")
            }
        }
    }
}
