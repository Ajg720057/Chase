package com.chase.lifeboard.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.chase.lifeboard.data.JournalEntryWithPhotos
import com.chase.lifeboard.data.Moods
import com.chase.lifeboard.ui.Navigator
import com.chase.lifeboard.ui.common.BackupMenu
import com.chase.lifeboard.ui.common.EmptyState
import com.chase.lifeboard.ui.common.Format
import com.chase.lifeboard.ui.common.SectionHeader
import com.chase.lifeboard.ui.lifeBoardApp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(navigator: Navigator) {
    val app = lifeBoardApp()
    val entries by app.journal.allEntries.collectAsStateWithLifecycle(initialValue = null)
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(entries, query) {
        val q = query.trim()
        entries.orEmpty().filter {
            q.isEmpty() || it.entry.title.contains(q, ignoreCase = true) || it.entry.body.contains(q, ignoreCase = true)
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.entry.day } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search journal") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("Journal")
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close search")
                        }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                    BackupMenu()
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigator.newEntry() },
                icon = { Icon(Icons.Filled.EditNote, null) },
                text = { Text("Write") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
        ) {
            if (entries != null && filtered.isEmpty()) {
                item {
                    EmptyState(
                        if (query.isBlank()) "Your journal is empty. Tap “Write” to capture today." else "No entries match “$query”.",
                    )
                }
            }
            grouped.forEach { (day, dayEntries) ->
                item(key = "d-$day") {
                    SectionHeader(Format.relativeDay(LocalDate.ofEpochDay(day)))
                }
                items(dayEntries, key = { it.entry.id }) { e ->
                    JournalEntryCard(e, onClick = { navigator.openEntry(e.entry.id) }, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
fun JournalEntryCard(item: JournalEntryWithPhotos, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val app = lifeBoardApp()
    val e = item.entry
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.clickable(onClick = onClick).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Moods.emoji(e.mood)?.let {
                    Text(it, fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    e.title.ifBlank { e.body.lineSequence().firstOrNull { it.isNotBlank() } ?: "Untitled entry" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Format.time(e.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (e.title.isNotBlank() && e.body.isNotBlank()) {
                Text(
                    e.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (item.photos.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.photos.sortedBy { it.sortOrder }.take(4).forEach { p ->
                        AsyncImage(
                            model = app.journal.photoFile(p.fileName),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
                        )
                    }
                    if (item.photos.size > 4) {
                        Text(
                            "+${item.photos.size - 4}",
                            modifier = Modifier.align(Alignment.CenterVertically),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
