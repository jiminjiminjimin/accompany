package com.example.pathreport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun PlaceCardActions(enabled: Boolean, edit: () -> Unit, delete: () -> Unit, editDescription: String = "수정 · 어르신 연결", deleteDescription: String = "장소 삭제", compact: Boolean = false, horizontal: Boolean = false) {
    val buttons: @Composable () -> Unit = {
        FilledIconButton(onClick = edit, enabled = enabled, modifier = Modifier.size(if (compact) 36.dp else 48.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE5F0FF), contentColor = Color(0xFF245A91))) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = editDescription, modifier = Modifier.size(if (compact) 20.dp else 22.dp))
        }
        FilledIconButton(onClick = delete, enabled = enabled, modifier = Modifier.size(if (compact) 36.dp else 48.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFFFE1E1), contentColor = Color(0xFF9B3535))) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = deleteDescription, modifier = Modifier.size(if (compact) 20.dp else 22.dp))
        }
    }
    if (horizontal) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { buttons() }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 8.dp)) { buttons() }
    }
}
