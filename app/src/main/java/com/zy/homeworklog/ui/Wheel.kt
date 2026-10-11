package com.zy.homeworklog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 滚轮列（时 / 分 / 天 …）：中间行放大高亮、上下行渐隐，滚动吸附到整行。
 * 「截止时间」与「提前提醒」两处共用。
 */
@Composable
fun WheelColumn(
    values: List<Int>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleRows: Int = 5,
    rowHeight: Dp = 38.dp,
    width: Dp = 76.dp,
    onScrollingChanged: (Boolean) -> Unit = {},
) {
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = values.indexOf(selected).coerceAtLeast(0),
    )
    val fling = rememberSnapFlingBehavior(state)
    val curSelected = rememberUpdatedState(selected)
    val curOnSelected = rememberUpdatedState(onSelected)
    val curOnScrollingChanged = rememberUpdatedState(onScrollingChanged)

    // 中线对应的“浮点行号”：静止时为整数、拖动时为小数。用 derivedStateOf 计算，避免直接读
    // state.layoutInfo（那种写法会在每次重新布局时触发重组，lint 会报 FrequentStateRead）。
    val centeredRow by remember(rowPx) {
        derivedStateOf { state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / rowPx }
    }

    // 先提交停止后的值，再放开确认按钮，避免确认读到滑动前的旧值。
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collect { moving ->
                if (!moving) {
                    values.getOrNull(centeredRow.roundToInt())?.let { v ->
                        if (v != curSelected.value) curOnSelected.value(v)
                    }
                }
                curOnScrollingChanged.value(moving)
            }
    }

    // 外部改变 selected（如点击「预设」快捷键）时，把滚轮滚到对应行；
    // 用户自己滑动时不触发（停止时 selected 已等于中线行，索引相同 → 不做动画）。
    LaunchedEffect(selected) {
        val idx = values.indexOf(selected)
        if (idx >= 0 && idx != centeredRow.roundToInt()) {
            curOnScrollingChanged.value(true)
            state.animateScrollToItem(idx)
        }
    }

    Box(modifier.width(width).height(rowHeight * visibleRows)
        .clip(RoundedCornerShape(16.dp)).background(C.SurfaceHigh)) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(rowHeight)
            .background(C.Blue.copy(alpha = if (C.IsLight) 0.10f else 0.14f)))
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = rowHeight * (visibleRows / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(values.size) { i ->
                Box(Modifier.height(rowHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = values[i].toString().padStart(2, '0'),
                        color = C.Text,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.graphicsLayer {
                            val distance = abs(i - centeredRow)
                            val scale = (1f - distance * 0.10f).coerceIn(0.80f, 1f)
                            scaleX = scale
                            scaleY = scale
                            alpha = (1f - distance * 0.32f).coerceIn(0.18f, 1f)
                        },
                    )
                }
            }
        }
    }
}

/** 底部胶囊按钮：secondary = 灰底（次要），否则蓝底（主操作）。 */
@Composable
fun PillButton(
    text: String,
    secondary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (secondary) C.SurfaceHigh else C.Text)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (secondary) C.Text else C.Background, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
