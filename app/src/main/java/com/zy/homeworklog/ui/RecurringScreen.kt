package com.zy.homeworklog.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zy.homeworklog.HomeworkApp
import com.zy.homeworklog.data.RecurringRule
import com.zy.homeworklog.data.Subject
import com.zy.homeworklog.util.Weekdays
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class RuleRow(val rule: RecurringRule, val subjectName: String, val subjectColor: Long)

class RecurringViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = (app as HomeworkApp).repo
    var rows by mutableStateOf<List<RuleRow>>(emptyList())
        private set
    var subjects by mutableStateOf<List<Subject>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            combine(repo.allRules(), repo.subjects) { rules, subs ->
                val smap = subs.associateBy { it.id }
                rules.map { r ->
                    RuleRow(r, smap[r.subjectId]?.name ?: "?", smap[r.subjectId]?.colorArgb ?: 0xFF9E9E9E)
                }
            }.collect { rows = it }
        }
        viewModelScope.launch { repo.subjects.collect { subjects = it } }
    }

    private var adding = false

    fun add(subjectId: Long, weekdays: String, onError: (String) -> Unit, onDone: () -> Unit) {
        if (adding) return
        adding = true
        viewModelScope.launch {
            try {
                repo.addRule(subjectId, weekdays)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(if (e is IllegalArgumentException) e.message ?: "周期创建失败" else "周期创建失败，请重试")
            } finally {
                adding = false
            }
        }
    }

    fun toggle(r: RecurringRule, onError: (String) -> Unit) {
        write(onError) { repo.updateRule(r.copy(enabled = !r.enabled)) }
    }

    fun delete(r: RecurringRule, onError: (String) -> Unit) {
        write(onError) { repo.deleteRule(r) }
    }

    private fun write(onError: (String) -> Unit, action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { onError("周期操作失败，请重试") }
        }
    }

}

@Composable
fun RecurringScreen(subjectId: Long, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HomeworkApp
    val vm: RecurringViewModel = viewModel(factory = viewModelFactory { initializer { RecurringViewModel(app) } })
    val ctx = LocalContext.current
    val onError: (String) -> Unit = { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    var showAdd by remember { mutableStateOf(false) }
    var pickedSubject by remember { mutableStateOf<Long?>(if (subjectId > 0) subjectId else null) }
    var pickedDays by remember { mutableStateOf(setOf<String>()) }

    Column(Modifier.fillMaxSize().background(C.Background)) {
        PageTopBar("周期任务", onBack = onBack)
        Text(
            "每周按所选星期生成一条周期作业，截止时间为所选日期当天 24:00。",
            color = C.Sub,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(vm.rows, key = { it.rule.id }) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.Surface)
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 科目优先：独占剩余宽度，放不下时紧凑地折成两行（行距收窄），保证完整可读
                    Text(
                        row.subjectName,
                        color = subjectColor(row.subjectColor),
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Weekdays.text(row.rule.weekdays),
                        color = C.Text,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Switch(
                        checked = row.rule.enabled,
                        onCheckedChange = { vm.toggle(row.rule, onError) },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.Green),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    Text(
                        "删除",
                        color = C.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 10.dp).clickable { vm.delete(row.rule, onError) },
                    )
                }
            }
        }
        Button(
            onClick = {
                pickedSubject = if (subjectId > 0) subjectId else vm.subjects.firstOrNull()?.id
                pickedDays = setOf()
                showAdd = true
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.Surface, contentColor = C.Text),
            shape = RoundedCornerShape(10.dp),
        ) { Text("添加周期规则") }
        Spacer(Modifier.navigationBarsPadding())
    }

    if (showAdd) {
        AppDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加周期规则", color = C.Text) },
            text = {
                Column {
                    if (subjectId <= 0) {
                        Text("科目", color = C.Sub, fontSize = 12.sp)
                        Column {
                            vm.subjects.forEach { s ->
                                Text(
                                    (if (pickedSubject == s.id) "● " else "○ ") + s.name,
                                    color = subjectColor(s.colorArgb),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { pickedSubject = s.id }
                                        .padding(vertical = 6.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("每周", color = C.Sub, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Weekdays.ORDER.forEach { d ->
                            val sel = d in pickedDays
                            Text(
                                Weekdays.NAMES[d] ?: d,
                                color = if (sel) C.Background else C.Text,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) C.Text else C.SurfaceHigh)
                                    .clickable {
                                        pickedDays = if (sel) pickedDays - d else pickedDays + d
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                DialogAction("保存", onClick = {
                    val sid = pickedSubject
                    when {
                        sid == null -> Toast.makeText(ctx, "先选科目", Toast.LENGTH_SHORT).show()
                        pickedDays.isEmpty() -> Toast.makeText(ctx, "至少选一天", Toast.LENGTH_SHORT).show()
                        else -> {
                            vm.add(sid, pickedDays.joinToString(","), onError, onDone = { showAdd = false })
                        }
                    }
                })
            },
            dismissButton = { DialogAction("取消", secondary = true, onClick = { showAdd = false }) },
            containerColor = C.Surface,
        )
    }
}
