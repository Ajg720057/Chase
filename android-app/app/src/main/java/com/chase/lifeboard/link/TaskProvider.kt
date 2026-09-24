package com.chase.lifeboard.link

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Process
import com.chase.lifeboard.LifeBoardApp
import com.chase.lifeboard.data.Recurrence
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest

/**
 * Lets PlanBoard add tasks to LifeBoard and keep them in step:
 *
 * - insert `tasks` with title, notes, due_at → `tasks/{id}`
 * - query `tasks?ids=1,2,3` → _id, title, completed, due_at for the ones that still exist
 * - update `tasks/{id}` with completed and/or title, due_at
 * - delete `tasks/{id}`
 *
 * Only apps listed in [TRUSTED], signed with the pinned certificate, may call it.
 */
class TaskProvider : ContentProvider() {
    private val app get() = context!!.applicationContext as LifeBoardApp

    override fun onCreate() = true

    override fun getType(uri: Uri): String? = null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        checkCaller()
        require(matcher.match(uri) == TASKS) { "Unknown uri $uri" }
        val ids = uri.getQueryParameter(PARAM_IDS).orEmpty()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
        val cursor = MatrixCursor(arrayOf(COL_ID, COL_TITLE, COL_COMPLETED, COL_DUE_AT))
        runBlocking {
            ids.forEach { id ->
                app.tasks.get(id)?.let { t ->
                    cursor.addRow(arrayOf<Any?>(t.id, t.title, if (t.completed) 1 else 0, t.dueAt))
                }
            }
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        checkCaller()
        require(matcher.match(uri) == TASKS) { "Unknown uri $uri" }
        val v = values ?: ContentValues()
        val id = runBlocking {
            app.tasks.create(
                title = v.getAsString(COL_TITLE).orEmpty(),
                notes = v.getAsString(COL_NOTES).orEmpty(),
                dueAt = v.getAsLong(COL_DUE_AT),
            )
        }
        return ContentUris.withAppendedId(TASKS_URI, id)
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        checkCaller()
        require(matcher.match(uri) == TASK_ID) { "Unknown uri $uri" }
        val v = values ?: return 0
        val id = ContentUris.parseId(uri)
        return runBlocking {
            var task = app.tasks.get(id) ?: return@runBlocking 0
            if (v.containsKey(COL_TITLE) || v.containsKey(COL_DUE_AT)) {
                val dueAt = if (v.containsKey(COL_DUE_AT)) v.getAsLong(COL_DUE_AT) else task.dueAt
                task = task.copy(
                    title = v.getAsString(COL_TITLE) ?: task.title,
                    dueAt = dueAt,
                    alarmEnabled = task.alarmEnabled && dueAt != null,
                    recurrence = if (dueAt == null) Recurrence.NONE else task.recurrence,
                )
                app.tasks.update(task)
            }
            val completed = v.getAsBoolean(COL_COMPLETED)
            if (completed != null && completed != task.completed) app.tasks.setCompleted(task, completed)
            1
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        checkCaller()
        require(matcher.match(uri) == TASK_ID) { "Unknown uri $uri" }
        return runBlocking {
            val task = app.tasks.get(ContentUris.parseId(uri)) ?: return@runBlocking 0
            app.tasks.delete(task)
            1
        }
    }

    private fun checkCaller() {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return
        val pm = context!!.packageManager
        val packages = pm.getPackagesForUid(uid).orEmpty()
        if (packages.none { isTrusted(pm, it) }) throw SecurityException("This app may not use LifeBoard tasks")
    }

    private fun isTrusted(pm: PackageManager, pkg: String): Boolean {
        val cert = TRUSTED[pkg] ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.hasSigningCertificate(pkg, cert, PackageManager.CERT_INPUT_SHA256)
            } else {
                @Suppress("DEPRECATION")
                val signatures = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures.orEmpty()
                signatures.isNotEmpty() && signatures.all { sha256(it.toByteArray()).contentEquals(cert) }
            }
        }.getOrDefault(false)
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    companion object {
        const val AUTHORITY = "com.chase.lifeboard.tasks"
        val TASKS_URI: Uri = Uri.parse("content://$AUTHORITY/tasks")
        const val PARAM_IDS = "ids"
        const val COL_ID = "_id"
        const val COL_TITLE = "title"
        const val COL_NOTES = "notes"
        const val COL_DUE_AT = "due_at"
        const val COL_COMPLETED = "completed"

        private const val TASKS = 1
        private const val TASK_ID = 2
        private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "tasks", TASKS)
            addURI(AUTHORITY, "tasks/#", TASK_ID)
        }

        /** Apps allowed to use the provider, with the SHA-256 of their signing certificate. */
        private val TRUSTED: Map<String, ByteArray> = mapOf(
            "com.chase.planboard" to hex("90DF94ABA3164DA7E369E919C715A4A58A1C613D015F6E0E73D6A5A18415C559"),
        )

        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
