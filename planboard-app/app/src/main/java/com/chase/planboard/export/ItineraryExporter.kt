package com.chase.planboard.export

import android.content.Context
import android.net.Uri
import com.chase.planboard.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the plans for a date range and saves them as a PDF itinerary to [target]. */
class ItineraryExporter(private val context: Context, private val db: AppDatabase) {
    suspend fun export(options: ExportOptions, target: Uri) = withContext(Dispatchers.IO) {
        require(options.dayCount <= ExportOptions.MAX_DAYS) { "Pick a range of a year or less" }
        val itinerary = ItineraryBuilder.build(
            plans = db.planDao().all(),
            todos = db.todoDao().all(),
            notes = db.noteDao().all(),
            options = options,
        )
        val out = context.contentResolver.openOutputStream(target) ?: error("Could not open file")
        out.buffered().use { ItineraryPdf.write(itinerary, it) }
    }
}
