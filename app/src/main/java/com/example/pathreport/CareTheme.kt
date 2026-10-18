package com.example.pathreport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun CareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF007AFF), onPrimary = Color.White,
            primaryContainer = Color(0xFFE5F0FF), onPrimaryContainer = Color(0xFF0055B3),
            secondary = Color(0xFF6C6C70), onSecondary = Color.White,
            secondaryContainer = Color(0xFFE5F0FF), onSecondaryContainer = Color(0xFF0055B3),
            background = Color(0xFFF2F2F7), onBackground = Color(0xFF1C1C1E),
            surface = Color(0xFFF2F2F7), onSurface = Color(0xFF1C1C1E),
            surfaceVariant = Color.White, onSurfaceVariant = Color(0xFF6C6C70),
            surfaceContainerHighest = Color.White, surfaceContainerHigh = Color.White,
            surfaceContainer = Color.White, surfaceContainerLow = Color.White,
            surfaceTint = Color.Transparent,
            outline = Color(0xFFC7C7CC), outlineVariant = Color(0xFFE5E5EA),
            error = Color(0xFFD70015)
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(18.dp),
            extraLarge = RoundedCornerShape(24.dp)
        ),
        typography = Typography().let { it.copy(
            headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = it.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        ) },
        content = content
    )
}

@Composable
fun FloatingCareNavigation(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(Modifier.padding(6.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                val active = selected == index
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                        .heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun CareTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFE5E5EA))
            .padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (selected == index) Color.White else Color.Transparent)
                    .selectable(selected = selected == index, role = Role.Tab, onClick = { onSelect(index) })
                    .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected == index) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
    }
}
