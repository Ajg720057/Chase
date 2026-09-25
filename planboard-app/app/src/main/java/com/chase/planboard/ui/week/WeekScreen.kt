package com.chase.planboard.ui.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chase.planboard.data.Periods
import com.chase.planboard.data.Scope
import com.chase.planboard.ui.BoardViewModel
import com.chase.planboard.ui.Navigator
import com.chase.planboard.ui.Tab
import com.chase.planboard.ui.common.Format
import com.chase.planboard.ui.common.MainMenu
import com.chase.planboard.ui.common.PeriodSwitcher
import com.chase.planboard.ui.common.PlanCard
import com.chase.planboard.ui.common.planSection
import com.chase.planboard.ui.common.swipeToShift
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayLabel = DateTimeFormatter.ofPattern("EEEE, MMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(board: BoardViewModel, navigator: Navigator) {
    val date by board.date.collectAsStateWithLifecycle()
    val state by board.board.collectAsStateWithLifecycle()
    val shift = { n: Long -> board.shift(Scope.WEEK, n) }
    val today = LocalDate.now()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Week") },
                actions = {
                    IconButton(onClick = { board.select(today) }) {
                        Icon(Icons.Filled.Today, contentDescription = "This week")
                    }
                    MainMenu(Scope.WEEK, date)
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigator.newPlan(Scope.WEEK, date) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Week plan") },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        val days = Periods.weekDays(date)

        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .swipeToShift(shift),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
        ) {
            item(key = "switcher") {
                PeriodSwitcher(
                    title = Format.week(date),
                    subtitle = if (today in days) "This week" else null,
                    onShift = shift,
                )
            }
            planSection(
                key = "week",
                title = "Plans for the week",
                plans = s.plansIn(Scope.WEEK, date),
                board = board,
                navigator = navigator,
                emptyText = "No week plans yet. Big goals for the week go here.",
                onAdd = { navigator.newPlan(Scope.WEEK, date) },
            )
            days.forEach { day ->
                val dayPlans = s.plansIn(Scope.DAY, day)
                item(key = "day-$day") {
                    DayHeader(
                        day = day,
                        isToday = day == today,
                        count = dayPlans.size,
                        onOpen = { navigator.show(Tab.DAY, day) },
                        onAdd = { navigator.newPlan(Scope.DAY, day) },
                    )
                }
                items(dayPlans, key = { "day-$day-${it.plan.id}" }) { item ->
                    PlanCard(
                        item = item,
                        onClick = { navigator.openPlan(item.plan.id) },
                        onStatusClick = { board.cycleStatus(item.plan) },
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, isToday: Boolean, count: Int, onOpen: () -> Unit, onAdd: () -> Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    day.format(dayLabel),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isToday) FontWeight.Bold else null,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when {
                        isToday && count == 0 -> "Today · nothing planned"
                        isToday -> "Today · $count planned"
                        count == 0 -> "Nothing planned"
                        else -> "$count planned"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add a plan for ${day.format(dayLabel)}")
            }
        }
    }
}
