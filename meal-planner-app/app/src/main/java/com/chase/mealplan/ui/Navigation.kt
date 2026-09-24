package com.chase.mealplan.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.chase.mealplan.MealPlanApp
import com.chase.mealplan.data.Slot
import com.chase.mealplan.ui.screens.GroceryScreen
import com.chase.mealplan.ui.screens.MealDetailScreen
import com.chase.mealplan.ui.screens.MealEditScreen
import com.chase.mealplan.ui.screens.MealEditViewModel
import com.chase.mealplan.ui.screens.MealsScreen
import com.chase.mealplan.ui.screens.PlanScreen
import java.time.LocalDate

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    PLAN("plan", "Plan", Icons.Filled.CalendarMonth),
    MEALS("meals", "Meals", Icons.AutoMirrored.Filled.MenuBook),
    GROCERY("grocery", "Grocery list", Icons.Filled.ShoppingCart),
}

/** Screen-to-screen moves, so screens don't need to know route strings. */
class Navigator(private val nav: NavHostController) {
    fun back() = nav.popBackStack()

    fun meal(mealId: Long, entryId: Long? = null) =
        nav.navigate("meal/$mealId?entryId=${entryId ?: -1}")

    /** Opens the editor. Pass [date]+[slot] to add to the plan, [entryId] to swap a planned meal. */
    fun edit(mealId: Long? = null, date: LocalDate? = null, slot: Slot? = null, entryId: Long? = null) =
        nav.navigate(
            "edit?mealId=${mealId ?: -1}&date=${date ?: ""}&slot=${slot?.name ?: ""}&entryId=${entryId ?: -1}",
        )

    fun groceryTab() = nav.navigate(Tab.GROCERY.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun MealPlanNavHost(showNextWeek: Int = 0) {
    val app = LocalContext.current.applicationContext as MealPlanApp
    val vm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(app) } })
    val nav = rememberNavController()
    val navigator = remember(nav) { Navigator(nav) }
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    // Opened from the weekly reminder: show next week's plan.
    LaunchedEffect(showNextWeek) {
        if (showNextWeek > 0) {
            vm.showNextWeek()
            // On a cold start the graph may not be set yet, but then we're on the plan already.
            runCatching {
                nav.navigate(Tab.PLAN.route) {
                    popUpTo(nav.graph.findStartDestination().id)
                    launchSingleTop = true
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (Tab.entries.any { it.route == route }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
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
            startDestination = Tab.PLAN.route,
            modifier = Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .consumeWindowInsets(padding),
        ) {
            composable(Tab.PLAN.route) { PlanScreen(vm, navigator) }
            composable(Tab.MEALS.route) { MealsScreen(vm, navigator) }
            composable(Tab.GROCERY.route) { GroceryScreen(vm) }
            composable(
                "meal/{mealId}?entryId={entryId}",
                arguments = listOf(
                    navArgument("mealId") { type = NavType.LongType },
                    navArgument("entryId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                val args = entry.arguments!!
                MealDetailScreen(
                    vm = vm,
                    mealId = args.getLong("mealId"),
                    entryId = args.getLong("entryId").takeIf { it >= 0 },
                    navigator = navigator,
                )
            }
            composable(
                "edit?mealId={mealId}&date={date}&slot={slot}&entryId={entryId}",
                arguments = listOf(
                    navArgument("mealId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("date") { type = NavType.StringType; defaultValue = "" },
                    navArgument("slot") { type = NavType.StringType; defaultValue = "" },
                    navArgument("entryId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                val args = entry.arguments!!
                val editVm: MealEditViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            MealEditViewModel(
                                app = app,
                                mealId = args.getLong("mealId").takeIf { it >= 0 },
                                date = args.getString("date").orEmpty().takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                                slot = args.getString("slot").orEmpty().takeIf { it.isNotEmpty() }?.let(Slot::valueOf),
                                entryId = args.getLong("entryId").takeIf { it >= 0 },
                            )
                        }
                    },
                )
                MealEditScreen(editVm, navigator)
            }
        }
    }
}
