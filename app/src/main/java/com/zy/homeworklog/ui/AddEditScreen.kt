package com.zy.homeworklog.ui

import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zy.homeworklog.HomeworkApp
import com.zy.homeworklog.data.DraftStore
import com.zy.homeworklog.data.Subject
import com.zy.homeworklog.reminder.AlarmScheduler
import com.zy.homeworklog.util.TimeFmt
import com.zy.homeworklog.util.Weekdays
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 作业类型只区分纸质 / 电子
val TYPE_OPTIONS = listOf("纸质", "电子")

class AddEditViewModel(app: android.app.Application, val recordId: Long) : AndroidViewModel(app) {
    private val homeworkApp = app as HomeworkApp
    private val repo = homeworkApp.repo

    /** 添加流程草稿：半填关闭后保留，超过 24 小时作废。 */
    private val draft = DraftStore(app)

    var subjects by mutableStateOf<List<Subject>>(emptyList())
        private set
    var subjectId by mutableStateOf<Long?>(null)
    var typeName by mutableStateOf<String?>(null)
    var content by mutableStateOf("")
    var deadline by mutableStateOf(TimeFmt.defaultDeadline())
    var isDefaultDeadline by mutableStateOf(true)
    var ruleId by mutableStateOf<Long?>(null)
    var ruleSummary by mutableStateOf("不重复")

    /** 单条覆盖的提醒时刻（绝对毫秒）；null = 跟随全局提前量。 */
    var remindAt by mutableStateOf<Long?>(null)
    var imagePaths by mutableStateOf<List<String>>(emptyList())
        private set

    var importing by mutableStateOf(false)
        private set

    var recordLoading by mutableStateOf(recordId > 0)
        private set

    private val importedImagePaths = linkedSetOf<String>()

    init {
        viewModelScope.launch { repo.subjects.collect { subjects = it } }
        if (recordId > 0) {
            viewModelScope.launch {
                try {
                    val r = repo.record(recordId) ?: return@launch
                    subjectId = r.subjectId
                    typeName = r.typeName.ifEmpty { null }
                    content = r.content
                    deadline = r.deadline
                    isDefaultDeadline = false
                    ruleId = r.recurringRuleId
                    remindAt = r.remindAt
                    imagePaths = repo.imagesFor(recordId).sortedBy { it.ord }.map { it.path }
                    ruleId?.let { rid ->
                        ruleSummary = repo.rule(rid)?.let { Weekdays.text(it.weekdays) } ?: "不重复"
                    }
                } finally {
                    recordLoading = false
                }
            }
        } else {
            // 添加流程：恢复 24 小时内的草稿（过期草稿由 DraftStore 自行清空 → 纯净状态）
            draft.load()?.let { d ->
                subjectId = d.subjectId
                typeName = d.typeName
                content = d.content
                deadline = d.deadline
                isDefaultDeadline = d.isDefaultDeadline
                ruleId = d.ruleId
                remindAt = d.remindAt
                imagePaths = d.imagePaths
                d.ruleId?.let { rid ->
                    viewModelScope.launch {
                        ruleSummary = repo.rule(rid)?.let { Weekdays.text(it.weekdays) } ?: "不重复"
                    }
                }
            }
        }
    }

    /** 把当前表单落成草稿（仅添加流程；编辑流程不写）。 */
    fun persistDraft() {
        if (recordId > 0 || saved) return
        draft.save(snapshot())
    }

    private fun snapshot() = DraftStore.Draft(
        subjectId = subjectId,
        typeName = typeName,
        content = content,
        deadline = deadline,
        isDefaultDeadline = isDefaultDeadline,
        ruleId = ruleId,
        remindAt = remindAt,
        imagePaths = imagePaths,
    )

    fun importImages(uris: List<Uri>, onError: (String) -> Unit) {
        if (uris.isEmpty() || saving || saved || importing) return
        val remaining = 9 - imagePaths.size
        if (remaining <= 0) return onError("最多添加9张图片")
        val selected = uris.take(remaining)
        val wasTruncated = uris.size > remaining
        importing = true
        viewModelScope.launch {
            var failedCount = 0
            try {
                selected.forEach { uri ->
                    val importedPath = withContext(Dispatchers.IO) {
                        val cameraFile = cameraFileFor(uri)
                        try {
                            repo.importImage(uri)
                        } finally {
                            cameraFile?.delete()
                        }
                    }
                    if (importedPath == null) {
                        failedCount++
                    } else {
                        importedImagePaths += importedPath
                        imagePaths = imagePaths + importedPath
                        persistDraft()
                    }
                }
                when {
                    failedCount > 0 && wasTruncated -> onError("部分图片导入失败，且最多保留9张")
                    failedCount > 0 -> onError("部分图片导入失败，请重试")
                    wasTruncated -> onError("最多保留9张图片，超出部分未导入")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onError("图片导入失败，请重试")
            } finally {
                importing = false
            }
        }
    }

    private fun cameraFileFor(uri: Uri): File? {
        if (uri.scheme != "content" || uri.authority != "${homeworkApp.packageName}.fileprovider") return null
        if (uri.pathSegments.firstOrNull() != "camera" || uri.pathSegments.size < 2) return null
        val cameraDir = File(homeworkApp.cacheDir, "camera").canonicalFile
        return runCatching {
            File(cameraDir, uri.pathSegments.drop(1).joinToString(File.separator))
                .canonicalFile.takeIf { it.parentFile == cameraDir }
        }.getOrNull()
    }

    fun removeImage(path: String) {
        if (saving || importing) return
        if (path !in imagePaths) return
        imagePaths = imagePaths.filterNot { it == path }
        persistDraft()
    }

    fun chooseSubject(id: Long) {
        if (subjectId != id) setRule(null)
        subjectId = id
    }

    fun createSubjectWithSwap(
        name: String,
        color: Long,
        conflict: Subject?,
        swap: Long,
        onError: (String) -> Unit,
        onCreated: (Long) -> Unit,
    ) {
        if (creatingSubject) return
        creatingSubject = true
        viewModelScope.launch {
            try {
                onCreated(repo.saveSubjectWithSwap(null, name, color, conflict, swap))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(if (e is IllegalArgumentException) e.message ?: "科目创建失败" else "科目创建失败，请重试")
            } finally {
                creatingSubject = false
            }
        }
    }

    private var creatingSubject = false
    private var creatingRule = false

    fun createRule(days: String, onError: (String) -> Unit, onDone: () -> Unit) {
        val sid = subjectId ?: return onError("先选科目")
        if (creatingRule) return
        creatingRule = true
        viewModelScope.launch {
            try {
                val rid = repo.addRule(sid, days)
                if (subjectId == sid) setRule(rid)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(if (e is IllegalArgumentException) e.message ?: "周期创建失败" else "周期创建失败，请重试")
            } finally {
                creatingRule = false
            }
        }
    }

    fun setRule(rid: Long?) {
        ruleId = rid
        viewModelScope.launch {
            val summary = if (rid == null) "不重复"
            else repo.rule(rid)?.let { Weekdays.text(it.weekdays) } ?: "不重复"
            if (ruleId == rid) ruleSummary = summary
        }
    }

    var saving by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set

    /** 科目必选；文字可空，图片作为可选附件。 */
    fun save(onError: (String) -> Unit, onDone: () -> Unit) {
        if (saving || saved) return
        if (recordLoading) {
            onError("作业资料加载中，请稍后保存")
            return
        }
        if (importing) {
            onError("图片导入中，请稍后保存")
            return
        }
        val sid = subjectId
        if (sid == null) {
            onError("科目必选")
            return
        }
        val form = snapshot()
        saving = true
        viewModelScope.launch {
            try {
                if (repo.subject(sid) == null) {
                    subjectId = null
                    setRule(null)
                    onError("原科目已删除，内容已保留，请重新选择科目")
                    return@launch
                }
                if (recordId > 0) {
                    repo.updateRecord(
                        recordId, sid, form.typeName ?: "", form.content, form.deadline, form.ruleId,
                        imagePaths = form.imagePaths,
                        remindAt = form.remindAt,
                    )
                } else {
                    repo.addRecord(sid, form.typeName ?: "", form.content, form.deadline, form.ruleId,
                        form.imagePaths, remindAt = form.remindAt)
                    // 入库成功 → 草稿作废，下次打开添加页为纯净状态
                    draft.clearIfMatches(form)
                }
                saved = true
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(if (e is IllegalArgumentException || e is IllegalStateException) {
                    e.message ?: "保存失败，内容已保留，请重试"
                } else "保存失败，内容已保留，请重试")
            } finally {
                saving = false
            }
        }
    }

    override fun onCleared() {
        val candidates = importedImagePaths.toList()
        super.onCleared()
        if (candidates.isNotEmpty()) {
            homeworkApp.appScope.launch { repo.discardUnreferencedImages(candidates) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(recordId: Long, onReminders: () -> Unit, onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as HomeworkApp
    val vm: AddEditViewModel = viewModel(
        key = "addedit-$recordId",
        factory = viewModelFactory { initializer { AddEditViewModel(app, recordId) } },
    )
    val ctx = LocalContext.current
    val isEdit = recordId > 0
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val latestOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(vm.saved, lifecycleState) {
        if (vm.saved && lifecycleState == Lifecycle.State.RESUMED) latestOnDone()
    }
    BackHandler(enabled = vm.saving || vm.importing) {
        Toast.makeText(ctx, if (vm.importing) "图片导入中，请稍后" else "正在保存，请稍后", Toast.LENGTH_SHORT).show()
    }

    var showSubject by remember { mutableStateOf(false) }
    var newSubjectName by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var tmpDate by remember { mutableStateOf<Long?>(null) }
    var tmpHour by remember { mutableIntStateOf(0) }
    var tmpMinute by remember { mutableIntStateOf(0) }
    var showRule by remember { mutableStateOf(false) }
    var newRuleDays by remember { mutableStateOf(setOf<String>()) }
    var showRemindSheet by remember { mutableStateOf(false) }
    var pickTarget by remember { mutableStateOf(PickTarget.DEADLINE) }

    val subjectName = vm.subjects.firstOrNull { it.id == vm.subjectId }?.name

    // 全局提前量（只读展示用；修改在「提醒设置」页）
    var globalLead by remember(ctx) { mutableLongStateOf(
        ctx.getSharedPreferences("prefs", 0)
            .getLong(AlarmScheduler.PREF_LEAD_MS, AlarmScheduler.DEFAULT_LEAD_MS)
    ) }
    DisposableEffect(ctx) {
        val prefs = ctx.getSharedPreferences("prefs", 0)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == AlarmScheduler.PREF_LEAD_MS) {
                globalLead = prefs.getLong(AlarmScheduler.PREF_LEAD_MS, AlarmScheduler.DEFAULT_LEAD_MS)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val remindLabel = vm.remindAt?.let { TimeFmt.fmtDeadline(it) } ?: "跟随全局"

    // 添加流程草稿自动落盘：任一字段变化即写草稿；
    // 跳过首帧，避免「只是打开添加页未编辑」就把 24h 计时刷新掉。
    var draftPrimed by remember { mutableStateOf(false) }
    LaunchedEffect(
        vm.subjectId, vm.typeName, vm.content, vm.deadline, vm.isDefaultDeadline, vm.ruleId, vm.remindAt,
        vm.imagePaths,
    ) {
        if (draftPrimed) vm.persistDraft() else draftPrimed = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Background)
            .imePadding()
    ) {
        PageTopBar(
            if (isEdit) "编辑作业" else "添加作业",
            onBack = { if (!vm.saving && !vm.importing) onDone() },
            confirmLabel = when {
                vm.recordLoading -> "加载中"
                vm.saving -> "保存中"
                vm.importing -> "导入中"
                else -> "保存"
            },
            // 顶栏右侧文字「保存」，底部不另设重复按钮。
            onConfirm = {
                vm.save(
                    onError = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() },
                    onDone = onDone,
                )
            },
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        CardGroup {
        FormRow(
            "科目",
            subjectName ?: "必选 · 点击选择",
            valueColor = if (subjectName != null) {
                subjectColor(vm.subjects.firstOrNull { it.id == vm.subjectId }?.colorArgb ?: 0xFFFFFFFF)
            } else C.Sub,
            onClick = { if (!vm.saving) showSubject = true },
        )
        CardDivider()
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("类型", color = C.Sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Row(Modifier.weight(1.7f).clip(RoundedCornerShape(10.dp)).background(C.SurfaceHigh)) {
                TYPE_OPTIONS.forEach { option ->
                    val selected = vm.typeName == option
                    Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) C.Text else Color.Transparent)
                        .clickable(enabled = !vm.saving) { vm.typeName = option }, contentAlignment = Alignment.Center) {
                        Text(option, color = if (selected) C.Background else C.Sub, fontSize = 14.sp)
                    }
                }
            }
        }
        }

        CardGroup {
        Text("作业内容", color = C.Sub, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
        TextField(
            value = vm.content,
            enabled = !vm.saving,
            onValueChange = { vm.content = it },
            placeholder = { Text("输入作业内容…", color = C.Sub) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp),
            textStyle = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = C.Text,
                unfocusedTextColor = C.Text,
                cursorColor = C.Text,
            ),
        )
        }

        CardGroup {
            AttachmentEditor(
                paths = vm.imagePaths,
                enabled = !vm.saving && !vm.importing && !vm.recordLoading,
                onAddUris = { uris ->
                    vm.importImages(uris) { msg ->
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onRemove = vm::removeImage,
            )
        }

        CardGroup {
        FormRow(
            "截止时间",
            TimeFmt.fmtDeadline(vm.deadline),
            tag = if (vm.isDefaultDeadline) "默认" else null,
        ) {
            if (vm.saving) return@FormRow
            pickTarget = PickTarget.DEADLINE
            val c = java.util.Calendar.getInstance().apply { timeInMillis = vm.deadline }
            tmpHour = c.get(java.util.Calendar.HOUR_OF_DAY)
            tmpMinute = c.get(java.util.Calendar.MINUTE)
            showDate = true
        }
        CardDivider()

        // 提醒时间支持「跟随全局」或「单条自定义」，最终都落成一个绝对时间点
        FormRow(
            "提醒时间",
            remindLabel,
            valueColor = if (vm.remindAt == null) C.Sub else C.Text,
            supportingText = if (vm.remindAt == null) "提前 ${TimeFmt.fmtLead(globalLead)}提醒" else "自定义时刻",
        ) { if (!vm.saving) showRemindSheet = true }
        }

        CardGroup {
            FormRow("周期任务", vm.ruleSummary) { if (!vm.saving) showRule = true }
            CardDivider()
            FormRow("全局提醒", "提醒设置") { if (!vm.saving) onReminders() }
        }

        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
        }

        // ---- 科目选择弹窗（列表 + 内联新建：名字 + 10 色板 + 冲突换色）
        if (showSubject) {
            var creating by remember { mutableStateOf(false) }
            var newColor by remember { mutableStateOf(PALETTE.first()) }
            val createConflict = vm.subjects.firstOrNull { it.colorArgb == newColor }
            val createSwap = remember(vm.subjects, newColor, createConflict?.id) {
                swapColorFor(vm.subjects, createConflict?.id ?: -1L, newColor)
            }
            AppDialog(
                onDismissRequest = { showSubject = false; creating = false; newSubjectName = "" },
                title = { Text(if (creating) "新建科目" else "选择科目", color = C.Text) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (creating) {
                            OutlinedTextField(
                                value = newSubjectName,
                                onValueChange = { newSubjectName = it },
                                placeholder = { Text("科目名", color = C.Sub) },
                            )
                            Spacer(Modifier.height(8.dp))
                            PaletteGrid(selected = newColor, onPick = { newColor = it })
                            SwapHint(subjects = vm.subjects, excludeId = null, color = newColor, swap = createSwap)
                        } else {
                            if (vm.subjects.isEmpty()) Text("（暂无科目）", color = C.Sub)
                            vm.subjects.forEach { s ->
                                Text(
                                    s.name,
                                    color = subjectColor(s.colorArgb),
                                    fontSize = 16.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.chooseSubject(s.id)
                                            showSubject = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    DialogAction(if (creating) "创建并使用" else "＋ 新建科目", onClick = {
                            if (!creating) {
                                newColor = PALETTE.firstOrNull { p -> vm.subjects.none { it.colorArgb == p } }
                                    ?: PALETTE.first()
                                creating = true
                            } else if (newSubjectName.isNotBlank()) {
                                vm.createSubjectWithSwap(
                                    newSubjectName.trim(), newColor, createConflict,
                                    createSwap,
                                    onError = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() },
                                ) { id ->
                                    vm.chooseSubject(id)
                                    newSubjectName = ""
                                    creating = false
                                    showSubject = false
                                }
                            }
                        },
                    )
                },
                dismissButton = {
                    DialogAction(if (creating) "返回列表" else "取消", secondary = true, onClick = {
                            if (creating) creating = false else showSubject = false
                        },
                    )
                },
                containerColor = C.Surface,
            )
        }

        // ---- 提醒时间：跟随全局 / 单条自定义
        if (showRemindSheet) {
            AppDialog(
                onDismissRequest = { showRemindSheet = false },
                title = { Text("提醒时间", color = C.Text) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "跟随全局（提前 ${TimeFmt.fmtLead(globalLead)}）",
                            color = if (vm.remindAt == null) C.Blue else C.Text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.remindAt = null; showRemindSheet = false }
                                .padding(vertical = 10.dp),
                        )
                        Text(
                            "自定义时间…",
                            color = if (vm.remindAt != null) C.Blue else C.Text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pickTarget = PickTarget.REMIND
                                    val seed = vm.remindAt ?: (vm.deadline - globalLead)
                                    val c = java.util.Calendar.getInstance().apply { timeInMillis = seed }
                                    tmpHour = c.get(java.util.Calendar.HOUR_OF_DAY)
                                    tmpMinute = c.get(java.util.Calendar.MINUTE)
                                    showRemindSheet = false
                                    showDate = true
                                }
                                .padding(vertical = 10.dp),
                        )
                        Text("自定义提醒是固定时刻，不会随截止时间变化。", color = C.Sub, fontSize = 11.sp)
                    }
                },
                confirmButton = {},
                dismissButton = {
                    DialogAction("取消", secondary = true, onClick = { showRemindSheet = false },
                    )
                },
                containerColor = C.Surface,
            )
        }

        // ---- 截止时间：日期（仅下半部分日历）→ 点日期自动进时间（可上一步返回）
        // 日期弹窗只留日历，点日期即进入时间选择。
        if (showDate) {
            val st = rememberDatePickerState()
            LaunchedEffect(st.selectedDateMillis) {
                val sel = st.selectedDateMillis
                if (sel != null) {
                    tmpDate = sel
                    showDate = false
                    showTime = true
                }
            }
            DatePickerDialog(
                onDismissRequest = { showDate = false },
                confirmButton = {},
                dismissButton = { DialogAction("取消", secondary = true, onClick = { showDate = false }) },
                shape = RoundedCornerShape(24.dp),
                colors = DatePickerDefaults.colors(containerColor = C.Surface),
                tonalElevation = 0.dp,
            ) {
                DatePicker(state = st, title = null, headline = null, showModeToggle = false,
                    colors = DatePickerDefaults.colors(
                        containerColor = C.Surface, weekdayContentColor = C.Sub,
                        navigationContentColor = C.Text, dayContentColor = C.Text,
                        selectedDayContainerColor = C.Text, selectedDayContentColor = C.Background,
                        todayContentColor = C.Blue, todayDateBorderColor = C.Blue,
                        yearContentColor = C.Text, currentYearContentColor = C.Blue,
                        selectedYearContainerColor = C.Text, selectedYearContentColor = C.Background,
                    ))
            }
        }
        // ---- 时间：滚轮选择器（保留 上一步 / 取消 / 确定）
        if (showTime) {
            var hourBusy by remember { mutableStateOf(false) }
            var minuteBusy by remember { mutableStateOf(false) }
            PickerDialog(
                title = if (pickTarget == PickTarget.REMIND) "选择提醒时间" else "选择时间",
                subtitle = tmpDate?.let { TimeFmt.fmtPickerDate(it) }
                    ?: TimeFmt.fmtCnDate(if (pickTarget == PickTarget.REMIND) (vm.remindAt ?: vm.deadline) else vm.deadline),
                onDismissRequest = { showTime = false },
                actions = {
                    PillButton("上一步", secondary = true, modifier = Modifier.weight(1f)) {
                        showTime = false; showDate = true
                    }
                    PillButton("取消", secondary = true, modifier = Modifier.weight(1f)) {
                        showTime = false
                    }
                    PillButton("确定", secondary = false, modifier = Modifier.weight(1.3f),
                        enabled = !hourBusy && !minuteBusy) {
                        if (hourBusy || minuteBusy) return@PillButton
                        val date = tmpDate ?: return@PillButton
                        val ts = TimeFmt.pickerDateTime(date, tmpHour, tmpMinute)
                        if (pickTarget == PickTarget.REMIND) {
                            vm.remindAt = ts
                        } else {
                            vm.deadline = ts
                            vm.isDefaultDeadline = false
                        }
                        showTime = false
                    }
                },
            ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("时", color = C.Sub, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            WheelColumn((0..23).toList(), tmpHour, onSelected = { tmpHour = it },
                                onScrollingChanged = { hourBusy = it })
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("分", color = C.Sub, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            WheelColumn((0..59).toList(), tmpMinute, onSelected = { tmpMinute = it },
                                onScrollingChanged = { minuteBusy = it })
                        }
                    }

            }
        }

        // ---- 重复：选择星期 → 建周期规则
        if (showRule) {
            AppDialog(
                onDismissRequest = { showRule = false },
                title = { Text("周期任务", color = C.Text) },
                text = {
                    Column {
                        Text("不重复", color = if (newRuleDays.isEmpty()) C.Blue else C.Text,
                            modifier = Modifier.fillMaxWidth().clickable {
                                newRuleDays = setOf(); vm.setRule(null); showRule = false
                            }.padding(vertical = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Weekdays.ORDER.forEach { d ->
                                val sel = d in newRuleDays
                                Text(
                                    Weekdays.NAMES[d] ?: d,
                                    color = if (sel) C.OnBlue else C.Text,
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f).clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) C.Blue else C.SurfaceHigh)
                                        .clickable {
                                            newRuleDays = if (sel) newRuleDays - d else newRuleDays + d
                                        }
                                        .padding(vertical = 12.dp),
                                )
                            }
                        }
                        Text("每周按所选星期生成一条周期作业", color = C.Sub, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                },
                confirmButton = {
                    DialogAction("创建规则", onClick = {
                        val sid = vm.subjectId
                        if (sid == null) {
                            Toast.makeText(ctx, "先选科目", Toast.LENGTH_SHORT).show()
                        } else if (newRuleDays.isEmpty()) {
                            vm.setRule(null); showRule = false
                        } else {
                            vm.createRule(newRuleDays.joinToString(","),
                                onError = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() },
                                onDone = { showRule = false })
                        }
                    })
                },
                dismissButton = { DialogAction("取消", secondary = true, onClick = { showRule = false }) },
                containerColor = C.Surface,
            )
        }
    }
}

// 滚轮列（WheelColumn）与胶囊按钮（PillButton）已抽到 ui/Wheel.kt，供「截止时间」与「提前提醒」共用。

/** 日期 → 时间 这套滚轮当前为哪个字段取值（截止时间与提醒时间共用同一套选择器）。 */
private enum class PickTarget { DEADLINE, REMIND }
