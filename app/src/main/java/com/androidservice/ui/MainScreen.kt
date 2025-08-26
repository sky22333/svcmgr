package com.androidservice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController


import com.androidservice.ui.screens.ConfigScreen
import com.androidservice.ui.screens.ConfigEditScreen
import com.androidservice.ui.screens.HomeScreen
import com.androidservice.ui.screens.LogsScreen
import com.androidservice.ui.screens.ManageScreen
import com.androidservice.viewmodel.MainViewModel


sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "主页", Icons.Filled.Home)
    object Manage : Screen("manage", "管理", Icons.Filled.Build)
    object Logs : Screen("logs", "日志", Icons.AutoMirrored.Filled.List)
    object Config : Screen("config", "配置", Icons.Filled.Settings)
    object ConfigEdit : Screen("config_edit", "配置编辑", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val viewModel: MainViewModel = viewModel()
    val navController = rememberNavController()
    val items = listOf(
        Screen.Home,
        Screen.Manage,
        Screen.Logs,
        Screen.Config
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    Scaffold(
        bottomBar = {
            NavigationBar(
                // 使用透明背景
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Screen.Manage.route) {
                FloatingActionButton(
                    onClick = { 
                        viewModel.setEditingFileName(null)
                        navController.navigate(Screen.ConfigEdit.route)
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "添加应用配置"
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        MainNavHost(
            navController = navController,
            viewModel = viewModel,
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
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(viewModel = viewModel)
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