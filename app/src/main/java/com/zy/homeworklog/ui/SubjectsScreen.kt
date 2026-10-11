package com.zy.homeworklog.ui

import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zy.homeworklog.HomeworkApp
import com.zy.homeworklog.data.Subject
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class SubjectsViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = (app as HomeworkApp).repo
    var subjects by mutableStateOf<List<Subject>>(emptyList())
        private set

    init {
        viewModelScope.launch { repo.subjects.collect { subjects = it } }
    }

    private var writing = false

    /** 新建科目选到已占用颜色：新科目优先，原科目随机换到剩余颜色。 */
    fun saveWithSwap(
        id: Long?, name: String, color: Long, conflict: Subject?, swap: Long,
        onError: (String) -> Unit, onDone: () -> Unit,
    ) {
        if (writing) return
        writing = true
        viewModelScope.launch {
            try {
                repo.saveSubjectWithSwap(id, name, color, conflict, swap)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(if (e is IllegalArgumentException || e is IllegalStateException) {
                    e.message ?: "科目保存失败，请重试"
                } else "科目保存失败，请重试")
            } finally {
                writing = false
            }
        }
    }

    fun delete(s: Subject, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                if (!repo.deleteSubject(s)) onError("该科目仍有作业记录（含历史），暂不能删除")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError("科目删除失败，请重试")
            }
        }
    }
}

/** 10 色色板，一行 5 个、两行展示。 */
@Composable
fun PaletteGrid(selected: Long, onPick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PALETTE.take(5).forEach { p -> ColorSwatch(p, p == selected, onPick) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PALETTE.drop(5).forEach { p -> ColorSwatch(p, p == selected, onPick) }
        }
    }
}

@Composable
private fun ColorSwatch(color: Long, selected: Boolean, onPick: (Long) -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .background(subjectColor(color), CircleShape)
            .then(
                if (selected) Modifier.border(2.dp, C.Text, CircleShape)
                else Modifier
            )
            .clickable { onPick(color) },
    )
}

/** 冲突换色实时预览：新选颜色若已被其他科目占用，原科目将随机换到剩余颜色。 */
@Composable
fun SwapHint(subjects: List<Subject>, excludeId: Long?, color: Long, swap: Long) {
    val conflict = subjects.firstOrNull { it.colorArgb == color && it.id != excludeId } ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 10.dp),
    ) {
        Text("「${conflict.name}」将改用 ", color = C.Sub, fontSize = 12.sp)
        Box(Modifier.size(12.dp).background(subjectColor(swap), CircleShape))
        Text(" 随机色", color = C.Sub, fontSize = 12.sp)
    }
}

/** 冲突换色：从剩余未占用颜色中随机取一个；全部占满则生成随机色。 */
fun swapColorFor(subjects: List<Subject>, conflictId: Long, takenColor: Long): Long {
    val usedByOthers = subjects.filter { it.id != conflictId }.map { it.colorArgb }
    val free = PALETTE.filter { it != takenColor && it !in usedByOthers }
    return if (free.isNotEmpty()) free.random()
    else 0xFF000000L + kotlin.random.Random.nextLong(0xFFFFFF)
}

@Composable
fun SubjectsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HomeworkApp
    val vm: SubjectsViewModel = viewModel(factory = viewModelFactory { initializer { SubjectsViewModel(app) } })
    val ctx = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Subject?>(null) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(PALETTE.first()) }

    Column(Modifier.fillMaxSize().background(C.Background)) {
        PageTopBar("科目与颜色", onBack = onBack)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(vm.subjects, key = { it.id }) { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(C.Surface)
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 16.dp)
                        .clickable {
                            editTarget = s
                            name = s.name
                            color = s.colorArgb
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).background(subjectColor(s.colorArgb), CircleShape))
                    Text(
                        s.name,
                        color = C.Text,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                    Text("重命名 / 改色 ›", color = C.Sub, fontSize = 12.sp)
                }
            }
        }
        Button(
            onClick = {
                name = ""
                color = PALETTE.firstOrNull { p -> vm.subjects.none { it.colorArgb == p } } ?: PALETTE.first()
                showAdd = true
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.Surface, contentColor = C.Text),
            shape = RoundedCornerShape(10.dp),
        ) { Text("添加科目") }
        Spacer(Modifier.navigationBarsPadding())
    }

    if (showAdd || editTarget != null) {
        val isEdit = editTarget != null
        val conflict = vm.subjects.firstOrNull { it.colorArgb == color && it.id != editTarget?.id }
        val swap = remember(vm.subjects, editTarget?.id, color, conflict?.id) {
            swapColorFor(vm.subjects, conflict?.id ?: -1L, color)
        }
        AppDialog(
            onDismissRequest = { showAdd = false; editTarget = null },
            title = { Text(if (isEdit) "编辑科目" else "新建科目", color = C.Text) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("科目名", color = C.Sub) },
                    )
                    Spacer(Modifier.height(12.dp))
                    PaletteGrid(selected = color, onPick = { color = it })
                    // 实时预览：颜色冲突时原科目随机换色
                    SwapHint(subjects = vm.subjects, excludeId = editTarget?.id, color = color, swap = swap)
                    if (isEdit) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "删除该科目",
                            color = C.Red,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable {
                                editTarget?.let {
                                    vm.delete(it) { m -> Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show() }
                                }
                                editTarget = null
                                showAdd = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                DialogAction("保存", onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(ctx, "科目名不能为空", Toast.LENGTH_SHORT).show()
                    } else {
                        vm.saveWithSwap(editTarget?.id, name, color, conflict, swap,
                            onError = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() },
                            onDone = { editTarget = null; showAdd = false })
                    }
                })
            },
            dismissButton = {
                DialogAction("取消", secondary = true, onClick = { showAdd = false; editTarget = null },
                )
            },
            containerColor = C.Surface,
        )
    }
}
