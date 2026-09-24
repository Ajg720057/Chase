package com.chase.planboard.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chase.planboard.PlanBoardApp
import com.chase.planboard.data.Scope
import com.chase.planboard.ui.day.DayScreen
import com.chase.planboard.ui.month.MonthScreen
import com.chase.planboard.ui.plan.PlanEditScreen
import com.chase.planboard.ui.week.WeekScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    DAY("day", "Day", Icons.Filled.Today),
    WEEK("week", "Week", Icons.Filled.CalendarViewWeek),
    MONTH("month", "Month", Icons.Filled.CalendarMonth),
}

@Composable
fun planBoardApp(): PlanBoardApp = LocalContext.current.applicationContext as PlanBoardApp

/** Actions shared by every screen for moving around and creating plans. */
class Navigator(
    private val nav: NavHostController,
    private val app: PlanBoardApp,
    private val scope: CoroutineScope,
    private val board: BoardViewModel,
) {
    fun openPlan(id: Long, isNew: Boolean = false) = nav.navigate("plan/$id?isNew=$isNew")

    fun newPlan(scope: Scope, date: LocalDate, parentId: Long? = null) = this.scope.launch {
        val id = app.plans.create(scope, date, parentId)
        nav.navigate("plan/$id?isNew=true")
    }

    /** Switches to a tab, showing the period that holds [date]. */
    fun show(tab: Tab, date: LocalDate? = null) {
        date?.let(board::select)
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun back() = nav.popBackStack()
}

@Composable
fun PlanBoardNavHost() {
    val nav = rememberNavController()
    val app = planBoardApp()
    val scope = rememberCoroutineScope()
    val board: BoardViewModel = viewModel(factory = viewModelFactory { initializer { BoardViewModel(app) } })
    val navigator = Navigator(nav, app, scope, board)
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigator.show(tab) },
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
            startDestination = Tab.DAY.route,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Tab.DAY.route) { DayScreen(board, navigator) }
            composable(Tab.WEEK.route) { WeekScreen(board, navigator) }
            composable(Tab.MONTH.route) { MonthScreen(board, navigator) }
            composable(
                "plan/{id}?isNew={isNew}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("isNew") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                PlanEditScreen(
                    planId = entry.arguments!!.getLong("id"),
                    isNew = entry.arguments!!.getBoolean("isNew"),
                    navigator = navigator,
                )
            }
        }
    }
}
