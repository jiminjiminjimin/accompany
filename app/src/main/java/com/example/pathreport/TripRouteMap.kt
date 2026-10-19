package com.example.pathreport

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Locale

internal fun routeMapPayload(trip: Trip): String = JSONObject()
    .put("route", JSONArray().apply { trip.route.forEach { p ->
        put(JSONArray().put(p.latitude).put(p.longitude).put(p.recordedAt).put(p.breakBefore))
    } })
    .put("stops", JSONArray().apply { trip.targets.forEachIndexed { index, target ->
        put(JSONObject().put("lat", target.place.latitude).put("lon", target.place.longitude)
            .put("number", index + 1).put("label", "${index + 1}. ${target.elderName}")
            .put("time", trip.arrivals.find { it.elderId == target.elderId }?.estimatedAt))
    } }).toString().replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun TripRouteMap(trip: Trip, dismiss: () -> Unit) {
    // A snapshot keeps a live trip from rebuilding the map after every storage update.
    val snapshot = remember(trip.id) { trip }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(8.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${snapshot.direction.label} 이동 기록", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = dismiss) { Text("닫기") }
                }
                Text("기록 경로 약 ${String.format(Locale.KOREA, "%.2f", routeDistanceMeters(snapshot.route) / 1000)}km · ${snapshot.route.size}개 지점",
                    style = MaterialTheme.typography.bodySmall)
                CareMapView("route.html", routeMapPayload(snapshot), Modifier.fillMaxWidth().weight(1f))
                Text("초록: 첫 기록 · 빨강: 마지막 기록 · 숫자: 대상 장소\n수신 중단 구간은 연결하지 않습니다. 배경 지도는 인터넷이 필요하며 표시 영역을 지도 서버에 요청합니다.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
