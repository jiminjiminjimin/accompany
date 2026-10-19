package com.example.pathreport

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Local HTML/JS only. Navigation callbacks carry numeric pin coordinates; no JS bridge. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun CareMapView(page: String, payload: String, modifier: Modifier = Modifier,
    selection: Pair<Double, Double>? = null, onPick: ((Double, Double) -> Unit)? = null) {
    val currentPick by rememberUpdatedState(onPick)
    val currentSelection by rememberUpdatedState(selection)
    val initialPayload = remember(page) { payload }
    var loaded by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    AndroidView(modifier = modifier, factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setGeolocationEnabled(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = "Accompany/1.0 (+https://github.com/jiminjiminjimin/accompany) " + settings.userAgentString
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url
                    if (uri.scheme == "https" && uri.host == "tile.openstreetmap.org") return null
                    if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net") {
                        val name = uri.lastPathSegment
                        if (name == page) {
                            val html = context.assets.open("map/$page").bufferedReader().use { it.readText() }
                                .replace("__TRIP_DATA__", initialPayload)
                            return WebResourceResponse("text/html", "UTF-8", html.byteInputStream())
                        }
                        val mime = when (name) {
                            "leaflet.js", "common.js" -> "application/javascript"
                            "leaflet.css", "common.css" -> "text/css"
                            else -> null
                        }
                        if (mime != null) return WebResourceResponse(mime, "UTF-8", context.assets.open("map/$name"))
                    }
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    loaded = true
                    currentSelection?.let { (lat, lon) ->
                        if (inKoreaMap(lat, lon)) view.evaluateJavascript("window.selectPlace && window.selectPlace($lat,$lon);", null)
                    }
                    applied = currentSelection
                }
                private fun failure(view: WebView, request: WebResourceRequest, message: String) {
                    if (request.url.host == "tile.openstreetmap.org") {
                        view.evaluateJavascript("window.mapLoadError && window.mapLoadError(${JSONObject.quote(message)});", null)
                    }
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    failure(view, request, mapErrorMessage(error.errorCode))
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    failure(view, request, mapErrorMessage(response.statusCode, true))
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (request.isForMainFrame && uri.scheme == "accompany-map" && uri.host == "pick") {
                        val lat = uri.getQueryParameter("lat")?.toDoubleOrNull()
                        val lon = uri.getQueryParameter("lon")?.toDoubleOrNull()
                        if (lat != null && lon != null && inKoreaMap(lat, lon)) currentPick?.invoke(lat, lon)
                    } else if (request.isForMainFrame && uri.scheme == "https" && uri.host == "www.openstreetmap.org") {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    }
                    return true
                }
            }
            loadUrl("https://appassets.androidplatform.net/map/$page")
        }
    }, update = { view ->
        if (loaded && selection != applied) {
            selection?.let { (lat, lon) ->
                if (inKoreaMap(lat, lon)) view.evaluateJavascript("window.selectPlace && window.selectPlace($lat,$lon);", null)
            }
            applied = selection
        }
    }, onRelease = { it.stopLoading(); it.destroy() })
}
