package com.chase.mealplan.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Meal photos live as JPEGs in the app's private files folder. */
class PhotoStore(private val context: Context) {
    val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(name: String) = File(dir, name)

    /** A fresh location the camera app can write a new photo to. */
    fun newCameraUri(): Uri {
        val folder = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(folder, "capture-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    /**
     * Copies a picked or captured image into private storage, turned upright and shrunk
     * so a week of photos doesn't eat the phone's storage. Returns the stored file name.
     */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return@runCatching null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIZE) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val bitmap = upright(scaleDown(decoded), orientation)
            val name = "${UUID.randomUUID()}.jpg"
            file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            name
        }.getOrNull().also { cleanCameraCache() }
    }

    fun delete(name: String?) {
        if (name != null) file(name).delete()
    }

    /** Removes photo files no meal points to any more (e.g. from an edit that was cancelled). */
    suspend fun deleteUnused(used: Set<String>) = withContext(Dispatchers.IO) {
        dir.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }

    private fun cleanCameraCache() {
        File(context.cacheDir, "camera").listFiles()?.forEach { it.delete() }
    }

    private fun scaleDown(b: Bitmap): Bitmap {
        val longest = max(b.width, b.height)
        if (longest <= MAX_SIZE) return b
        val ratio = MAX_SIZE.toFloat() / longest
        return Bitmap.createScaledBitmap(b, (b.width * ratio).toInt(), (b.height * ratio).toInt(), true)
    }

    private fun upright(b: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.preScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.preScale(-1f, 1f) }
            else -> return b
        }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    private companion object {
        const val MAX_SIZE = 1600
    }
}
