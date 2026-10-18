package com.example.pathreport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LockScreen(message: String, unlock: () -> Unit) {
    val blue = Color(0xFF007AFF)
    val secondary = Color(0xFF6C6C70)
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF2F2F7)) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(Modifier.widthIn(max = 400.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
                    Canvas(Modifier.size(88.dp).padding(22.dp)) {
                        drawArc(blue, 180f, 180f, false, Offset(size.width * .25f, size.height * .08f),
                            Size(size.width * .5f, size.height * .55f), style = Stroke(3.dp.toPx()))
                        drawRoundRect(blue, Offset(size.width * .12f, size.height * .37f),
                            Size(size.width * .76f, size.height * .57f), CornerRadius(7.dp.toPx()))
                        drawLine(Color.White, Offset(size.width * .5f, size.height * .59f),
                            Offset(size.width * .5f, size.height * .73f), strokeWidth = 3.dp.toPx())
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("안심 동행", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1C1E))
                Spacer(Modifier.height(8.dp))
                Text("어르신의 등-하원 시간을 기록합니다.", style = MaterialTheme.typography.bodyLarge, color = secondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = unlock,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = blue, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    Text("기기 인증으로 잠금 해제", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(14.dp))
                Text("기기에 설정된 인증으로 시작하세요", style = MaterialTheme.typography.bodyMedium, color = secondary, textAlign = TextAlign.Center)
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(40.dp))
                Text("개인정보는 기기 안에 암호화되어 보관됩니다.\n앱을 벗어나면 자동으로 잠깁니다.",
                    style = MaterialTheme.typography.bodySmall, color = secondary, textAlign = TextAlign.Center, lineHeight = 20.sp)
            }
        }
    }
}
