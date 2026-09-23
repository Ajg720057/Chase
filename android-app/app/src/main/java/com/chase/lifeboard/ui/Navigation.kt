package com.chase.lifeboard.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chase.lifeboard.LifeBoardApp
import com.chase.lifeboard.ui.calendar.CalendarScreen
import com.chase.lifeboard.ui.journal.JournalEditScreen
import com.chase.lifeboard.ui.journal.JournalScreen
import com.chase.lifeboard.ui.tasks.TaskEditScreen
import com.chase.lifeboard.ui.tasks.TaskListScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TASKS("tasks", "Tasks", Icons.Filled.Checklist),
    CALENDAR("calendar", "Calendar", Icons.Filled.CalendarMonth),
    JOURNAL("journal", "Journal", Icons.AutoMirrored.Filled.MenuBook),
}

@Composable
fun lifeBoardApp(): LifeBoardApp = LocalContext.current.applicationContext as LifeBoardApp

/** Actions shared by every screen for creating and opening items. */
class Navigator(
    private val nav: NavHostController,
    private val app: LifeBoardApp,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    fun openTask(id: Long) = nav.navigate("task/$id?isNew=false")
    fun openEntry(id: Long) = nav.navigate("entry/$id?isNew=false")

    fun newTask(parentId: Long? = null, dueAt: Long? = null) = scope.launch {
        val id = app.tasks.create(parentId = parentId, dueAt = dueAt)
        nav.navigate("task/$id?isNew=true")
    }

    fun newEntry(day: LocalDate = LocalDate.now()) = scope.launch {
        val id = app.journal.create(day.toEpochDay())
        nav.navigate("entry/$id?isNew=true")
    }

    fun back() = nav.popBackStack()
}

@Composable
fun LifeBoardNavHost(openTaskId: Long?, onOpenTaskHandled: () -> Unit) {
    val nav = rememberNavController()
    val app = lifeBoardApp()
    val scope = rememberCoroutineScope()
    val navigator = Navigator(nav, app, scope)
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == currentRoute }

    LaunchedEffect(openTaskId) {
        if (openTaskId != null) {
            navigator.openTask(openTaskId)
            onOpenTaskHandled()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.TASKS.route,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Tab.TASKS.route) { TaskListScreen(navigator) }
            composable(Tab.CALENDAR.route) { CalendarScreen(navigator) }
            composable(Tab.JOURNAL.route) { JournalScreen(navigator) }
            composable(
                "task/{id}?isNew={isNew}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("isNew") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                TaskEditScreen(
                    taskId = entry.arguments!!.getLong("id"),
                    isNew = entry.arguments!!.getBoolean("isNew"),
                    navigator = navigator,
                )
            }
            composable(
                "entry/{id}?isNew={isNew}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("isNew") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                JournalEditScreen(
                    entryId = entry.arguments!!.getLong("id"),
                    isNew = entry.arguments!!.getBoolean("isNew"),
                    navigator = navigator,
                )
            }
        }
    }
}
