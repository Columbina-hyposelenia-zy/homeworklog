package com.zy.homeworklog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.remember
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** 仅展示时柔化科目色，保留数据库内原始颜色与色板冲突判断。 */
@Composable
fun subjectColor(argb: Long): Color {
    val light = C.IsLight
    return remember(argb, light) {
        if (!light) lerp(Color(argb), Color(0xFFABAEBB), 0.36f) else {
            // 浅色卡片上的科目文字仍可识别，原始色板与数据库不变。
            var result = Color(argb).copy(alpha = 1f)
            var amount = 0f
            while (ColorUtils.calculateContrast(result.toArgb(), Color.White.toArgb()) < 4.5) {
                amount += 0.05f
                result = lerp(Color(argb).copy(alpha = 1f), Color.Black, amount.coerceAtMost(1f))
            }
            result
        }
    }
}

@Composable
fun CardGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(C.Surface),
        content = content,
    )
}

@Composable
fun CardDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.5.dp).background(C.Hair))
}

enum class AppIcon { ADD, SETTINGS, BACK }

/** 矢量图标与 48dp 点击区，不依赖系统字体的齿轮字形。 */
@Composable
fun IconAction(icon: AppIcon, description: String, color: Color = C.Text, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val s = size.minDimension
            val center = Offset(s / 2, s / 2)
            val width = 1.7.dp.toPx()
            when (icon) {
                AppIcon.ADD -> {
                    drawLine(color, Offset(s * 0.14f, s / 2), Offset(s * 0.86f, s / 2), width, StrokeCap.Round)
                    drawLine(color, Offset(s / 2, s * 0.14f), Offset(s / 2, s * 0.86f), width, StrokeCap.Round)
                }
                AppIcon.BACK -> {
                    val p = Path().apply {
                        moveTo(s * 0.65f, s * 0.18f)
                        lineTo(s * 0.33f, s * 0.50f)
                        lineTo(s * 0.65f, s * 0.82f)
                    }
                    drawPath(p, color, style = Stroke(width, cap = StrokeCap.Round))
                }
                AppIcon.SETTINGS -> {
                    val p = Path()
                    for (i in 0 until 32) {
                        val angle = i * Math.PI / 16 - Math.PI / 2
                        val r = s * if (i % 4 < 2) 0.43f else 0.35f
                        val x = center.x + cos(angle).toFloat() * r
                        val y = center.y + sin(angle).toFloat() * r
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    p.close()
                    drawPath(p, color, style = Stroke(width))
                    drawCircle(color, radius = s * 0.13f, center = center, style = Stroke(width))
                }
            }
        }
    }
}
