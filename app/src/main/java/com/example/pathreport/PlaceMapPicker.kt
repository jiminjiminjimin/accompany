package com.example.pathreport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*

@Composable
internal fun PlaceMapPicker(initial: PlaceDraft, dismiss: () -> Unit, choose: (AddressChoice) -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var selection by remember { mutableStateOf(initial.lat.toDoubleOrNull()?.let { lat ->
        initial.lon.toDoubleOrNull()?.takeIf { inKoreaMap(lat, it) }?.let { lat to it }
    }) }
    var address by remember { mutableStateOf(if (selection != null) initial.address else "") }
    var query by remember { mutableStateOf(initial.address) }
    var results by remember { mutableStateOf<List<AddressChoice>>(emptyList()) }
    var message by remember { mutableStateOf("검색하거나 지도를 눌러 핀을 찍어 주세요. 핀을 끌어서 조정할 수도 있습니다.") }
    var searching by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var addressEdited by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var addressJob by remember { mutableStateOf<Job?>(null) }
    fun pick(lat: Double, lon: Double, knownAddress: String? = null) {
        searchJob?.cancel(); searching = false
        addressJob?.cancel(); resolving = false
        selection = lat to lon; address = knownAddress.orEmpty(); results = emptyList(); addressEdited = false
        keyboard?.hide()
        if (knownAddress != null) { message = "핀 위치와 주소를 확인한 뒤 이 위치 사용을 눌러 주세요."; return }
        addressJob = scope.launch {
            resolving = true; message = "선택한 위치의 주소를 찾고 있습니다…"
            try {
                val found = withTimeout(10_000) { addressAt(context, lat, lon) }
                if (!addressEdited) address = found.orEmpty()
                message = if (found.isNullOrBlank()) "주소를 찾지 못했습니다. 핀은 선택됐으니 주소를 직접 입력해 주세요."
                    else "핀 위치와 주소를 확인한 뒤 이 위치 사용을 눌러 주세요."
            } catch (_: TimeoutCancellationException) { message = "주소 확인이 늦어지고 있습니다. 주소를 직접 입력해도 됩니다." }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "주소를 불러오지 못했습니다. 선택한 위치의 주소를 직접 입력해 주세요." }
            finally { resolving = false }
        }
    }
    fun search() {
        if (query.isBlank() || searching) return
        keyboard?.hide()
        addressJob?.cancel(); resolving = false
        val term = query.trim()
        searchJob = scope.launch {
            searching = true; results = emptyList(); message = "한국 주소·장소 검색 중…"
            try {
                results = withTimeout(15_000) { searchAddress(context, term) }
                message = if (results.isEmpty()) "검색 결과가 없습니다. 도로명·건물번호로 검색하거나 지도에서 선택해 주세요."
                    else "검색 결과를 누르면 해당 위치로 지도가 이동합니다."
            } catch (_: TimeoutCancellationException) { message = "검색 응답이 늦어지고 있습니다. 다시 검색하거나 지도에서 선택해 주세요." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = (e as? EntryFailure)?.guidance ?: "검색 서비스가 응답하지 않았습니다. 지도에서 직접 선택할 수 있습니다." }
            finally { searching = false }
        }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("장소 위치 선택", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = dismiss) { Text("취소") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text("주소 또는 건물명 검색") },
                        singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }))
                    Button(enabled = query.isNotBlank() && !searching, onClick = { search() }) { Text("검색") }
                }
                if (searching || resolving) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(message, style = MaterialTheme.typography.bodySmall)
                if (results.isNotEmpty()) Column(Modifier.heightIn(max = 130.dp).verticalScroll(rememberScrollState())) {
                    results.forEach { result ->
                        TextButton(onClick = { pick(result.lat, result.lon, result.address) }, modifier = Modifier.fillMaxWidth()) { Text(result.address) }
                    }
                }
                CareMapView("picker.html", "{}", Modifier.weight(1f).fillMaxWidth(), selection = selection,
                    onPick = { lat, lon -> pick(lat, lon) })
                if (selection != null) OutlinedTextField(address, { address = it; addressEdited = true },
                    label = { Text("선택한 위치의 주소 · 수정 가능") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                Text("검색어와 선택 좌표는 주소 검색 서비스로 전송됩니다. 어르신 이름은 입력하지 마세요.", style = MaterialTheme.typography.bodySmall)
                Button(enabled = selection != null && address.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                    selection?.let { (lat, lon) -> choose(AddressChoice(address.trim(), lat, lon)) }
                }) { Text("이 위치 사용") }
            }
        }
    }
}
