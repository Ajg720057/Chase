package com.chase.mealplan.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chase.mealplan.importer.ImportedRecipe
import com.chase.mealplan.importer.RecipeImporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONTokener
import kotlin.coroutines.resume

/** Collects the text of every recipe-data block on the page as a JSON array of strings. */
private const val COLLECT_JSON_LD = """
(function() {
  var s = document.querySelectorAll('script[type="application/ld+json"]');
  var out = [];
  for (var i = 0; i < s.length; i++) out.push(s[i].textContent);
  return JSON.stringify(out);
})()
"""

/**
 * Opens a recipe page in a real browser view. Sites that refuse a plain download
 * (bot protection) or build the page with JavaScript work here. Checks the page
 * every couple of seconds and hands back the recipe as soon as it's found.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserImportDialog(url: String, onFound: (ImportedRecipe) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var searching by remember { mutableStateOf(true) }
    // Bumped on each page load and on "Check again", restarting the search.
    var attempt by remember { mutableIntStateOf(0) }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                // Stay inside this view for redirects and links.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    !(request.url.scheme == "http" || request.url.scheme == "https")

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    searching = true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    attempt++
                }
            }
            loadUrl(url)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    LaunchedEffect(attempt) {
        searching = true
        // The page (or a "checking your browser" screen in front of it) may take a while.
        repeat(if (attempt == 0) 20 else 8) {
            delay(1500)
            val found = extract(webView)
            if (found != null) {
                onFound(found)
                return@LaunchedEffect
            }
        }
        searching = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Import recipe") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Cancel") } },
                    actions = { if (!searching) TextButton(onClick = { attempt++ }) { Text("Check again") } },
                )
                if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    if (searching) {
                        "Looking for the recipe… If the site asks you to accept cookies or confirm you're human, " +
                            "do that below."
                    } else {
                        "No recipe found on this page yet. If it's showing a prompt, tap through it and press " +
                            "Check again. Some sites don't share their recipe details; you can still type it in."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private suspend fun extract(webView: WebView): ImportedRecipe? {
    val raw = suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(COLLECT_JSON_LD) { cont.resume(it) }
    } ?: return null
    return runCatching {
        // evaluateJavascript hands back a JSON-encoded string holding our JSON array.
        val text = JSONTokener(raw).nextValue() as? String ?: return null
        val blocks = JSONArray(text)
        val html = (0 until blocks.length()).joinToString("\n") {
            "<script type=\"application/ld+json\">${blocks.optString(it)}</script>"
        }
        RecipeImporter.parseHtml(html, webView.url ?: "")
    }.getOrNull()
}
