package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.NotesRemindersScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WakeWordDiagnosticsScreen
import com.example.ui.theme.*
import com.example.ui.viewmodel.AssistantViewModel

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Onboarding : Screen("onboarding", "Onboarding", Icons.Filled.Person, Icons.Outlined.Person)
    object Assistant : Screen("assistant", "Assistant", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
    object History : Screen("history", "History", Icons.Filled.History, Icons.Outlined.History)
    object Notes : Screen("notes", "Notes", Icons.Filled.NoteAlt, Icons.Outlined.NoteAlt)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    object Memory : Screen("memory", "Memory", Icons.Filled.Psychology, Icons.Outlined.Psychology)
    object Diagnostics : Screen("diagnostics", "Diagnostics", Icons.Filled.Analytics, Icons.Outlined.Analytics)
}

class MainActivity : ComponentActivity() {

    private var assistantViewModel: AssistantViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KulsoomTheme {
                val navController = rememberNavController()
                val viewModel: AssistantViewModel = viewModel()
                assistantViewModel = viewModel

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
                val profiles by viewModel.profiles.collectAsStateWithLifecycle()

                val shouldShowOnboarding = !hasCompletedOnboarding && profiles.isEmpty()
                val startDestination = if (shouldShowOnboarding) Screen.Onboarding.route else Screen.Assistant.route

                LaunchedEffect(intent) {
                    handleWakeWordIntent(intent, viewModel, navController)
                }

                val items = listOf(
                    Screen.Assistant,
                    Screen.History,
                    Screen.Notes,
                    Screen.Settings
                )

                val showBottomNav = currentRoute != Screen.Onboarding.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = SpaceBlack,
                    bottomBar = {
                        if (showBottomNav) {
                            NavigationBar(
                                containerColor = SurfaceDark.copy(alpha = 0.98f),
                                tonalElevation = 6.dp,
                                windowInsets = WindowInsets.navigationBars,
                                modifier = Modifier.testTag("main_bottom_nav")
                            ) {
                                items.forEach { screen ->
                                    val selected = currentRoute == screen.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            if (currentRoute != screen.route) {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                                contentDescription = screen.title
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = screen.title,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.White,
                                            selectedTextColor = ProfessionalBlue,
                                            indicatorColor = ProfessionalBlue.copy(alpha = 0.35f),
                                            unselectedIconColor = TextMuted,
                                            unselectedTextColor = TextMuted
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(bottom = if (showBottomNav) innerPadding.calculateBottomPadding() else 0.dp)
                    ) {
                        composable(Screen.Onboarding.route) {
                            OnboardingScreen(
                                viewModel = viewModel,
                                onOnboardingComplete = {
                                    navController.navigate(Screen.Assistant.route) {
                                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable(Screen.Assistant.route) {
                            AssistantScreen(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable(Screen.History.route) {
                            HistoryScreen(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable(Screen.Notes.route) {
                            NotesRemindersScreen(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateToOnboarding = {
                                    navController.navigate(Screen.Onboarding.route)
                                },
                                onNavigateToMemory = {
                                    navController.navigate(Screen.Memory.route)
                                },
                                onNavigateToDiagnostics = {
                                    navController.navigate(Screen.Diagnostics.route)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable(Screen.Memory.route) {
                            MemoryScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable(Screen.Diagnostics.route) {
                            WakeWordDiagnosticsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToVoiceTraining = {
                                    navController.popBackStack()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        com.example.service.KulsoomWakeWordService.setAppForegroundState(true)
    }

    override fun onStop() {
        super.onStop()
        com.example.service.KulsoomWakeWordService.setAppForegroundState(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        assistantViewModel?.let { vm ->
            handleWakeWordIntent(intent, vm, null)
        }
    }

    private var lastWakeWordTriggerTime = 0L

    private fun handleWakeWordIntent(
        intent: Intent?,
        viewModel: AssistantViewModel,
        navController: androidx.navigation.NavController?
    ) {
        if (intent?.getBooleanExtra("WAKE_WORD_TRIGGER", false) == true) {
            intent.removeExtra("WAKE_WORD_TRIGGER")
            val now = System.currentTimeMillis()
            if (now - lastWakeWordTriggerTime < 2500L) {
                return
            }
            lastWakeWordTriggerTime = now
            navController?.let { nc ->
                try {
                    nc.navigate(Screen.Assistant.route) {
                        popUpTo(nc.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Could not navigate to assistant on wake-word: ${e.message}")
                }
            }
            viewModel.startListening()
        }
    }
}
