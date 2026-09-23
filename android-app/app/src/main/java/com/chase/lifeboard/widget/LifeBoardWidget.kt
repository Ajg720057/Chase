package com.chase.lifeboard.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
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
import androidx.glance.text.TextStyle
import com.chase.lifeboard.LifeBoardApp
import com.chase.lifeboard.MainActivity
import com.chase.lifeboard.R
import com.chase.lifeboard.data.TaskEntity
import com.chase.lifeboard.ui.common.Format
import com.chase.lifeboard.ui.theme.PriorityColors

private data class WidgetTask(val task: TaskEntity, val parentTitle: String?)

/**
 * Upcoming unfinished tasks: dated ones first (soonest, including overdue), then
 * undated top-level tasks by priority. Dated subtasks are included too.
 */
private fun upcoming(all: List<TaskEntity>): List<WidgetTask> {
    val byId = all.associateBy { it.id }
    return all
        .filter { !it.completed && (it.parentId == null || it.dueAt != null) }
        .sortedWith(
            compareBy<TaskEntity> { it.dueAt == null }
                .thenBy { it.dueAt ?: 0 }
                .thenByDescending { it.priority }
                .thenBy { it.sortOrder },
        )
        .take(50)
        .map { t -> WidgetTask(t, t.parentId?.let { byId[it]?.title?.ifBlank { "Untitled" } }) }
}

class LifeBoardWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as LifeBoardApp
        val initial = app.tasks.allTasksOnce()
        provideContent {
            val all by app.tasks.allTasks.collectAsState(initial = initial)
            GlanceTheme {
                WidgetContent(context, upcoming(all))
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = LifeBoardWidget().updateAll(context)
    }
}

class LifeBoardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeBoardWidget()
}

@Composable
private fun WidgetContent(context: Context, tasks: List<WidgetTask>) {
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Upcoming",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier
                    .defaultWeight()
                    .padding(start = 4.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            )
            HeaderButton(
                R.drawable.ic_widget_journal,
                "Write in journal",
                actionStartActivity(QuickAddActivity.intent(context, QuickAddActivity.MODE_JOURNAL)),
            )
            Spacer(GlanceModifier.width(8.dp))
            HeaderButton(
                R.drawable.ic_widget_add,
                "Add task",
                actionStartActivity(QuickAddActivity.intent(context, QuickAddActivity.MODE_TASK)),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        if (tasks.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "All clear! Tap + to add a task.",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                )
            }
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(tasks, itemId = { it.task.id }) { item -> TaskLine(context, item) }
            }
        }
    }
}

@Composable
private fun HeaderButton(icon: Int, description: String, action: androidx.glance.action.Action) {
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
private fun TaskLine(context: Context, item: WidgetTask) {
    val t = item.task
    val due = t.dueAt
    val overdue = due != null && due < System.currentTimeMillis()
    val details = listOfNotNull(
        due?.let { Format.due(it) },
        item.parentTitle?.let { "in $it" },
    ).joinToString(" · ")

    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            GlanceModifier
                .width(4.dp)
                .height(34.dp)
                .cornerRadius(2.dp)
                .background(PriorityColors.of(t.priority) ?: Color.Transparent),
        ) {}
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<CompleteTaskAction>(actionParametersOf(TaskIdKey to t.id)),
        )
        Column(
            GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(MainActivity.openTaskIntent(context, t.id))),
        ) {
            Text(
                t.title.ifBlank { "Untitled task" },
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            )
            if (details.isNotEmpty()) {
                Text(
                    details,
                    maxLines = 1,
                    style = TextStyle(
                        color = if (overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
    }
}

private val TaskIdKey = ActionParameters.Key<Long>("task_id")

/** Checks a task off from the widget (a repeating task moves to its next date instead). */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        val app = context.applicationContext as LifeBoardApp
        app.tasks.get(id)?.let { app.tasks.setCompleted(it, true) }
        LifeBoardWidget.refresh(context)
    }
}
