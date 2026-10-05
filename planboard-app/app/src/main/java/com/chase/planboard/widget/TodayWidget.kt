package com.chase.planboard.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.chase.planboard.MainActivity
import com.chase.planboard.OpenRequest
import com.chase.planboard.PlanBoardApp
import com.chase.planboard.R
import com.chase.planboard.data.PlanEntity
import com.chase.planboard.data.PlanStatus
import com.chase.planboard.data.Scope
import com.chase.planboard.data.TodoEntity
import com.chase.planboard.ui.Board
import com.chase.planboard.ui.PlanSummary
import com.chase.planboard.ui.common.Format
import com.chase.planboard.ui.theme.StatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One line in the widget's list: a section label or a plan. */
private sealed class Line(val id: Long) {
    class Header(id: Long, val text: String) : Line(id)
    class Plan(val item: PlanSummary, val compact: Boolean) : Line(item.plan.id)
}

private data class TodayState(val date: LocalDate, val lines: List<Line>, val dayPlans: Int)

/** Today's day plans in time order, then this week's and this month's plans for context. */
private fun todayState(date: LocalDate, plans: List<PlanEntity>, todos: List<TodoEntity>): TodayState {
    val board = Board.of(plans, todos)
    val day = board.plansIn(Scope.DAY, date)
        .sortedWith(compareBy({ it.plan.startMinute == null }, { it.plan.startMinute }, { it.plan.sortOrder }))
    val week = board.plansIn(Scope.WEEK, date)
    val month = board.plansIn(Scope.MONTH, date)
    val lines = buildList {
        day.forEach { add(Line.Plan(it, compact = false)) }
        if (week.isNotEmpty()) {
            add(Line.Header(-1, "This week"))
            week.forEach { add(Line.Plan(it, compact = true)) }
        }
        if (month.isNotEmpty()) {
            add(Line.Header(-2, "This month"))
            month.forEach { add(Line.Plan(it, compact = true)) }
        }
    }
    return TodayState(date, lines, day.size)
}

/** Today's date, emitted again just after each midnight so the widget rolls over. */
private fun todayTicker(): Flow<LocalDate> = flow {
    while (true) {
        val today = LocalDate.now()
        emit(today)
        val nextMidnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        delay((nextMidnight - System.currentTimeMillis()).coerceAtLeast(0) + 1_000)
    }
}

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as PlanBoardApp
        val initial = todayState(LocalDate.now(), app.database.planDao().all(), app.database.todoDao().all())
        val states = combine(todayTicker(), app.plans.allPlans, app.plans.allTodos) { date, plans, todos ->
            todayState(date, plans, todos)
        }
        provideContent {
            val state by states.collectAsState(initial = initial)
            GlanceTheme {
                WidgetContent(context, state)
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = TodayWidget().updateAll(context)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

private val headerDate = DateTimeFormatter.ofPattern("EEEE, MMM d")

@Composable
private fun WidgetContent(context: Context, state: TodayState) {
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                GlanceModifier
                    .defaultWeight()
                    .padding(start = 4.dp)
                    .clickable(actionStartActivity(MainActivity.openIntent(context, OpenRequest.Kind.TODAY))),
            ) {
                Text(
                    "Today",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    state.date.format(headerDate),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
            HeaderButton(
                R.drawable.ic_widget_add,
                "Add a plan for today",
                actionStartActivity(MainActivity.openIntent(context, OpenRequest.Kind.NEW_DAY_PLAN)),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        if (state.lines.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing planned today. Tap + to plan your day.",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                )
            }
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                if (state.dayPlans == 0) {
                    item(itemId = -3) {
                        Text(
                            "Nothing planned for today yet.",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                            modifier = GlanceModifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                }
                items(state.lines, itemId = { it.id }) { line ->
                    when (line) {
                        is Line.Header -> Text(
                            line.text.uppercase(),
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            modifier = GlanceModifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                        )
                        is Line.Plan -> PlanLine(context, line.item, line.compact)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderButton(icon: Int, description: String, action: Action) {
    Box(
        GlanceModifier
            .size(36.dp)
            .cornerRadius(18.dp)
            .background(GlanceTheme.colors.primaryContainer)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
            modifier = GlanceModifier.size(20.dp),
        )
    }
}

@Composable
private fun PlanLine(context: Context, item: PlanSummary, compact: Boolean) {
    val p = item.plan
    val done = p.status == PlanStatus.DONE
    val icon = when (p.status) {
        PlanStatus.PLANNED -> R.drawable.ic_status_planned
        PlanStatus.IN_PROGRESS -> R.drawable.ic_status_progress
        PlanStatus.DONE -> R.drawable.ic_status_done
    }
    val tint = StatusColors.of(p.status)?.let { ColorProvider(it) } ?: GlanceTheme.colors.onSurfaceVariant
    val details = listOfNotNull(
        if (compact) null else (Format.timeRange(p) ?: "Any time"),
        if (item.todosTotal > 0) "${item.todosDone}/${item.todosTotal} to-dos" else null,
    ).joinToString(" · ")

    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = if (compact) 1.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tap the status icon to move the plan along: Planned → In progress → Done.
        Box(
            GlanceModifier
                .size(36.dp)
                .clickable(actionRunCallback<CycleStatusAction>(actionParametersOf(PlanIdKey to p.id))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = "Status: ${p.status.label}. Tap to change.",
                colorFilter = ColorFilter.tint(tint),
                modifier = GlanceModifier.size(if (compact) 18.dp else 22.dp),
            )
        }
        Spacer(GlanceModifier.width(4.dp))
        Column(
            GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(MainActivity.openIntent(context, OpenRequest.Kind.PLAN, p.id))),
        ) {
            Text(
                p.title.ifBlank { "Untitled plan" },
                maxLines = if (compact) 1 else 2,
                style = TextStyle(
                    color = if (done) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                    fontSize = if (compact) 13.sp else 14.sp,
                    fontWeight = if (compact) null else FontWeight.Medium,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                ),
            )
            if (details.isNotEmpty()) {
                Text(
                    details,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
    }
}

private val PlanIdKey = ActionParameters.Key<Long>("plan_id")

/** Moves a plan's status along from the widget. */
class CycleStatusAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[PlanIdKey] ?: return
        val app = context.applicationContext as PlanBoardApp
        app.plans.get(id)?.let { app.plans.update(it.copy(status = it.status.next())) }
        TodayWidget.refresh(context)
    }
}
