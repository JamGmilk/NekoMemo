package mirujam.nekomemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import mirujam.nekomemo.data.local.MigrationErrorStore
import mirujam.nekomemo.data.preferences.ThemeMode
import mirujam.nekomemo.data.preferences.ThemePreferenceRepository
import mirujam.nekomemo.navigation.BottomNavBar
import mirujam.nekomemo.navigation.NekoMemoNavigation
import mirujam.nekomemo.navigation.Route
import mirujam.nekomemo.ui.component.LocalSnackbarHostState
import mirujam.nekomemo.ui.theme.NekoMemoTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themePreferenceRepository: ThemePreferenceRepository

    @Inject
    lateinit var migrationErrorStore: MigrationErrorStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferenceRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            NekoMemoTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val showBottomBar = currentRoute in listOf(Route.Library.route, Route.Settings.route)
                val migrationErrorTemplate = stringResource(R.string.migration_error)
                val migrationErrorTitle = stringResource(R.string.migration_error_title)
                var showMigrationErrorDialog by remember { mutableStateOf(false) }
                var migrationErrorMessage by remember { mutableStateOf("") }

                CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            if (showBottomBar) {
                                BottomNavBar(
                                    currentRoute = currentRoute ?: Route.Library.route,
                                    onNavigate = { route ->
                                        navController.navigate(route.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        },
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { innerPadding ->
                        NekoMemoNavigation(
                            navController = navController,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    delay(500)
                    if (migrationErrorStore.hasFailed()) {
                        val errorMessage = migrationErrorStore.getLastError()
                        migrationErrorMessage = migrationErrorTemplate.format(errorMessage ?: "Unknown error")
                        showMigrationErrorDialog = true
                    }
                }

                if (showMigrationErrorDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showMigrationErrorDialog = false
                            migrationErrorStore.clearError()
                        },
                        title = { Text(migrationErrorTitle) },
                        text = { Text(migrationErrorMessage) },
                        confirmButton = {
                            TextButton(onClick = {
                                showMigrationErrorDialog = false
                                migrationErrorStore.clearError()
                            }) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    )
                }
            }
        }
    }
}
