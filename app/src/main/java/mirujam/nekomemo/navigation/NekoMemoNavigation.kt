package mirujam.nekomemo.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import mirujam.nekomemo.R
import mirujam.nekomemo.ui.detail.BankDetailScreen
import mirujam.nekomemo.ui.extract.ExtractScreen
import mirujam.nekomemo.ui.fetcher.FetcherScreen
import mirujam.nekomemo.ui.jsonimport.JsonImportScreen
import mirujam.nekomemo.ui.library.LibraryScreen
import mirujam.nekomemo.ui.settings.SettingsScreen
import mirujam.nekomemo.ui.test.TestScreen

private val TOP_LEVEL_ROUTES = setOf(Route.Library.route, Route.Settings.route)

data class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val labelResId: Int
)

val TOP_LEVEL_DESTINATIONS: List<TopLevelDestination> = listOf(
    TopLevelDestination(
        route = Route.Library.route,
        icon = Icons.Outlined.FolderOpen,
        labelResId = R.string.nav_library
    ),
    TopLevelDestination(
        route = Route.Settings.route,
        icon = Icons.Outlined.Settings,
        labelResId = R.string.nav_settings
    )
)

@Stable
class NekoMemoAppState(
    val navController: NavHostController,
    private val selectedTabRouteState: MutableState<String> = mutableStateOf(Route.Library.route)
) {
    private var selectedTabRoute: String
        get() = selectedTabRouteState.value
        set(value) { selectedTabRouteState.value = value }

    val currentTopLevelDestination: TopLevelDestination?
        @Composable get() {
            val currentRoute = navController
                .currentBackStackEntryAsState()
                .value
                ?.destination
                ?.route
            return TOP_LEVEL_DESTINATIONS.find { it.route == currentRoute }
                ?: TOP_LEVEL_DESTINATIONS.find { it.route == selectedTabRoute }
        }

    val isTopLevelRoute: Boolean
        @Composable get() {
            val currentRoute = navController
                .currentBackStackEntryAsState()
                .value
                ?.destination
                ?.route
            return currentRoute in TOP_LEVEL_ROUTES
        }

    fun navigateToTopLevelDestination(destination: TopLevelDestination) {
        selectedTabRoute = destination.route
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
fun rememberNekoMemoAppState(
    navController: NavHostController = rememberNavController()
): NekoMemoAppState {
    val selectedTabRoute = rememberSaveable { mutableStateOf(Route.Library.route) }
    return remember(navController) {
        NekoMemoAppState(navController, selectedTabRoute)
    }
}

@Composable
fun BottomNavBar(
    destinations: List<TopLevelDestination>,
    currentDestination: TopLevelDestination?,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        destinations.forEach { destination ->
            val label = stringResource(destination.labelResId)
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = label
                    )
                },
                label = { Text(text = label) },
                selected = currentDestination == destination,
                onClick = { onNavigateToDestination(destination) },
                alwaysShowLabel = false
            )
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val initial = initialState.destination.route ?: return false
    val target = targetState.destination.route ?: return false
    return initial in TOP_LEVEL_ROUTES && target in TOP_LEVEL_ROUTES
}

@Composable
fun NekoMemoNavigation(
    appState: NekoMemoAppState,
    modifier: Modifier = Modifier
) {
    val navController = appState.navController

    NavHost(
        navController = navController,
        startDestination = Route.Library.route,
        modifier = modifier,
        enterTransition = {
            if (isTabSwitch()) {
                if (targetState.destination.route == Route.Settings.route) {
                    slideInHorizontally(tween(200)) { it }
                } else {
                    slideInHorizontally(tween(200)) { -it }
                }
            } else {
                fadeIn(tween(300))
            }
        },
        exitTransition = {
            if (isTabSwitch()) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(300))
            }
        },
        popEnterTransition = {
            if (isTabSwitch()) {
                if (targetState.destination.route == Route.Settings.route) {
                    slideInHorizontally(tween(200)) { it }
                } else {
                    slideInHorizontally(tween(200)) { -it }
                }
            } else {
                fadeIn(tween(300))
            }
        },
        popExitTransition = {
            if (isTabSwitch()) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(300))
            }
        }
    ) {
        composable(Route.Library.route) {
            LibraryScreen(
                onBankClick = { bankId ->
                    navController.navigate(Route.Detail.createRoute(bankId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToFetcher = {
                    navController.navigate(Route.Fetcher.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToJsonImport = {
                    navController.navigate(Route.JsonImport.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Route.Settings.route) {
            SettingsScreen()
        }

        composable(Route.Fetcher.route) {
            FetcherScreen(
                onNavigateToExtract = {
                    navController.navigate(Route.Extract.route) {
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.JsonImport.route) {
            JsonImportScreen(
                onNavigateToExtract = {
                    navController.navigate(Route.Extract.route) {
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.Extract.route) {
            ExtractScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.Detail.route,
            arguments = listOf(
                navArgument("bankId") { type = NavType.LongType }
            )
        ) {
            BankDetailScreen(
                onStartTest = { id, count, shuffleQuestions, shuffleOptions ->
                    navController.navigate(Route.Test.createRoute(id, count, shuffleQuestions, shuffleOptions)) {
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.Test.route,
            arguments = listOf(
                navArgument("bankId") { type = NavType.LongType },
                navArgument("questionCount") { type = NavType.IntType },
                navArgument("shuffleQuestions") { type = NavType.BoolType; defaultValue = false },
                navArgument("shuffleOptions") { type = NavType.BoolType; defaultValue = false }
            )
        ) {
            TestScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
