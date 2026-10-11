package com.zy.homeworklog.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 动效 token 的唯一来源。
 * 任何时长/曲线都引用这里，不在页面里就地写死数字。
 */
object Motion {
    const val MICRO = 90      // 按压反馈、收框、选中态
    const val FAST = 160      // 滑动吸附、切换
    const val BASE = 220      // 展开 / 收起主体高度
    const val MEDIUM = 300    // 折叠消失 + 补位 + 旧项让位
    const val SLOW = 380      // 页面级转场
    const val CONTENT_FADE = 120 // 折叠时的内容淡出（与塌陷并行）
    const val EXPANSION_GUARD = BASE + MICRO // 留一段启动/提交余量，避免末帧重新叠加位置动画。

    /** ease.standard = cubic-bezier(0.2, 0, 0, 1) */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** ease.emphasized = cubic-bezier(0.05, 0.7, 0.1, 1) —— 换项让位 / 整表重排 */
    val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** ease.exit = cubic-bezier(0.4, 0, 1, 1) —— 折叠消失 + 下方补位（共享进度） */
    val Exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
}

/** 灰阶卡片尺寸 token；与 [Motion] 一样是布局数值的唯一来源。 */
object Dim {
    val marginH = 16.dp
    val tabH = 48.dp
    val tabPadH = 16.dp
    val tabLabelSp = 11.sp
    val tabLabelLhSp = 14.sp
    val tabValueSp = 16.sp
    val tabValueLhSp = 20.sp
    val bodyMinH = 48.dp
    val bodyPadV = 8.dp
    val expTopPad = 12.dp
    val editGap = 8.dp
    val lhEdit = 18.sp
    val editBottomPad = 12.dp
    val editBlockH = editGap + lhEdit.value.dp + editBottomPad
    val lhContent = 22.sp
    val maxExpandedHeight = 352.dp
    fun expandedBodyHeight(lines: Int): Dp =
        (expTopPad + lhContent.value.dp * lines + editBlockH + expTopPad)
            .coerceIn(bodyMinH, maxExpandedHeight)
    val padH = 16.dp
    val colGap = 8.dp
    val countW = 26.dp
    val btnSize = 42.dp
    val btnInset = 10.dp
    val btnEnterShift = 4.dp
    val opW = 62.dp
    val maxOffset = 62.dp
    val openThreshold = 25.dp
    val reverseTolerance = 24.dp
    val topBarH = 56.dp
    val listTopPad = 8.dp
    val radiusOuter = 14.dp
    /** 记录卡片之间的垂直间距（首页列表 Arrangement.spacedBy）。 */
    val recordGap = 10.dp
}
