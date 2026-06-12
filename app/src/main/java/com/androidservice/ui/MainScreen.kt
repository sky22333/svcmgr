package com.androidservice.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.androidservice.ui.screens.ConfigEditScreen
import com.androidservice.ui.screens.ConfigScreen
import com.androidservice.ui.screens.HomeScreen
import com.androidservice.ui.screens.LogsScreen
import com.androidservice.ui.screens.ManageScreen
import com.androidservice.viewmodel.MainViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "首页", Icons.Filled.Home)
    data object Manage : Screen("manage", "文件", Icons.AutoMirrored.Filled.Article)
    data object Logs : Screen("logs", "日志", Icons.AutoMirrored.Filled.List)
    data object Config : Screen("config", "设置", Icons.Filled.Settings)
    data object ConfigEdit : Screen("config_edit", "编辑配置", Icons.Filled.Settings)
}

@Composable
fun MainScreen(
    seedColor: Color,
    onSeedColorChange: (Color) -> Unit
) {
    val viewModel: MainViewModel = viewModel()
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Manage, Screen.Logs, Screen.Config)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = currentRoute != Screen.ConfigEdit.route,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
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
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { androidx.compose.material3.Text(screen.title) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = currentRoute == Screen.Manage.route,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        viewModel.setEditingFileName(null)
                        navController.navigate(Screen.ConfigEdit.route)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "新建配置")
                }
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        MainNavHost(
            navController = navController,
            viewModel = viewModel,
            seedColor = seedColor,
            onSeedColorChange = onSeedColorChange,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        )
    }
}

@Composable
fun MainNavHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    seedColor: Color,
    onSeedColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                seedColor = seedColor,
                onSeedColorChange = onSeedColorChange
            )
        }
        composable(Screen.Manage.route) {
            ManageScreen(
                viewModel = viewModel,
                onNavigateToConfigEdit = { fileName ->
                    viewModel.setEditingFileName(fileName)
                    navController.navigate(Screen.ConfigEdit.route)
                }
            )
        }
        composable(Screen.Logs.route) {
            LogsScreen(viewModel = viewModel)
        }
        composable(Screen.Config.route) {
            ConfigScreen(viewModel = viewModel)
        }
        composable(Screen.ConfigEdit.route) {
            val editingFileName by viewModel.editingFileName.collectAsStateWithLifecycle()
            ConfigEditScreen(
                fileName = editingFileName,
                onNavigateBack = {
                    viewModel.setEditingFileName(null)
                    navController.popBackStack()
                },
                viewModel = viewModel
            )
        }
    }
}
