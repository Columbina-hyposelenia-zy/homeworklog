package com.zy.homeworklog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private object DarkPalette {
    // 灰阶卡片配色：无金属高光、材质贴图或厚重阴影。
    val Background = Color(0xFF101113)
    val Surface = Color(0xFF202226)
    val SurfaceHigh = Color(0xFF282B30)
    val Text = Color(0xFFF1F2F4)
    val Hair = Color(0xFF34373D)
    val Line = Hair
    val Sub = Color(0xFFA7ADB7)
    val Urgent = Color(0xFFE5C080)
    val Overdue = Color(0xFFEB9292)
    val Later = Sub
    val Green = Color(0xFF657D64)
    val Red = Color(0xFFA66F70)
    val Blue = Color(0xFF9BBEDD)
}

@Immutable
data class AppColors(
    val background: Color, val surface: Color, val surfaceHigh: Color,
    val text: Color, val sub: Color, val hair: Color,
    val urgent: Color, val overdue: Color, val green: Color, val red: Color,
    val blue: Color, val light: Boolean,
)

private val DarkColors = AppColors(
    DarkPalette.Background, DarkPalette.Surface, DarkPalette.SurfaceHigh,
    DarkPalette.Text, DarkPalette.Sub, DarkPalette.Hair, DarkPalette.Urgent,
    DarkPalette.Overdue, DarkPalette.Green, DarkPalette.Red, DarkPalette.Blue, false,
)
private val LightColors = AppColors(
    Color(0xFFF0F1F3), Color.White, Color(0xFFE7E9ED),
    Color(0xFF202226), Color(0xFF626975), Color(0xFFD5D9DF),
    Color(0xFF8B641F), Color(0xFFB13B44), Color(0xFF51694F),
    Color(0xFF94505A), Color(0xFF315F86), true,
)
private val LocalAppColors = staticCompositionLocalOf { DarkColors }

/** 所有页面共用当前调色板；只读 CompositionLocal，不在全局变量中修改颜色。 */
object C {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
    val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceHigh
    val Text: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.text
    val Sub: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.sub
    val Hair: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.hair
    val Line: Color @Composable @ReadOnlyComposable get() = Hair
    val Urgent: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.urgent
    val Overdue: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.overdue
    val Later: Color @Composable @ReadOnlyComposable get() = Sub
    val Green: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.green
    val Red: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.red
    val Blue: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.blue
    val OnBlue: Color @Composable @ReadOnlyComposable get() = if (IsLight) Color.White else Background
    val IsLight: Boolean @Composable @ReadOnlyComposable get() = LocalAppColors.current.light
}

@Stable
class AppAppearance(private val prefs: SharedPreferences) {
    // 保留既有键：老版本已开启的浅色首页设置自动扩展到全应用。
    var light by mutableStateOf(prefs.getBoolean("dark_text", false))
        private set
    fun setLightMode(enabled: Boolean) {
        light = enabled
        prefs.edit().putBoolean("dark_text", enabled).apply()
    }
    internal fun refresh() { light = prefs.getBoolean("dark_text", false) }
}
val LocalAppAppearance = staticCompositionLocalOf<AppAppearance> { error("AppTheme required") }

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** 科目色板（10 色，一行 5 个展示）；新建科目选到已占用颜色时，新科目优先、原科目随机换色。 */
val PALETTE: List<Long> = listOf(
    0xFF4FC3F7, 0xFFBA68C8, 0xFF4DD0A5, 0xFFFFB74D,
    0xFFF06292, 0xFF9575CD, 0xFFAED581, 0xFFFF8A65,
    0xFF26C6DA, 0xFF7986CB,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context.applicationContext) { context.getSharedPreferences("prefs", 0) }
    val appearance = remember(prefs) { AppAppearance(prefs) }
    DisposableEffect(prefs, appearance) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "dark_text" || key == null) appearance.refresh()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val colors = if (appearance.light) LightColors else DarkColors
    val scheme = remember(colors) {
        if (colors.light) lightColorScheme(
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.sub,
            primary = colors.text, onPrimary = colors.background,
            secondary = colors.blue, onSecondary = Color.White,
            outline = colors.hair, error = colors.overdue, onError = Color.White,
        ) else darkColorScheme(
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.sub,
            primary = colors.text, onPrimary = colors.background,
            secondary = colors.blue, onSecondary = colors.background,
            outline = colors.hair, error = colors.overdue, onError = colors.background,
        )
    }
    SideEffect {
        context.activity()?.window?.let { window ->
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            window.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = colors.light
                isAppearanceLightNavigationBars = colors.light
            }
        }
    }
    CompositionLocalProvider(LocalAppColors provides colors, LocalAppAppearance provides appearance) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** 次级页面顶栏：‹ 返回 + 标题 +（可选）右侧确认（首页冻结口径为无标题，仅次级页使用）。 */
@Composable
fun PageTopBar(
    title: String,
    onBack: () -> Unit,
    onConfirm: (() -> Unit)? = null,
    confirmLabel: String = "保存",
    confirmColor: Color = C.Text,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(C.Background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(AppIcon.BACK, "返回", onClick = onBack)
            Text(title, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            if (onConfirm != null) {
                Box(
                    Modifier.height(48.dp).clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onConfirm).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(confirmLabel, color = confirmColor, fontSize = 15.sp)
                }
            } else {
                Box(Modifier.size(48.dp))
            }
        }
    }
}

/** 表单行：灰标签 +（可选小标签）+ 右侧值 + ›。 */
@Composable
fun FormRow(
    label: String,
    value: String,
    valueColor: Color = C.Text,
    tag: String? = null,
    supportingText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.Sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Column(Modifier.weight(1.7f).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
            Text(value, color = valueColor, fontSize = 15.sp, textAlign = TextAlign.End)
            val hint = supportingText ?: tag
            if (hint != null) Text(hint, color = C.Sub, fontSize = 11.sp,
                textAlign = TextAlign.End, modifier = Modifier.padding(top = 4.dp))
        }
        if (onClick != null) {
            Text("›", color = C.Sub, fontSize = 16.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** 页面级简单标题行（设置分组用）。 */
@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = C.Sub,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 2.dp),
    )
}

/** 确认弹窗。 */
@Composable
fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = C.Text) },
        text = { Text(text, color = C.Sub) },
        confirmButton = {
            DialogAction("确定", onClick = onConfirm)
        },
        dismissButton = {
            DialogAction("取消", secondary = true, onClick = onDismiss)
        },
        containerColor = C.Surface,
        shape = RoundedCornerShape(24.dp),
    )
}

/** 选项列表弹窗。 */
@Composable
fun <T> ListDialog(
    title: String,
    options: List<T>,
    label: (T) -> String,
    colorOf: ((T) -> Color)? = null,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = C.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (options.isEmpty()) Text("（空）", color = C.Sub)
                options.forEach { op ->
                    Text(
                        label(op),
                        color = colorOf?.invoke(op) ?: C.Text,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(op) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            DialogAction("取消", secondary = true, onClick = onDismiss)
        },
        containerColor = C.Surface,
        shape = RoundedCornerShape(24.dp),
    )
}
