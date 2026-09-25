package com.chase.mealplan.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a recipe page and reads the recipe out of it. */
object RecipeImporter {
    // Some sites turn away requests that don't look like a phone browser.
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private const val MAX_PAGE_BYTES = 5 * 1024 * 1024

    class ImportException(message: String) : Exception(message)

    /** Finds the first web address in shared text such as "Try this! https://…". */
    fun findUrl(text: String): String? =
        Regex("https?://[^\\s<>\"']+").find(text)?.value?.trimEnd('.', ',', ')', ']')

    suspend fun fetch(url: String): ImportedRecipe = withContext(Dispatchers.IO) {
        val address = findUrl(url.trim()) ?: if (url.contains('.')) "https://${url.trim()}" else
            throw ImportException("That doesn't look like a web address.")
        val html = try {
            download(address, MAX_PAGE_BYTES).toString(Charsets.UTF_8)
        } catch (e: IOException) {
            throw ImportException("Couldn't open that page. Check the address and your internet connection.")
        }
        RecipeParser.parse(html, address)
            ?: throw ImportException(
                "Couldn't find a recipe on that page. Some sites don't share their recipe details; " +
                    "you can still type or paste it in.",
            )
    }

    suspend fun downloadImage(url: String, into: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            into.parentFile?.mkdirs()
            into.writeBytes(download(url, 15 * 1024 * 1024))
            true
        }.getOrDefault(false)
    }

    private fun download(address: String, limit: Int): ByteArray {
        var current = address
        repeat(5) { // follow redirects, including http -> https
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,image/*,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val next = conn.getHeaderField("Location") ?: throw IOException("Redirect without location")
                    current = URL(URL(current), next).toString()
                    return@repeat
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                conn.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0 || out.size() > limit) break
                        out.write(buf, 0, n)
                    }
                    return out.toByteArray()
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }
}
