package com.zy.homeworklog.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zy.homeworklog.HomeworkApp
import com.zy.homeworklog.reminder.AlarmScheduler
import com.zy.homeworklog.util.TimeFmt
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Immutable
data class HomeItem(
    val id: Long,
    val subjectName: String,
    val colorArgb: Long,
    val count: Int,
    val deadline: Long,
    val content: String,
    val imagePaths: List<String> = emptyList(),
)

/** 文本行盒统一裁掉系统字体额外内边距，字号与自然行高留有空间。 */
private fun trimmedTextStyle(fontSize: TextUnit, lineHeight: TextUnit) = TextStyle(
    fontSize = fontSize,
    lineHeight = lineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)
private val TabLabelStyle = trimmedTextStyle(Dim.tabLabelSp, Dim.tabLabelLhSp)
private val TabValueStyle = trimmedTextStyle(Dim.tabValueSp, Dim.tabValueLhSp)
private val ContentLineStyle = trimmedTextStyle(14.sp, 22.sp).copy(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontStyle = FontStyle.Normal,
    letterSpacing = 0.sp,
)
private val CountLineStyle = trimmedTextStyle(23.sp, 28.sp).copy(
    fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, letterSpacing = 0.sp,
)
private val LargeCountLineStyle = CountLineStyle.copy(fontSize = 16.sp, lineHeight = 22.sp)
private val EditLineStyle = trimmedTextStyle(12.sp, 18.sp)

class HomeViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = (app as HomeworkApp).repo

    var tick by mutableStateOf(System.currentTimeMillis())
        private set
    var items by mutableStateOf<List<HomeItem>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            // 超期自动清理：未完成且超期 ≥ 10 天的记录直接删掉。
            // 先扫再订阅，第一帧列表就已经是干净的。
            repo.sweepExpired()
            combine(repo.incomplete, repo.counts, repo.subjects, repo.images) { recs, counts, subs, images ->
                val cnt = counts.associate { it.subjectId to it.cnt }
                val smap = subs.associateBy { it.id }
                val imageMap = images.groupBy { it.recordId }
                recs.map { r ->
                    val s = smap[r.subjectId]
                    HomeItem(
                        id = r.id,
                        subjectName = s?.name ?: "?",
                        colorArgb = s?.colorArgb ?: 0xFF9E9E9E,
                        count = cnt[r.subjectId] ?: 0,
                        deadline = r.deadline,
                        content = r.content,
                        imagePaths = imageMap[r.id].orEmpty().map { it.path },
                    )
                }
            }.collect { items = it }
        }
        // 倒计时按分钟对齐刷新（真实时间计算，非示例文字）
        viewModelScope.launch {
            while (true) {
                delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
                val now = System.currentTimeMillis()
                tick = now
                // 列表一直开着时也要能自动清理（比如跨过 10 天阈值的瞬间）；
                // 无超期记录时该查询命中索引且 0 行变动，不产生写事务、不触发失效通知。
                repo.sweepExpired(now)
            }
        }
    }

    fun complete(id: Long) {
        viewModelScope.launch { repo.complete(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}

@Composable
fun HomeScreen(onAdd: () -> Unit, onSettings: () -> Unit, onEdit: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as HomeworkApp
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(
        key = "home",
        factory = viewModelFactory { initializer { HomeViewModel(app) } },
    )
    val bg = C.Background
    val prim = C.Text
    val sub = C.Sub

    // Android 13 及以上需要通知权限才会登记闹钟，首次进入首页即申请一次；
    // 未授权时 AlarmScheduler 不会排任何提醒，所以授权结果要触发一次重排。
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { AlarmScheduler.refresh(context) }
    LaunchedEffect(Unit) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // 只量一段固定首行；字体解析器/密度/字号比例变化时 measurer 会更新。
    // 不读取滚动正文的动态基线，滚动时不会把两侧标签带走。
    val textMeasurer = rememberTextMeasurer(cacheSize = 1)
    val contentBaselinePx = remember(textMeasurer) {
        textMeasurer.measure("作业", style = ContentLineStyle, maxLines = 1).firstBaseline.roundToInt()
    }

    // 纯文字最多三条 FIFO；含图记录独占展开。
    var expandedIds by remember { mutableStateOf(listOf<Long>()) }
    var viewerPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var viewerIndex by remember { mutableStateOf(0) }
    val imageIds = remember(vm.items) { vm.items.filter { it.imagePaths.isNotEmpty() }.map { it.id }.toSet() }
    val currentImageIds by rememberUpdatedState(imageIds)
    LaunchedEffect(vm.items) {
        val validIds = vm.items.map { it.id }.toSet()
        val validExpanded = expandedIds.filter { it in validIds }
        expandedIds = validExpanded.lastOrNull { it in imageIds }?.let { listOf(it) } ?: validExpanded
    }

    // 全局唯一滑动打开项：任何时刻滑动打开态最多 1 条。
    var openId by remember { mutableStateOf<Long?>(null) }

    // 高度动画已逐帧推挤下方项，窗口内不再对新位置重复插值。
    var expansionGeneration by remember { mutableStateOf(0) }
    var expansionAnimating by remember { mutableStateOf(false) }
    LaunchedEffect(expansionGeneration) {
        if (expansionAnimating) {
            delay(Motion.EXPANSION_GUARD.toLong())
            expansionAnimating = false
        }
    }
    val currentEdit by rememberUpdatedState(onEdit)
    val changeOpen: (Long?) -> Unit = remember { { openId = it } }

    val listState = rememberLazyListState()
    // 列表开始滚动即关闭已打开的滑动项（先收框再归位，由卡片自身播放）
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect {
            if (it && openId != null) openId = null
        }
    }

    // 记录卡片之间的固定间距（Dim.recordGap）。
    val gap = Dim.recordGap

    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(Dim.topBarH).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(AppIcon.ADD, "添加作业", color = prim, onClick = onAdd)
            IconAction(AppIcon.SETTINGS, "设置", color = prim, onClick = onSettings)
        }

        if (vm.items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无作业记录", color = prim, fontSize = 16.sp)
                    Text("点击右上 ＋ 添加", color = sub, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = Dim.marginH, end = Dim.marginH,
                    top = Dim.listTopPad, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(vm.items, key = { it.id }) { item ->
                    // 回调读取同一 State，不因其它卡片的展开而换身份。
                    val toggle: () -> Unit = remember(item.id) {
                        {
                            val wasOpen = openId == item.id
                            openId = null
                            if (!wasOpen) {
                                expansionAnimating = true
                                expansionGeneration++
                                expandedIds = ExpansionPolicy.toggle(expandedIds, item.id, currentImageIds)
                            }
                        }
                    }
                    val edit: () -> Unit = remember(item.id) { { currentEdit(item.id) } }
                    val complete: () -> Unit = remember(item.id) {
                        {
                            expansionAnimating = false
                            openId = null
                            expandedIds = expandedIds - item.id
                            vm.complete(item.id)
                        }
                    }
                    val delete: () -> Unit = remember(item.id) {
                        {
                            expansionAnimating = false
                            openId = null
                            expandedIds = expandedIds - item.id
                            vm.delete(item.id)
                        }
                    }
                    RecordCard(
                        item = item,
                        now = vm.tick,
                        primColor = prim,
                        subColor = sub,
                        contentBaselinePx = contentBaselinePx,
                        expanded = expandedIds.contains(item.id),
                        isOpen = openId == item.id,
                        // 展开推挤：下方记录同帧、同时长（220 ms）、同曲线，无错峰
                        modifier = Modifier.animateItem(
                            placementSpec = if (expansionAnimating) null
                                else tween(Motion.BASE, easing = Motion.Standard),
                            fadeOutSpec = tween(Motion.MEDIUM, easing = Motion.Exit),
                        ),
                        onToggle = toggle,
                        onEdit = edit,
                        onOpenChange = changeOpen,
                        onComplete = complete,
                        onDelete = delete,
                        onOpenImage = { index ->
                            viewerPaths = item.imagePaths
                            viewerIndex = index
                        },
                    )
                }
            }
        }
    }
    if (viewerPaths.isNotEmpty()) AttachmentViewer(viewerPaths, viewerIndex) { viewerPaths = emptyList() }
}

/** 记录卡片：卡片和两侧时间区保持固定，仅主体内容压缩露出确认按钮。 */
@Composable
fun RecordCard(
    item: HomeItem,
    now: Long,
    primColor: Color,
    subColor: Color,
    contentBaselinePx: Int,
    expanded: Boolean,
    isOpen: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onOpenChange: (Long?) -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onOpenImage: (Int) -> Unit = {},
) {
    val density = LocalDensity.current
    // shift（dp）：主体内容水平位移。左滑为负（露出右侧 ✓），右滑为正（露出左侧 ×）。
    // 拖动期直接写值，1:1 跟手；仅吸附/归位走 animate()。
    var shift by remember { mutableFloatStateOf(0f) }
    var btnAlpha by remember { mutableFloatStateOf(0f) }
    var btnEnter by remember { mutableFloatStateOf(0f) }
    // 操作框完全就位（alpha == 1）后才注册命中区，避免误触。
    val btnReady by remember { derivedStateOf { btnAlpha >= 0.999f } }

    val bg = C.Surface
    val rem = remember(now, item.deadline) { TimeFmt.remaining(now, item.deadline) }
    val zone = java.time.ZoneId.systemDefault()
    val deadlineText = remember(item.deadline, zone) { TimeFmt.fmtDeadline(item.deadline) }
    val remColor = when (rem.kind) {
        2 -> C.Overdue
        1 -> C.Urgent
        3 -> C.Later
        else -> primColor
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 按压反馈：叠一层 4% 白色遮罩，90 ms，无曲线位移。
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(Motion.MICRO),
        label = "press",
    )

    // 越界阻尼：超出 maxOffset 后位移 ×0.25（硬上限 ≈78 dp）
    val maxOffset = Dim.maxOffset.value
    val dragState = rememberDraggableState { deltaPx ->
        if (!expanded) {
            val deltaDp = deltaPx / density.density
            val raw = shift + deltaDp
            shift = when {
                raw > maxOffset -> maxOffset + (raw - maxOffset) * 0.25f
                raw < -maxOffset -> -maxOffset + (raw + maxOffset) * 0.25f
                else -> raw
            }
        }
    }

    // 本条不再持有打开权（他条接管 / 点别处 / 滚动）：先收框 90 ms，再归位边界
    LaunchedEffect(isOpen) {
        if (!isOpen && (shift != 0f || btnAlpha > 0f)) {
            animate(btnAlpha, 0f, animationSpec = tween(Motion.MICRO)) { v, _ -> btnAlpha = v }
            btnEnter = 0f
            animate(shift, 0f, animationSpec = tween(Motion.MEDIUM, easing = Motion.Emphasized)) { v, _ ->
                shift = v
            }
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dim.radiusOuter))
            .background(bg)
            .drawBehind { drawRect(primColor.copy(alpha = 0.04f * pressAlpha)) }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onToggle,
            )
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = !expanded,
                onDragStarted = {
                    // 越过 touchSlop、判定为横向的同一帧即进入跟手态，同时收掉旧框
                    onOpenChange(item.id)
                    animate(btnAlpha, 0f, animationSpec = tween(Motion.MICRO)) { v, _ -> btnAlpha = v }
                },
                onDragStopped = { velocity ->
                    val vDp = velocity / density.density
                    val dir = if (abs(shift) > 0.5f) sign(shift) else sign(vDp)
                    val shouldOpen = abs(shift) > Dim.openThreshold.value || abs(vDp) > 300f
                    if (shouldOpen && dir != 0f) {
                        // 吸附到既定位置（160 ms / ease.standard）
                        animate(
                            shift, dir * maxOffset,
                            animationSpec = tween(Motion.FAST, easing = Motion.Standard),
                        ) { v, _ -> shift = v }
                        // 出框：第一帧不得早于吸附动画的最后一帧
                        btnAlpha = 0f
                        btnEnter = Dim.btnEnterShift.value
                        coroutineScope {
                            launch {
                                animate(
                                    btnEnter, 0f,
                                    animationSpec = tween(Motion.MICRO + Motion.FAST, easing = Motion.Standard),
                                ) { v, _ -> btnEnter = v }
                            }
                            animate(btnAlpha, 0.25f, animationSpec = tween(Motion.MICRO)) { v, _ -> btnAlpha = v }
                            animate(
                                btnAlpha, 1f,
                                animationSpec = tween(Motion.FAST, easing = Motion.Standard),
                            ) { v, _ -> btnAlpha = v }
                        }
                    } else {
                        // 未达阈值：回弹归位，操作框全程不出现
                        btnAlpha = 0f
                        animate(
                            shift, 0f,
                            animationSpec = tween(Motion.FAST, easing = Motion.Standard),
                        ) { v, _ -> shift = v }
                        onOpenChange(null)
                    }
                },
            )
            .clipToBounds()
            .animateContentSize(
                // 展开/收起主体高度：220 ms + ease.standard
                animationSpec = tween(Motion.BASE, easing = Motion.Standard),
            )
    ) {
        // 双时间区在卡片内部；滑动时其宽度、位置不随 shift 改变。
        Row(
            Modifier.fillMaxWidth().heightIn(min = Dim.tabH)
                .padding(start = Dim.tabPadH, end = Dim.tabPadH, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val value = rem.text.removePrefix("已逾期 ").replace(" ", "")
            Column(Modifier.weight(1.2f).padding(end = 10.dp)) {
                Text(if (rem.kind == 2) "已逾期" else "剩余时间", style = TabLabelStyle, color = subColor)
                Text(value, style = TabValueStyle.copy(
                    fontSize = if (value.length > 11) 12.sp else Dim.tabValueSp,
                    fontWeight = FontWeight.Medium,
                ), color = remColor, maxLines = 2)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("截止时间", style = TabLabelStyle, color = subColor)
                Text(deadlineText,
                    style = trimmedTextStyle(13.sp, 20.sp), color = subColor, maxLines = 1)
            }
        }
        // ---- 主体：三列（数字 → 内容 → 学科）+ 操作框。
        // 操作框固定在主体内垂直居中：y = bodyTop + (bodyHeight − btnSize) / 2。
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dim.bodyMinH)
                    .padding(
                        start = Dim.padH + shift.coerceAtLeast(0f).dp,
                        end = Dim.padH + (-shift).coerceAtLeast(0f).dp,
                        // 两态首行锚点不变，不在展开首帧切换内边距。
                        top = Dim.bodyPadV,
                        bottom = Dim.bodyPadV,
                    ),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    item.count.toString(),
                    Modifier.width(Dim.countW).alignByBaseline(),
                    color = primColor,
                    // 位数 ≥ 3 时降至 16 sp
                    style = if (item.count >= 100) LargeCountLineStyle else CountLineStyle,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(Dim.colGap))
                // 保持同一个内容容器及 TextStyle，只改变摘要行数限制。
                Column(Modifier.weight(1f).alignBy { contentBaselinePx }
                    // 图片移至底行，仍计入原352dp内容预算，长文内部滚动。
                    .heightIn(max = if (expanded && item.imagePaths.isNotEmpty()) {
                        Dim.maxExpandedHeight - 48.dp
                    } else Dim.maxExpandedHeight)
                    .verticalScroll(rememberScrollState())) {
                    Text(
                        item.content.ifBlank { if (item.imagePaths.isNotEmpty()) "图片作业" else "" },
                        color = primColor,
                        style = ContentLineStyle,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    )
                }
                if (!expanded && item.imagePaths.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    AttachmentMarker(Modifier.alignBy { it.measuredHeight })
                }
                Spacer(Modifier.width(Dim.colGap))
                Text(
                    item.subjectName,
                    Modifier.widthIn(max = 112.dp).alignByBaseline(),
                    color = subjectColor(item.colorArgb),
                    // 科目完整显示：名称越长字号越小（13 → 12 → 11 sp），`…` 只允许出现在内容列
                    style = trimmedTextStyle(subjectFontSp(item.subjectName), 22.sp).copy(
                        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, letterSpacing = 0.sp,
                    ),
                    textAlign = TextAlign.End,
                    // 极长科目自然换行，保持完整显示，内容列优先让宽。
                    overflow = TextOverflow.Clip,
                )
            }
            // ---- 操作框：固定于主体内两侧，垂直居中于主体；出框动画结束后才可点
            if (shift < -0.5f) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = Dim.btnInset)
                        .size(Dim.btnSize)
                        .graphicsLayer {
                            alpha = btnAlpha
                            translationX = btnEnter.dp.toPx()
                        }
                        .background(C.Green, RoundedCornerShape(10.dp))
                        .semantics { contentDescription = "完成作业" }
                        .then(if (btnReady) Modifier.clickable(onClick = onComplete) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { ActionMark(icon = ActionIcon.CHECK, color = Color.White) }
            }
            if (shift > 0.5f) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = Dim.btnInset)
                        .size(Dim.btnSize)
                        .graphicsLayer {
                            alpha = btnAlpha
                            translationX = -btnEnter.dp.toPx()
                        }
                        .background(C.Red, RoundedCornerShape(10.dp))
                        .semantics { contentDescription = "删除作业" }
                        .then(if (btnReady) Modifier.clickable(onClick = onDelete) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { ActionMark(icon = ActionIcon.CROSS, color = Color.White) }
            }
        }
        // 图片与编辑合用底部一行；短正文不再为图片、编辑分别留行。
        if (expanded && item.imagePaths.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(
                    start = Dim.padH + Dim.countW + Dim.colGap,
                    end = Dim.padH,
                    top = 0.dp,
                    bottom = Dim.editBottomPad,
                ),
                verticalAlignment = Alignment.Bottom,
            ) {
                ThumbnailStrip(item.imagePaths, Modifier.weight(1f).padding(end = 8.dp), onOpenImage)
                Text(
                    "编辑",
                    style = EditLineStyle,
                    color = subColor,
                    modifier = Modifier.clickable(onClick = onEdit),
                )
            }
        } else if (expanded) {
            // 纯文字记录沿用原编辑带的间距和位置。
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Dim.padH,
                        end = Dim.padH,
                        top = Dim.editGap,
                        bottom = Dim.editBottomPad,
                    ),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "编辑",
                    style = EditLineStyle,
                    color = subColor,
                    modifier = Modifier.clickable(onClick = onEdit),
                )
            }
        }
    }
}

/** 科目优先保证完整，超长名称缩小字号后自然换行。 */
private fun subjectFontSp(name: String) = when {
    name.length <= 8 -> 13.sp
    name.length <= 10 -> 12.sp
    else -> 11.sp
}

/** ✓ / × 操作框内的图形种类。 */
private enum class ActionIcon { CHECK, CROSS }

/**
 * 操作框图标：矢量描边，1.8 dp、圆端圆拐，两个图标共用同一位置与 20 dp 占位。
 * 不使用字体字形（✓ / ×），避免线宽与端头随系统字体变化。
 * 框尺寸（42 dp）、圆角（6 dp）、位置与点击行为保持不变。
 */
@Composable
private fun ActionMark(icon: ActionIcon, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        val sw = 1.8.dp.toPx()
        val cap = StrokeCap.Round
        if (icon == ActionIcon.CHECK) {
            val p = Path().apply {
                moveTo(s * 0.24f, s * 0.53f)
                lineTo(s * 0.42f, s * 0.71f)
                lineTo(s * 0.77f, s * 0.30f)
            }
            drawPath(p, color, style = Stroke(width = sw, cap = cap, join = StrokeJoin.Round))
        } else {
            drawLine(color, Offset(s * 0.30f, s * 0.30f), Offset(s * 0.70f, s * 0.70f), sw, cap)
            drawLine(color, Offset(s * 0.70f, s * 0.30f), Offset(s * 0.30f, s * 0.70f), sw, cap)
        }
    }
}
