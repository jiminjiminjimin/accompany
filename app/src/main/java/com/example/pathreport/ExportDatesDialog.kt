package com.example.pathreport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportDatesDialog(trips: List<Trip>, dismiss: () -> Unit, export: (Set<String>) -> Unit) {
    val format = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
    val groups = trips.filter { it.endedAt != null }.sortedByDescending { it.startedAt }
        .groupBy { format.format(Date(it.startedAt)) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val chosen = selected.intersect(groups.keys)
    AlertDialog(onDismissRequest = dismiss, title = { Text("저장할 날짜 선택") }, text = {
        Column {
            Text("선택한 날짜의 등원·하원 기록을 함께 저장합니다.")
            Row {
                TextButton(onClick = { selected = groups.keys.toSet() }) { Text("전체 선택") }
                TextButton(onClick = { selected = emptySet() }) { Text("선택 해제") }
            }
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                groups.forEach { (date, records) ->
                    Row(Modifier.fillMaxWidth().toggleable(value = date in chosen, role = Role.Checkbox,
                        onValueChange = { checked -> selected = if (checked) selected + date else selected - date })
                        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = date in chosen, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(date, style = MaterialTheme.typography.titleMedium)
                            Text("등원 ${records.count { it.direction == TripDirection.TO_CENTER }}건 · 하원 ${records.count { it.direction == TripDirection.FROM_CENTER }}건",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text("${chosen.size}개 날짜 선택", style = MaterialTheme.typography.bodyMedium)
        }
    }, confirmButton = {
        TextButton(enabled = chosen.isNotEmpty(), onClick = {
            export(groups.filterKeys { it in chosen }.values.flatten().map { it.id }.toSet())
        }) { Text("선택한 날짜 저장") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("취소") } })
}
