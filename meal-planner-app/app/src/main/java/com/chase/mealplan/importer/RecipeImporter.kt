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

    /**
     * [tryBrowser] is true when opening the page in the in-app browser may still work
     * (the site blocked a plain download, or builds its page with JavaScript).
     */
    class ImportException(message: String, val tryBrowser: Boolean = false) : Exception(message)

    /** Finds the first web address in shared text such as "Try this! https://…". */
    fun findUrl(text: String): String? =
        Regex("https?://[^\\s<>\"']+").find(text)?.value?.trimEnd('.', ',', ')', ']')

    /** The address to load: the first link in [text], or "https://" added to a bare "site.com/…". */
    fun normalize(text: String): String? {
        val t = text.trim()
        findUrl(t)?.let { return it }
        if (t.contains('.') && !t.contains(' ')) return "https://$t"
        return null
    }

    suspend fun fetch(url: String): ImportedRecipe = withContext(Dispatchers.IO) {
        val address = normalize(url) ?: throw ImportException("That doesn't look like a web address.")
        val html = try {
            download(address, MAX_PAGE_BYTES).toString(Charsets.UTF_8)
        } catch (e: java.net.UnknownHostException) {
            throw ImportException("Couldn't reach that site. Check your internet connection.")
        } catch (e: IOException) {
            // Usually bot protection refusing a plain download; a real browser view gets through.
            throw ImportException("The site didn't allow a quick download.", tryBrowser = true)
        }
        parseHtml(html, address)
            ?: throw ImportException("No recipe data in the quick download.", tryBrowser = true)
    }

    fun parseHtml(html: String, address: String): ImportedRecipe? = RecipeParser.parse(html, address)

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
