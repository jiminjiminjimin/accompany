package com.example.pathreport

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.cancelChildren
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var unlocked by mutableStateOf(false)
    private var manuallyLocked = false
    private var lockMessage by mutableStateOf("")
    private val authentication = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        unlocked = it.resultCode == Activity.RESULT_OK
        manuallyLocked = !unlocked
        lockMessage = if (unlocked) "" else "인증이 취소되었습니다. 다시 인증해 주세요."
    }
    private var exporting by mutableStateOf(false)
    private var exportTripIds: Set<String> = emptySet()
    private val exportDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val selectedIds = exportTripIds
        exportTripIds = emptySet()
        if (uri != null) {
            exporting = true
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val rows = selectedTripExcelRows(SecureCareStore(applicationContext).loadAndMigrate().trips, selectedIds)
                        require(rows.isNotEmpty()) { "종료된 운행이 없습니다." }
                        val output = contentResolver.openOutputStream(uri, "wt") ?: error("파일을 열 수 없습니다.")
                        output.use { writeTripExcel(it, rows) }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) {
                    android.widget.Toast.makeText(this@MainActivity, "엑셀 저장에 실패했습니다. 저장 위치를 확인하고 다시 시도해 주세요.", android.widget.Toast.LENGTH_LONG).show()
                } finally { exporting = false }
            }
        }
    }
    private fun exportTrips(tripIds: Set<String>) {
        if (exporting || tripIds.isEmpty()) return
        exportTripIds = tripIds.toSet()
        try {
            val date = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.KOREA).format(java.util.Date())
            exportDocument.launch("운행내역_$date.xlsx")
        } catch (_: Exception) {
            exportTripIds = emptySet()
            android.widget.Toast.makeText(this, "파일 저장 화면을 열 수 없습니다.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    @Suppress("DEPRECATION")
    private fun unlock() {
        val manager = getSystemService(KeyguardManager::class.java)
        if (!manager.isDeviceSecure || !manager.isDeviceLocked) {
            manuallyLocked = false
            unlocked = true
            lockMessage = ""
            return
        }
        val intent = manager.createConfirmDeviceCredentialIntent("안심 동행 잠금 해제", "기기 인증으로 앱을 열어 주세요.")
        if (intent != null) authentication.launch(intent)
        else lockMessage = "기기 인증을 시작하지 못했습니다. 기기 잠금 설정을 확인해 주세요."
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manuallyLocked = savedInstanceState?.getBoolean("manuallyLocked", false) ?: false
        refreshDeviceAccess()
        exportTripIds = savedInstanceState?.getStringArrayList("exportTripIds")?.toSet().orEmpty()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (android.os.Build.VERSION.SDK_INT >= 26) window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setContent {
            CareTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (unlocked) CareApp(exportTrips = ::exportTrips, exporting = exporting) { manuallyLocked = true; unlocked = false; lockMessage = "" }
                    else LockScreen(message = lockMessage, unlock = ::unlock)
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("exportTripIds", ArrayList(exportTripIds))
        outState.putBoolean("manuallyLocked", manuallyLocked)
        super.onSaveInstanceState(outState)
    }
    private fun refreshDeviceAccess() {
        val manager = getSystemService(KeyguardManager::class.java)
        unlocked = canEnterCareApp(manager.isDeviceSecure, manager.isDeviceLocked, manuallyLocked)
        if (unlocked) lockMessage = ""
    }
    override fun onResume() {
        super.onResume()
        refreshDeviceAccess()
    }
    override fun onStop() {
        if (!isChangingConfigurations) unlocked = false
        super.onStop()
    }
}

data class PlaceDraft(val id: String = UUID.randomUUID().toString(), val name: String = "", val address: String = "",
    val lat: String = "", val lon: String = "", val elderIds: Set<String> = emptySet()) {
    fun error(): String? = when {
        name.isBlank() -> "장소 이름을 입력해 주세요."
        address.isBlank() -> "주소를 입력해 주세요."
        lat.toDoubleOrNull() == null || lon.toDoubleOrNull() == null -> "검색 결과를 선택하거나 위도·경도를 입력해 주세요."
        !validCoordinates(lat.toDoubleOrNull(), lon.toDoubleOrNull()) -> "위도는 -90~90, 경도는 -180~180 범위여야 합니다."
        else -> null
    }
    fun place() = StopPlace(id, name.trim(), address.trim(), lat.toDouble(), lon.toDouble())
}

@Composable
fun CareApp(exportTrips: (Set<String>) -> Unit = {}, exporting: Boolean = false, lock: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SecureCareStore(context) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<CareData?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(2) }
    var drafts by remember { mutableStateOf<List<PlaceDraft>?>(null) }
    var editing by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var elderEditor by remember { mutableStateOf<Elder?>(null) }
    var deletePlace by remember { mutableStateOf<StopPlace?>(null) }
    var deleteElder by remember { mutableStateOf<Elder?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        StoreChanges.revision.collect {
            val initial = data == null
            if (initial) busy = true
            try {
                data = withContext(Dispatchers.IO) { store.loadAndMigrate() }
                if (initial) message = ""
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { message = "저장 자료를 열지 못했습니다. 기존 자료를 유지했습니다. 다시 확인해 주세요." }
            finally { if (initial) busy = false }
        }
    }
    fun persist(change: (CareData) -> CareData, failed: () -> Unit = {}, done: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                var newlyStarted: String? = null
                withContext(Dispatchers.IO) {
                    store.update { current ->
                        val next = change(current)
                        if (current.trips.none { it.endedAt == null }) newlyStarted = next.trips.singleOrNull { it.endedAt == null }?.id
                        next
                    }
                }
                newlyStarted?.let { TripRuntime.start(context, it) }
                message = ""
                done()
            } catch (e: kotlinx.coroutines.CancellationException) { failed(); throw e }
            catch (_: Exception) { failed(); message = "저장하지 못했습니다. 기존 자료와 대기 기록을 유지했습니다. 다시 시도해 주세요." }
            finally { busy = false }
        }
    }
    var permissionRefresh by remember { mutableStateOf(0) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRefresh++
        data?.trips?.singleOrNull { it.endedAt == null }?.let { TripRuntime.start(context, it.id) }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRefresh++ }
    val runtime by TripRuntime.status.collectAsState()
    val activeTrip = data?.trips?.singleOrNull { it.endedAt == null }
    // Refresh permissions whenever the authenticated screen is composed again after Settings.
    @Suppress("UNUSED_VARIABLE") val observedPermissionRefresh = permissionRefresh
    val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val notificationsAllowed = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    val tracking = trackingFor(activeTrip, runtime, fineGranted, notificationsAllowed)
    fun finishTrip(id: String) {
        if (busy) return
        val pending = TripRuntime.beginEnd(id)
        persist({ it.appendDetectedArrivals(id, pending).endTrip(id, System.currentTimeMillis()) },
            failed = { TripRuntime.clearEnd(id) }) {
            TripRuntime.clearEnd(id)
            context.stopService(android.content.Intent(context, TripLocationService::class.java))
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("안심 동행", style = MaterialTheme.typography.headlineMedium)
            FilledTonalButton(enabled = !busy, onClick = lock, shape = RoundedCornerShape(16.dp)) { Text("잠금 화면") }
        }
        Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("GPS 기록은 위치 기반 추정이며 실제 승하차 확인이 아닙니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("운행 중에는 화면이 꺼지거나 앱이 잠겨도 기록합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (message.isNotEmpty()) Text(message, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val current = data
        if (current == null) {
            if (!busy) Button(onClick = { reload++ }) { Text("자료 열기 다시 시도") }
        } else if (drafts != null) {
            val rows = drafts!!
            Text(if (editing) "장소 수정" else "장소 한 번에 등록", style = MaterialTheme.typography.titleLarge)
            Row {
                TextButton(enabled = !busy, onClick = { drafts = null; review = false }) { Text("취소") }
                if (!editing) TextButton(enabled = !busy, onClick = { drafts = rows + PlaceDraft() }) { Text("다른 주소 추가") }
                Button(enabled = !busy, onClick = { showErrors = true; if (rows.all { it.error() == null }) review = true }) { Text("저장") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEachIndexed { index, row -> key(row.id) {
                    PlaceRow(row, index + 1, current.elders, !busy, showErrors,
                        update = { changed -> drafts = drafts?.map { if (it.id == row.id) changed else it } },
                        remove = if (!editing && rows.size > 1) ({ drafts = drafts?.filterNot { it.id == row.id } }) else null)
                } }
            }
            if (review) AlertDialog(onDismissRequest = { if (!busy) review = false }, title = { Text("저장할 장소 확인") },
                text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    rows.forEachIndexed { i, row -> Text("${i + 1}. ${row.name}\n${row.address}\n위도 ${row.lat} / 경도 ${row.lon}\n연결 어르신 ${row.elderIds.size}명\n") }
                    Text("총 ${rows.size}개 장소를 암호화 저장합니다.")
                } }, confirmButton = { TextButton(enabled = !busy, onClick = {
                    val ids = rows.map { it.id }.toSet()
                    persist({ latest -> latest.copy(places = latest.places.filterNot { it.id in ids } + rows.map { it.place() },
                        links = latest.links.filterNot { it.placeId in ids } + rows.flatMap { row -> row.elderIds.map { PlaceElder(row.id, it) } })
                    }) { drafts = null; review = false }
                }) { Text("확인하고 저장") } }, dismissButton = { TextButton(enabled = !busy, onClick = { review = false }) { Text("입력으로 돌아가기") } })
        } else {

            if (tab != 2) CareSectionHeading(if (tab == 0) "승하차장소" else "어르신명단", if (tab == 0) current.places.size else current.elders.size)
            if (tab != 2) Button(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp), enabled = !busy, onClick = {
                if (tab == 0) { editing = false; showErrors = false; drafts = listOf(PlaceDraft()) }
                else elderEditor = Elder(name = "")
            }) { Text(if (tab == 0) "장소 추가 · 여러 장소 등록" else "어르신 추가") }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tab == 2) {
                    TripScreen(current, busy, tracking = tracking, requestLocation = {
                        if (!fineGranted) locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        else activeTrip?.let { TripRuntime.start(context, it.id) }
                    }, requestNotifications = {
                        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else {
                            val settings = if (android.os.Build.VERSION.SDK_INT >= 26)
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            else android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                            context.startActivity(settings)
                        }
                    }, endTrip = ::finishTrip, exportTrips = exportTrips, exporting = exporting) { persist(it) }
                } else if (tab == 0) {
                    if (current.places.isEmpty()) Text("등록된 장소가 없습니다. 장소를 추가해 주세요.")
                    current.places.sortedByKoreanName { it.name }.forEach { place -> Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CareSymbol(R.drawable.ic_location)
                            Column(Modifier.weight(1f)) {
                                Text(place.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(4.dp))
                                Text(place.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (place.latitude == null) Text("좌표 미등록 · 수정에서 위치를 지정해 주세요.")
                                val names = current.links.filter { it.placeId == place.id }.mapNotNull { link -> current.elders.find { it.id == link.elderId }?.name }
                                Text("연결 어르신: " + names.joinToString(", ").ifEmpty { "없음" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PlaceCardActions(enabled = !busy, edit = {
                                editing = true; showErrors = false
                                drafts = listOf(PlaceDraft(place.id, place.name, place.address, place.latitude?.toString().orEmpty(), place.longitude?.toString().orEmpty(), current.links.filter { it.placeId == place.id }.map { it.elderId }.toSet()))
                            }, delete = { deletePlace = place })
                        }
                    } }
                } else {
                    if (current.elders.isEmpty()) Text("등록된 어르신이 없습니다. 어르신을 추가하고 장소를 연결해 주세요.")
                    current.elders.sortedByKoreanName { it.name }.forEach { elder -> Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CareSymbol(R.drawable.ic_people)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    androidx.compose.ui.text.buildAnnotatedString {
                                        append(elder.name)
                                        append(" ")
                                        pushStyle(androidx.compose.ui.text.SpanStyle(
                                            color = androidx.compose.ui.graphics.Color(0xFF8E8E93),
                                            fontSize = 14.sp, fontWeight = FontWeight.Normal
                                        ))
                                        append("어르신")
                                        pop()
                                    },
                                    style = MaterialTheme.typography.titleMedium, fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text("연결 장소: " + current.links.filter { it.elderId == elder.id }.mapNotNull { link -> current.places.find { it.id == link.placeId }?.name }.joinToString(", ").ifEmpty { "없음" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PlaceCardActions(enabled = !busy, edit = { elderEditor = elder }, delete = { deleteElder = elder },
                                editDescription = "수정 · 장소 연결", deleteDescription = "어르신 삭제", compact = true, horizontal = true)
                        }
                    } }
                }
            }
        }
        if (current != null && drafts == null) {
            FloatingCareNavigation(
                labels = listOf("승하차장소", "어르신명단", "운행시작 및 기록"),
                selected = tab, onSelect = { tab = it }
            )
        }
    }
    val current = data
    if (current != null) {
        elderEditor?.let { elder -> ElderDialog(elder, current, busy, dismiss = { elderEditor = null }, save = { name, ids ->
            persist({ latest -> latest.copy(elders = latest.elders.filterNot { it.id == elder.id } + elder.copy(name = name),
                links = latest.links.filterNot { it.elderId == elder.id } + ids.map { PlaceElder(it, elder.id) }) }) { elderEditor = null }
        }) }
        deletePlace?.let { p -> ConfirmDelete("장소를 삭제할까요?", "이 장소와 어르신의 연결 ${current.links.count { it.placeId == p.id }}개가 제거됩니다. 어르신 정보는 유지됩니다.", busy,
            { deletePlace = null }) { persist({ it.withoutPlace(p.id) }) { deletePlace = null } } }
        deleteElder?.let { e -> ConfirmDelete("어르신을 삭제할까요?", "어르신 정보와 장소 연결이 제거됩니다. 장소 정보는 유지됩니다.", busy,
            { deleteElder = null }) { persist({ it.withoutElder(e.id) }) { deleteElder = null } } }
    }
}

@Composable
private fun ConfirmDelete(title: String, body: String, busy: Boolean, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(enabled = !busy, onClick = confirm) { Text("삭제") } },
        dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("취소") } })
}

@Composable
private fun ElderDialog(elder: Elder, data: CareData, busy: Boolean, dismiss: () -> Unit, save: (String, Set<String>) -> Unit) {
    var name by remember(elder.id) { mutableStateOf(elder.name) }
    var ids by remember(elder.id) { mutableStateOf(data.links.filter { it.elderId == elder.id }.map { it.placeId }.toSet()) }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("어르신 등록·수정") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(value = name, onValueChange = { name = it }, enabled = !busy, label = { Text("어르신 이름") }, singleLine = true)
            Text("연결 장소 선택 (여러 곳 선택 가능)")
            if (data.places.isEmpty()) Text("장소 등록 후 연결할 수 있습니다.")
            data.places.sortedByKoreanName { it.name }.forEach { p -> Row {
                Checkbox(enabled = !busy, checked = p.id in ids, onCheckedChange = { ids = if (it) ids + p.id else ids - p.id }); Text(p.name, Modifier.padding(top = 12.dp))
            } }
            Text("동명이인은 별도 ID로 저장됩니다.")
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && !busy, onClick = { save(name.trim(), ids) }) { Text("저장") } },
        dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("취소") } })
}

@Composable
private fun PlaceRow(row: PlaceDraft, index: Int, elders: List<Elder>, enabled: Boolean, showErrors: Boolean,
    update: (PlaceDraft) -> Unit, remove: (() -> Unit)?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(row)
    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) scope.coroutineContext.cancelChildren()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    var results by remember { mutableStateOf<List<AddressChoice>>(emptyList()) }
    var feedback by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    fun locate() {
        if (working) return
        scope.launch {
            working = true; feedback = "GPS 수신 중입니다. 최대 25초 기다려 주세요."
            try {
                val location = currentCoordinates(context)
                update(latest.copy(lat = location.latitude.toString(), lon = location.longitude.toString()))
                feedback = "현재 위치를 입력했습니다. 정확도 약 ${location.accuracy.toInt()}m입니다. 주소와 좌표를 확인해 주세요."
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) { feedback = "GPS를 수신하지 못했습니다. 실외에서 다시 시도하거나 좌표를 직접 입력해 주세요." }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { feedback = (e as? EntryFailure)?.guidance ?: "현재 위치를 확인하지 못했습니다. 권한과 GPS 설정을 확인해 주세요." }
            finally { working = false }
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) locate()
        else feedback = "정확한 위치 권한이 필요합니다. 기기 앱 설정에서 허용하거나 좌표를 직접 입력해 주세요."
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(placeOrdinalLabel(index), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(row.name, { update(row.copy(name = it)) }, enabled = enabled && !working, label = { Text("장소 이름") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(row.address, {
            results = emptyList(); feedback = ""; update(row.copy(address = it, lat = "", lon = ""))
        }, enabled = enabled && !working, label = { Text("주소 (이름을 넣지 마세요)") }, modifier = Modifier.fillMaxWidth())
        Text("주소 검색 시 주소만 기기의 외부 검색 서비스로 전달됩니다. 이름을 주소에 입력하지 마세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            TextButton(enabled = enabled && !working && row.address.isNotBlank(), onClick = {
                val query = row.address.trim()
                scope.launch {
                    working = true; feedback = "주소 검색 중…"; results = emptyList()
                    try {
                        results = withTimeout(15_000) { searchAddress(context, query) }
                        feedback = if (results.isEmpty()) "검색 결과가 없습니다. 도로명·건물번호를 확인하거나 좌표를 직접 입력해 주세요." else "검색 결과를 선택해야 좌표가 입력됩니다."
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) { feedback = "주소 검색 시간이 초과되었습니다. 다시 시도해 주세요." }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { feedback = (e as? EntryFailure)?.guidance ?: "주소 검색에 실패했습니다. 다시 시도하거나 좌표를 직접 입력해 주세요." }
                    finally { working = false }
                }
            }) { Text("주소 검색") }
            TextButton(enabled = enabled && !working, onClick = { permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("현재 GPS 위치") }
        }
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (feedback.isNotEmpty()) Text(feedback)
        results.forEach { result -> OutlinedButton(enabled = enabled && !working, onClick = {
            update(latest.copy(address = result.address, lat = result.lat.toString(), lon = result.lon.toString()))
            results = emptyList(); feedback = "선택한 주소의 좌표를 입력했습니다. 위치를 확인해 주세요."
        }, modifier = Modifier.fillMaxWidth()) { Text("${result.address}\n${result.lat}, ${result.lon}") } }
        Text("좌표 직접 입력 (WGS84 십진수)")
        OutlinedTextField(row.lat, { update(row.copy(lat = it)) }, enabled = enabled && !working, label = { Text("위도 (-90~90)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(row.lon, { update(row.copy(lon = it)) }, enabled = enabled && !working, label = { Text("경도 (-180~180)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("연결할 어르신 (여러 명 선택 가능)")
        if (elders.isEmpty()) Text("어르신 탭에서 등록 후 연결할 수 있습니다.")
        elders.sortedByKoreanName { it.name }.forEach { elder -> Row {
            Checkbox(enabled = enabled && !working, checked = elder.id in row.elderIds, onCheckedChange = {
                update(row.copy(elderIds = if (it) row.elderIds + elder.id else row.elderIds - elder.id))
            }); Text(elder.name, Modifier.padding(top = 12.dp))
        } }
        if (showErrors) row.error()?.let { Text("${index}행 오류: $it", color = MaterialTheme.colorScheme.error) }
        if (remove != null) TextButton(enabled = enabled && !working, onClick = remove) { Text("이 입력 행 제거") }
    } }
}

private fun placeOrdinalLabel(index: Int): String {
    val ones = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉")
    val tens = listOf("", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔")
    val ordinal = when {
        index == 1 -> "첫"
        index == 20 -> "스무"
        index in 2..99 -> tens[index / 10] + ones[index % 10]
        else -> index.toString()
    }
    return "${ordinal}번째 장소"
}