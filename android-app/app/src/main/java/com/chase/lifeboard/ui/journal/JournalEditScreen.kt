package com.chase.lifeboard.ui.journal

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.chase.lifeboard.data.JournalPhotoEntity
import com.chase.lifeboard.data.Moods
import com.chase.lifeboard.ui.Navigator
import com.chase.lifeboard.ui.common.ConfirmDialog
import com.chase.lifeboard.ui.common.DatePickerModal
import com.chase.lifeboard.ui.common.Format
import com.chase.lifeboard.ui.common.SectionHeader
import com.chase.lifeboard.ui.lifeBoardApp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEditScreen(entryId: Long, isNew: Boolean, navigator: Navigator) {
    val app = lifeBoardApp()
    val vm: JournalEditViewModel = viewModel(
        key = "entry-$entryId",
        factory = viewModelFactory { initializer { JournalEditViewModel(app, entryId, isNew) } },
    )
    val item by vm.entry.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val body by vm.body.collectAsStateWithLifecycle()
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<JournalPhotoEntity?>(null) }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) vm.addPhotos(uris)
    }

    val leave = {
        vm.onLeave()
        navigator.back()
    }
    BackHandler { leave() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal entry") },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        val current = item ?: return@Scaffold
        val e = current.entry
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedButton(onClick = { showDate = true }) {
                Icon(Icons.Filled.CalendarToday, null)
                Spacer(Modifier.width(6.dp))
                Text(Format.longDay(LocalDate.ofEpochDay(e.day)))
            }

            SectionHeader("How are you feeling?")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Moods.all.forEach { (value, emoji) ->
                    val selected = e.mood == value
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { vm.setMood(if (selected) null else value) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(emoji, fontSize = 28.sp)
                        Text(Moods.labels[value].orEmpty(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            TextField(
                value = title,
                onValueChange = vm::setTitle,
                placeholder = { Text("Title") },
                textStyle = MaterialTheme.typography.titleLarge,
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = body,
                onValueChange = vm::setBody,
                placeholder = { Text("What happened today? What's on your mind?") },
                minLines = 8,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            SectionHeader("Photos")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(current.photos.sortedBy { it.sortOrder }, key = { it.id }) { photo ->
                    Box {
                        AsyncImage(
                            model = app.journal.photoFile(photo.fileName),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(110.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { viewing = photo },
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp),
                        ) {
                            IconButton(onClick = { vm.removePhoto(photo) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                item(key = "add") {
                    OutlinedButton(
                        onClick = {
                            pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.size(110.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AddPhotoAlternate, null)
                            Text("Add", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.size(48.dp))
        }

        if (showDate) {
            DatePickerModal(
                initial = LocalDate.ofEpochDay(e.day),
                onPick = { vm.setDay(it.toEpochDay()) },
                onDismiss = { showDate = false },
            )
        }
    }

    viewing?.let { photo ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { viewing = null },
            ) {
                AsyncImage(
                    model = app.journal.photoFile(photo.fileName),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete entry?",
            text = "The entry and its photos will be removed.",
            confirm = "Delete",
            onConfirm = {
                vm.delete()
                navigator.back()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
