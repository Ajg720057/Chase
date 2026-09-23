package com.chase.lifeboard.ui.common

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.chase.lifeboard.ui.lifeBoardApp
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Overflow menu with backup/restore, shown on each main tab. */
@Composable
fun BackupMenu() {
    val app = lifeBoardApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching { app.backup.export(uri) }
            Toast.makeText(
                context,
                if (result.isSuccess) "Backup saved" else "Backup failed: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Back up to file…") },
            onClick = { open = false; exportLauncher.launch("lifeboard-backup-${LocalDate.now()}.zip") },
        )
        DropdownMenuItem(
            text = { Text("Restore from backup…") },
            onClick = { open = false; importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
        )
    }

    confirmRestore?.let { uri ->
        ConfirmDialog(
            title = "Restore backup?",
            text = "This replaces all tasks, journal entries and photos on this phone with the ones in the backup.",
            confirm = "Restore",
            onConfirm = {
                scope.launch {
                    val result = runCatching { app.backup.import(uri) }
                    Toast.makeText(
                        context,
                        if (result.isSuccess) "Backup restored" else "Restore failed: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onDismiss = { confirmRestore = null },
        )
    }
}
