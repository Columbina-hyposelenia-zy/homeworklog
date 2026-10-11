package com.zy.homeworklog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 确认框与可编辑表单共用的中性卡片容器。 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = C.Surface,
    shape: Shape = RoundedCornerShape(24.dp),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 400.dp).fillMaxWidth(),
        title = title,
        text = text?.let { body -> {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) { body() }
        } },
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        containerColor = containerColor,
        titleContentColor = C.Text,
        textContentColor = C.Sub,
        tonalElevation = 0.dp,
        shape = shape,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}

/** 整个 48dp 操作区（含圆角背景）都可点击。 */
@Composable
fun DialogAction(text: String, secondary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).widthIn(min = 76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (secondary) Color.Transparent else C.SurfaceHigh)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (secondary) C.Sub else C.Blue,
            fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** 时间与提醒选择器共用同一套标题、容器与间距。 */
@Composable
fun PickerDialog(
    title: String,
    subtitle: String,
    onDismissRequest: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(max = 400.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp), color = C.Surface, tonalElevation = 0.dp,
        ) {
            Column(
                Modifier.heightIn(max = 620.dp).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(subtitle, color = C.Sub, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally, content = content)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
            }
        }
    }
}
