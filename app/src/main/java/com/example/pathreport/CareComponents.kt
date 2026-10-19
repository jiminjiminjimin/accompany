package com.example.pathreport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CareSectionHeading(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
            Text("전체 $count", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun CareSymbol(resource: Int) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(resource), null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun TripStartTiles(enabled: Boolean, start: (TripDirection) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TripDirection.entries.forEach { direction ->
            val morning = direction == TripDirection.TO_CENTER
            val ink = if (morning) Color(0xFF245A91) else Color(0xFF9B3535)
            Card(onClick = { start(direction) }, enabled = enabled, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (morning) Color(0xFFDCEBFF) else Color(0xFFFFE1E1), contentColor = ink)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(if (morning) R.drawable.ic_sun else R.drawable.ic_moon), null, Modifier.size(28.dp))
                    Text("${direction.label} 운행 시작", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("대상 어르신 선택", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
