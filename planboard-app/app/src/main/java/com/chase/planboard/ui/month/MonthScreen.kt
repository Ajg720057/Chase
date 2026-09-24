package com.chase.planboard.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chase.planboard.data.Periods
import com.chase.planboard.data.Scope
import com.chase.planboard.data.startDate
import com.chase.planboard.ui.BoardViewModel
import com.chase.planboard.ui.Navigator
import com.chase.planboard.ui.PlanSummary
import com.chase.planboard.ui.Tab
import com.chase.planboard.ui.common.Format
import com.chase.planboard.ui.common.MainMenu
import com.chase.planboard.ui.common.PeriodSwitcher
import com.chase.planboard.ui.common.planSection
import com.chase.planboard.ui.common.swipeToShift
import com.chase.planboard.ui.theme.StatusColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(board: BoardViewModel, navigator: Navigator) {
    val date by board.date.collectAsStateWithLifecycle()
    val state by board.board.collectAsStateWithLifecycle()
    val shift = { n: Long -> board.shift(Scope.MONTH, n) }
    val month = YearMonth.from(date)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Month") },
                actions = {
                    IconButton(onClick = { board.select(LocalDate.now()) }) {
                        Icon(Icons.Filled.Today, contentDescription = "Today")
                    }
                    MainMenu()
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigator.newPlan(Scope.MONTH, date) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Month plan") },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        val grid = Periods.monthGrid(month)
        // Week plans for any week that touches this month.
        val weekPlans = s.plans
            .filter { it.plan.scope == Scope.WEEK }
            .filter { p ->
                val start = Periods.weekStart(p.plan.startDate)
                !start.plusDays(6).isBefore(month.atDay(1)) && !start.isAfter(month.atEndOfMonth())
            }

        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
        ) {
            item(key = "grid") {
                Column(Modifier.swipeToShift(shift)) {
                    PeriodSwitcher(title = Format.month(month), subtitle = null, onShift = shift)
                    MonthGrid(
                        month = month,
                        days = grid,
                        selected = date,
                        plansByDate = s.dayPlansByDate,
                        onSelect = board::select,
                    )
                }
            }
            planSection(
                key = "month",
                title = "Plans for the month",
                plans = s.plansIn(Scope.MONTH, date),
                board = board,
                navigator = navigator,
                emptyText = "No month plans yet. Set the big goals for ${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} here.",
                onAdd = { navigator.newPlan(Scope.MONTH, date) },
            )
            planSection(
                key = "weeks",
                title = "Week plans",
                plans = weekPlans,
                board = board,
                navigator = navigator,
                emptyText = null,
                onAdd = { navigator.newPlan(Scope.WEEK, date) },
                subtitle = { Format.period(it.plan) },
            )
            planSection(
                key = "selected",
                title = Format.relativeDay(date),
                plans = s.plansIn(Scope.DAY, date),
                board = board,
                navigator = navigator,
                emptyText = "Nothing planned for this day.",
                onAdd = { navigator.newPlan(Scope.DAY, date) },
            )
            item(key = "open-day") {
                TextButton(onClick = { navigator.show(Tab.DAY, date) }) { Text("Open day") }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    days: List<LocalDate>,
    selected: LocalDate,
    plansByDate: Map<LocalDate, List<PlanSummary>>,
    onSelect: (LocalDate) -> Unit,
) {
    val firstDay = remember { Periods.defaultFirstDay() }
    val weekdays = remember(firstDay) { (0L until 7L).map { firstDay.plus(it) } }
    val today = LocalDate.now()

    Column {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { d: DayOfWeek ->
                Text(
                    d.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        isSelected = date == selected,
                        plans = plansByDate[date].orEmpty(),
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(MaterialTheme.colorScheme.primary)
            Text(" Planned   ", style = MaterialTheme.typography.labelSmall)
            Dot(StatusColors.inProgress)
            Text(" In progress   ", style = MaterialTheme.typography.labelSmall)
            Dot(StatusColors.done)
            Text(" Done", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    plans: List<PlanSummary>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .aspectRatio(0.85f)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (isSelected) colors.primaryContainer else colors.surface)
            .then(if (isToday) Modifier.border(1.5.dp, colors.primary, MaterialTheme.shapes.small) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfMonth.toString(),
            modifier = Modifier.padding(top = 4.dp),
            fontWeight = if (isToday) FontWeight.Bold else null,
            color = when {
                isSelected -> colors.onPrimaryContainer
                inMonth -> colors.onSurface
                else -> colors.onSurface.copy(alpha = 0.35f)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        // One dot per plan (up to three), colored by status; "+n" when there are more.
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            plans.take(3).forEach { p -> Dot(StatusColors.of(p.plan.status) ?: colors.primary) }
        }
        if (plans.size > 3) {
            Text(
                "+${plans.size - 3}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(6.dp).background(color, CircleShape))
}
