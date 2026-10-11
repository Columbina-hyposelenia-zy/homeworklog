package com.zy.homeworklog.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zy.homeworklog.reminder.AlarmScheduler
import com.zy.homeworklog.util.TimeFmt

/** 提前量快捷预设：滚轮 + 下方快捷胶囊。 */
private val LEAD_PRESETS: List<Long> = listOf(
    30L * 60_000,           // 30 分钟
    60L * 60_000,           // 1 小时
    3L * 60 * 60_000,       // 3 小时
    24L * 60 * 60_000,      // 1 天
)

private data class ReminderPermissions(val granted: Boolean)

private fun readReminderPermissions(context: Context): ReminderPermissions = ReminderPermissions(
    granted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED,
)

/**
 * 全局提醒设置：提醒总开关、提前量（滚轮 天/时/分 + 快捷胶囊）、通知权限入口。
 * 未授权通知权限时不会登记任何闹钟。
 */
@Composable
fun ReminderSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("prefs", 0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(prefs.getBoolean(AlarmScheduler.PREF_ENABLED, true)) }
    var leadMs by remember {
        mutableLongStateOf(prefs.getLong(AlarmScheduler.PREF_LEAD_MS, AlarmScheduler.DEFAULT_LEAD_MS))
    }
    var showLead by remember { mutableStateOf(false) }

    var permissions by remember(ctx) { mutableStateOf(readReminderPermissions(ctx)) }
    val refreshPermissions = remember(ctx) {
        { permissions = readReminderPermissions(ctx) }
    }
    val req = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
        AlarmScheduler.refresh(ctx) // 授权后才排闹钟（未授权不排，避免空转）
    }

    DisposableEffect(lifecycleOwner, ctx) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
                AlarmScheduler.refresh(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().background(C.Background)) {
        PageTopBar("提醒设置", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Text(
            "选择默认提前量，也可以在每条作业中单独设置提醒时间。",
            color = C.Sub,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
        )

        CardGroup {
        SettingSwitchRow("提醒总开关", enabled) {
            enabled = it
            prefs.edit().putBoolean(AlarmScheduler.PREF_ENABLED, it).apply()
            AlarmScheduler.refresh(ctx) // 任何改动都要重排，否则会漏
        }
        CardDivider()

        // 提前量可选：点击进滚轮 + 快捷胶囊
        FormRow(
            label = "提前提醒",
            value = if (enabled) TimeFmt.fmtLead(leadMs) else "已关闭",
            valueColor = if (enabled) C.Text else C.Sub,
            onClick = { showLead = true },
        )
        }

        CardGroup {

        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp)
                .clickable {
                    if (Build.VERSION.SDK_INT >= 33 && !permissions.granted) {
                        req.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("通知权限", color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(
                if (permissions.granted) "已授权" else "未授权 · 点击申请",
                color = if (permissions.granted) C.Sub else C.Urgent,
                fontSize = 13.sp,
            )
        }
        }

        Text(
            "每条作业只提醒一次（改过提醒时间会重新提醒）；逾期不再提醒；多条同时到点会合并成一条通知。",
            color = C.Sub,
            fontSize = 11.sp,
            modifier = Modifier.padding(16.dp, 12.dp),
        )
        Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (showLead) {
        LeadTimeDialog(
            initial = leadMs,
            onDismiss = { showLead = false },
            onConfirm = { v ->
                leadMs = v
                prefs.edit().putLong(AlarmScheduler.PREF_LEAD_MS, v).apply()
                AlarmScheduler.refresh(ctx) // 提前量变了 → 所有「跟随全局」的提醒时刻都要重算
                showLead = false
            },
        )
    }
}

/** 提前量选择：滚轮（天 / 时 / 分）+ 快捷胶囊。 */
@Composable
private fun LeadTimeDialog(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val ctx = LocalContext.current
    var day by remember { mutableIntStateOf((initial / 86_400_000L).toInt()) }
    var hour by remember { mutableIntStateOf((initial % 86_400_000L / 3_600_000L).toInt()) }
    var minute by remember { mutableIntStateOf((initial % 3_600_000L / 60_000L).toInt()) }
    var dayBusy by remember { mutableStateOf(false) }
    var hourBusy by remember { mutableStateOf(false) }
    var minuteBusy by remember { mutableStateOf(false) }
    val pending = ((day * 24L + hour) * 60L + minute) * 60_000L
    val busy = dayBusy || hourBusy || minuteBusy

    PickerDialog(
        title = "提前多久提醒",
        subtitle = TimeFmt.fmtLead(pending),
        onDismissRequest = onDismiss,
        actions = {
            PillButton("取消", secondary = true, modifier = Modifier.weight(1f)) { onDismiss() }
            PillButton("确定", secondary = false, modifier = Modifier.weight(1.3f), enabled = !busy) {
                if (dayBusy || hourBusy || minuteBusy) {
                    return@PillButton
                } else if (pending <= 0L) {
                    Toast.makeText(ctx, "至少提前 1 分钟", Toast.LENGTH_SHORT).show()
                } else {
                    onConfirm(pending)
                }
            }
        },
    ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                WheelUnit(
                    "天", 0..7, day,
                    onScrollingChanged = { dayBusy = it },
                    onSelected = { day = it },
                )
                WheelUnit(
                    "时", 0..23, hour,
                    onScrollingChanged = { hourBusy = it },
                    onSelected = { hour = it },
                )
                WheelUnit(
                    "分", 0..59, minute,
                    onScrollingChanged = { minuteBusy = it },
                    onSelected = { minute = it },
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEAD_PRESETS.forEach { p ->
                    PresetChip(
                        text = TimeFmt.fmtLead(p),
                        selected = p == pending,
                        modifier = Modifier.weight(1f),
                    ) {
                        val m = p / 60_000L
                        day = (m / (60 * 24)).toInt()
                        hour = (m % (60 * 24) / 60).toInt()
                        minute = (m % 60).toInt()
                    }
                }
            }


    }
}

/** 单个滚轮 + 上方单位标签。 */
@Composable
private fun WheelUnit(
    label: String,
    range: IntRange,
    selected: Int,
    onScrollingChanged: (Boolean) -> Unit,
    onSelected: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = C.Sub, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        // 具名参数：避免尾随 lambda 误绑到最后的 width 形参上
        WheelColumn(
            range.toList(),
            selected,
            onSelected = onSelected,
            onScrollingChanged = onScrollingChanged,
            width = 58.dp,
        )
    }
}

/** 快捷胶囊：选中时蓝底，未选中时描边（参考系统计时器预设按钮）。 */
@Composable
private fun PresetChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val accent = C.Text
    Box(
        modifier
            .height(34.dp)
            .clip(shape)
            .background(if (selected) accent else C.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) C.Background else C.Text, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = C.Green),
        )
    }
}
