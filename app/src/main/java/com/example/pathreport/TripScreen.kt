package com.example.pathreport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun tripTime(time: Long) = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA).format(Date(time))

@Composable
fun TripScreen(data: CareData, busy: Boolean, tracking: TrackingInfo = TrackingInfo(), requestLocation: () -> Unit = {}, requestNotifications: () -> Unit = {}, endTrip: ((String) -> Unit)? = null, save: ((CareData) -> CareData) -> Unit) {
    var selecting by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf<String?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    val active = data.trips.singleOrNull { it.endedAt == null }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("GPS 운행 기록", style = MaterialTheme.typography.titleLarge)
        Text("등록 장소 50m 이내에서 연속 20초 정차하면 도착을 추정합니다. 위치 오차가 30m 이하여야 합니다.")
        if (active == null) {
            Button(enabled = !busy, onClick = { selecting = true }) { Text("운행 시작") }
        } else {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${active.direction.label} 운행 진행 중", style = MaterialTheme.typography.titleMedium)
                Text("시작: ${tripTime(active.startedAt)} · 대상 ${active.targets.size}명")
                Text(tracking.message)
                Text("장소 판정 반경 ${StopPolicy.RADIUS_METERS.toInt()}m · 현재 GPS 오차 ${tracking.accuracyMeters?.let { "약 ${it}m" } ?: if (!tracking.serviceAlive) "감지 중지" else "수신 대기"}")
                Text("GPS 오차 허용 기준: ${StopPolicy.MAX_ACCURACY_METERS.toInt()}m 이하", style = MaterialTheme.typography.bodySmall)
                Text("기록 완료 ${active.arrivals.size}명 · 미기록 ${active.targets.size - active.arrivals.size}명")
                if (tracking.pending.isNotEmpty()) Text("저장 대기 ${tracking.pending.size}명 · 저장 실패 시 자동으로 다시 시도합니다.")
                if (tracking.phase != TrackingPhase.COMPLETE && tracking.phase != TrackingPhase.ENDING && (tracking.needsPermission || !tracking.serviceAlive))
                    Button(enabled = !busy && tracking.phase != TrackingPhase.STARTING, onClick = requestLocation) {
                        Text(if (tracking.needsPermission) "정확한 위치 권한 허용" else "GPS 감지 시작·재개")
                    }
                if (!tracking.notificationsAllowed) {
                    Text("운행 알림이 꺼져 있습니다. 감지 상태를 알림에서 보려면 허용해 주세요.")
                    TextButton(enabled = !busy, onClick = requestNotifications) { Text("운행 알림 허용·설정") }
                }
                Text("운행 감지 서비스는 화면 꺼짐·앱 잠금 상태에서도 동작합니다. 감지가 중단되면 앱에서 재개해 주세요.")
                TextButton(enabled = !busy, onClick = { detailId = active.id }) { Text("대상·장소 확인") }
                Button(enabled = !busy, onClick = { ending = active.id }) { Text("운행 종료") }
            } }
        }
        Text("운행 내역", style = MaterialTheme.typography.titleMedium)
        if (data.trips.isEmpty()) Text("저장된 운행이 없습니다.")
        data.trips.asReversed().forEach { trip ->
            OutlinedButton(enabled = !busy, onClick = { detailId = trip.id }, modifier = Modifier.fillMaxWidth()) {
                Text("${trip.direction.label} · ${tripTime(trip.startedAt)}\n기록 ${trip.arrivals.size}명 · 미기록 ${trip.targets.size - trip.arrivals.size}명 · ${if (trip.endedAt == null) "진행 중" else "종료"}")
            }
        }
    }
    if (selecting) StartTripDialog(data, busy, dismiss = { selecting = false }, start = { direction, choices ->
        save { it.startTrip(direction, choices, System.currentTimeMillis()) }
        // Selection stays available on write failure; successful save exposes the active trip.
    })
    LaunchedEffect(active?.id) { if (active != null) selecting = false }
    ending?.let { id -> AlertDialog(onDismissRequest = { if (!busy) ending = null },
        title = { Text("운행을 종료할까요?") }, text = {
            val endingTrip = data.trips.find { it.id == id }
            val completedIds = endingTrip?.arrivals.orEmpty().map { it.elderId }.toSet() + tracking.pending.map { it.elderId }
            val missing = endingTrip?.targets.orEmpty().filterNot { it.elderId in completedIds }
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text("미기록 ${missing.size}명입니다.")
                missing.forEach { Text("${it.elderName} · ${it.place.name}") }
                Text("대기 중인 도착 추정 기록과 종료 시각을 저장하고 GPS 감지를 멈춥니다.")
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { if (endTrip != null) endTrip(id) else save { it.appendDetectedArrivals(id, tracking.pending).endTrip(id, System.currentTimeMillis()) } }) { Text("종료하고 저장") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { ending = null }) { Text("취소") } }) }
    LaunchedEffect(data.trips) { if (data.trips.any { it.id == ending && it.endedAt != null }) ending = null }
    data.trips.find { it.id == detailId }?.let { trip ->
        AlertDialog(onDismissRequest = { detailId = null }, title = { Text("${trip.direction.label} 운행 상세") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("시작: ${tripTime(trip.startedAt)}")
                Text("종료: ${trip.endedAt?.let { tripTime(it) } ?: "진행 중"}")
                Text("기록 완료 ${trip.arrivals.size}명 · 미기록 ${trip.targets.size - trip.arrivals.size}명")
                Text("운행 시작 당시의 대상·장소입니다. GPS 도착 추정은 실제 승하차 확인이 아닙니다.")
                trip.targets.forEachIndexed { index, target ->
                    val arrival = trip.arrivals.find { it.elderId == target.elderId }
                    Text(if (arrival == null) "미기록" else "도착 추정: ${tripTime(arrival.estimatedAt)}")
                    Text("${index + 1}. ${target.elderName}\n${target.place.name}\n${target.place.address}\n${target.place.latitude}, ${target.place.longitude}")
                }
            } }, confirmButton = { TextButton(onClick = { detailId = null }) { Text("닫기") } })
    }
}

@Composable
private fun StartTripDialog(data: CareData, busy: Boolean, dismiss: () -> Unit,
    start: (TripDirection, Map<String, String>) -> Unit) {
    var direction by remember { mutableStateOf(TripDirection.TO_CENTER) }
    var choices by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val eligible = data.elders.associate { elder -> elder.id to data.places.filter { p ->
        p.latitude != null && p.longitude != null && PlaceElder(p.id, elder.id) in data.links
    } }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("운행 대상·장소 선택") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                TripDirection.entries.forEach { d -> FilterChip(selected = direction == d, enabled = !busy,
                    onClick = { direction = d }, label = { Text(d.label) }) }
            }
            Text("운행에 포함할 어르신을 선택하고, 각 어르신의 장소 한 곳을 지정해 주세요.")
            if (data.elders.isEmpty()) Text("어르신을 등록한 뒤 좌표가 있는 장소와 연결해 주세요.")
            data.elders.forEachIndexed { index, elder ->
                val places = eligible.getValue(elder.id)
                Row {
                    Checkbox(enabled = !busy && places.isNotEmpty(), checked = elder.id in choices,
                        onCheckedChange = { checked -> choices = if (checked) choices + (elder.id to places.first().id) else choices - elder.id })
                    Text("${index + 1}. ${elder.name}", Modifier.padding(top = 12.dp))
                }
                if (places.isEmpty()) Text("좌표가 등록된 연결 장소가 없습니다.")
                if (elder.id in choices) places.forEach { place ->
                    Row {
                        RadioButton(selected = choices[elder.id] == place.id, enabled = !busy,
                            onClick = { choices = choices + (elder.id to place.id) })
                        Text("${place.name}\n${place.address}", Modifier.padding(top = 8.dp))
                    }
                }
            }
            Text("선택 ${choices.size}명 · ${direction.label}\n시작 시 대상과 장소를 고정하여 암호화 저장합니다. 정확한 위치 권한을 허용하면 운행 감지 서비스를 시작합니다. 화면을 꺼도 기록합니다.")
        }
    }, confirmButton = { TextButton(enabled = !busy && choices.isNotEmpty(), onClick = { start(direction, choices) }) { Text("선택한 대상으로 시작") } },
        dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("취소") } })
}

