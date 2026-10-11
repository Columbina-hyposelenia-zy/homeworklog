package com.zy.homeworklog.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onSubjects: () -> Unit,
    onRecurring: () -> Unit,
    onReminders: () -> Unit,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as com.zy.homeworklog.HomeworkApp
    val appearance = LocalAppAppearance.current
    var confirmClear by remember { mutableStateOf(false) }
    val uncheckedTrackColor = if (C.IsLight) Color(0xFF626975) else Color(0xFF7C8594)
    val uncheckedThumbColor = if (C.IsLight) Color.White else Color(0xFFF1F2F4)
    val uncheckedBorderColor = if (C.IsLight) Color(0xFF626975) else Color(0xFFADB5C3)
    val appearanceSwitchColors = SwitchDefaults.colors(
        checkedThumbColor = C.OnBlue,
        checkedTrackColor = C.Blue,
        checkedBorderColor = C.Blue,
        uncheckedThumbColor = uncheckedThumbColor,
        uncheckedTrackColor = uncheckedTrackColor,
        uncheckedBorderColor = uncheckedBorderColor,
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Background)
    ) {
        PageTopBar("设置", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        SectionTitle("记录")
        CardGroup {
        NavRow("历史记录（已完成）", onClick = onHistory)
        CardDivider()
        NavRow("周期任务", onClick = onRecurring)
        }

        SectionTitle("外观")
        CardGroup {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .toggleable(
                    value = appearance.light,
                    role = Role.Switch,
                    onValueChange = appearance::setLightMode,
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("浅色模式", color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = appearance.light,
                onCheckedChange = null,
                colors = appearanceSwitchColors,
            )
        }
        }
        Text("应用于所有页面与弹窗。", color = C.Sub, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp))

        SectionTitle("提醒与权限")
        CardGroup {
        NavRow("提醒设置 / 通知权限", onClick = onReminders)
        }

        SectionTitle("数据")
        CardGroup {
        NavRow("科目与颜色", onClick = onSubjects)
        CardDivider()
        NavRow("清空全部数据", value = "清空", valueColor = C.Overdue) { confirmClear = true }
        }

        Text(
            "时间采用 24 小时制",
            color = C.Sub,
            fontSize = 12.sp,
            modifier = Modifier.padding(16.dp, 12.dp),
        )
        Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空全部数据？",
            text = "科目、作业记录、周期规则与偏好将全部删除，不可恢复。",
            onConfirm = {
                confirmClear = false
                app.appScope.launch {
                    app.repo.clearAll()
                    Toast.makeText(ctx, "已清空", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun NavRow(label: String, value: String = "›", valueColor: Color = C.Sub, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, color = valueColor, fontSize = 13.sp)
    }
}
