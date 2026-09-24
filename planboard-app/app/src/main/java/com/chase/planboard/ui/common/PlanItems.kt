package com.chase.planboard.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chase.planboard.ui.BoardViewModel
import com.chase.planboard.ui.Navigator
import com.chase.planboard.ui.PlanSummary

/** A section header with an add button, followed by plan cards or an empty message. */
fun LazyListScope.planSection(
    key: String,
    title: String,
    plans: List<PlanSummary>,
    board: BoardViewModel,
    navigator: Navigator,
    emptyText: String?,
    onAdd: (() -> Unit)?,
    subtitle: (PlanSummary) -> String? = { null },
) {
    item(key = "$key-header") {
        SectionHeader(
            title,
            action = onAdd?.let { add ->
                @Composable {
                    IconButton(onClick = add) { Icon(Icons.Filled.Add, contentDescription = "Add to $title") }
                }
            },
        )
    }
    if (plans.isEmpty() && emptyText != null) item(key = "$key-empty") { EmptyState(emptyText) }
    items(plans, key = { "$key-${it.plan.id}" }) { item ->
        PlanCard(
            item = item,
            subtitle = subtitle(item),
            onClick = { navigator.openPlan(item.plan.id) },
            onStatusClick = { board.cycleStatus(item.plan) },
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}
