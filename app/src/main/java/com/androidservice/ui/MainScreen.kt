package com.androidservice.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.androidservice.R
import com.androidservice.ui.screens.ConfigEditScreen
import com.androidservice.ui.screens.ConfigScreen
import com.androidservice.ui.screens.HomeScreen
import com.androidservice.ui.screens.LogsScreen
import com.androidservice.ui.screens.ManageScreen
import com.androidservice.viewmodel.MainViewModel

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    data object Home : Screen("home", R.string.nav_home, Icons.Filled.Home)
    data object Manage : Screen("manage", R.string.nav_files, Icons.AutoMirrored.Filled.Article)
    data object Logs : Screen("logs", R.string.nav_logs, Icons.AutoMirrored.Filled.List)
    data object Config : Screen("config", R.string.nav_settings, Icons.Filled.Settings)
    data object ConfigEdit : Screen("config_edit", R.string.edit_title, Icons.Filled.Settings)
}

@Composable
fun MainScreen(
    seedColor: Color,
    onSeedColorChange: (Color) -> Unit,
) {
    val viewModel: MainViewModel = viewModel()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val items = listOf(Screen.Home, Screen.Manage, Screen.Logs, Screen.Config)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val showFab = currentRoute == Screen.Manage.route
    val showBottomBar = currentRoute != Screen.ConfigEdit.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    items.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    screen.icon,
                                    contentDescription = stringResource(screen.titleRes),
                                    modifier = Modifier.padding(0.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(screen.titleRes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showFab,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        viewModel.setEditingFileName(null)
                        navController.navigate(Screen.ConfigEdit.route)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.nav_new_config),
                        modifier = Modifier.size(AppDimens.iconSize),
                    )
                }
            }
        },
    ) { innerPadding ->
        MainNavHost(
            navController = navController,
            viewModel = viewModel,
            seedColor = seedColor,
            onSeedColorChange = onSeedColorChange,
            snackbarHostState = snackbarHostState,
            fabClearance = if (showFab) AppDimens.fabClearance else 0.dp,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        )
    }
}

@Composable
fun MainNavHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    seedColor: Color,
    onSeedColorChange: (Color) -> Unit,
    snackbarHostState: SnackbarHostState,
    fabClearance: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val navigateToConfigEdit: (String?) -> Unit = { fileName ->
        viewModel.setEditingFileName(fileName)
        navController.navigate(Screen.ConfigEdit.route)
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                seedColor = seedColor,
                onSeedColorChange = onSeedColorChange,
            )
        }
        composable(Screen.Manage.route) {
            ManageScreen(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                listBottomPadding = fabClearance,
                onNavigateToConfigEdit = navigateToConfigEdit,
            )
        }
        composable(Screen.Logs.route) {
            LogsScreen(viewModel = viewModel)
        }
        composable(Screen.Config.route) {
            ConfigScreen(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onNavigateToConfigEdit = navigateToConfigEdit,
            )
        }
        composable(Screen.ConfigEdit.route) {
            val editingFileName by viewModel.editingFileName.collectAsStateWithLifecycle()
            ConfigEditScreen(
                fileName = editingFileName,
                onNavigateBack = {
                    viewModel.setEditingFileName(null)
                    navController.popBackStack()
                },
                viewModel = viewModel,
            )
        }
    }
}
