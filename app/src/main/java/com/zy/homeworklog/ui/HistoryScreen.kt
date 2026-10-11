package com.zy.homeworklog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.zy.homeworklog.util.TimeFmt
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HistoryRow(
    val id: Long,
    val subjectName: String,
    val subjectColor: Long,
    val content: String,
    val completedAt: Long,
    val imagePaths: List<String> = emptyList(),
)

class HistoryViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = (app as HomeworkApp).repo
    var rows by mutableStateOf<List<HistoryRow>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            combine(repo.completed, repo.subjects, repo.images) { recs, subs, images ->
                val smap = subs.associateBy { it.id }
                val imageMap = images.groupBy { it.recordId }
                recs.map { r ->
                    HistoryRow(
                        id = r.id,
                        subjectName = smap[r.subjectId]?.name ?: "?",
                        subjectColor = smap[r.subjectId]?.colorArgb ?: 0xFF9E9E9E,
                        content = r.content,
                        completedAt = r.completedAt ?: 0L,
                        imagePaths = imageMap[r.id].orEmpty().map { it.path },
                    )
                }
            }.collect { rows = it }
        }
    }
}

/** 历史记录：科目 + 完成日期 + 内容摘要，按完成时间倒序。 */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as HomeworkApp
    val vm: HistoryViewModel = viewModel(
        key = "history",
        factory = viewModelFactory { initializer { HistoryViewModel(app) } },
    )
    var viewerPaths by androidx.compose.runtime.remember { mutableStateOf<List<String>>(emptyList()) }
    var viewerIndex by androidx.compose.runtime.remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().background(C.Background)) {
        PageTopBar("历史记录", onBack = onBack)
        if (vm.rows.isEmpty()) {
            Text(
                "暂无完成记录",
                color = C.Sub,
                fontSize = 14.sp,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.rows, key = { it.id }) { r ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(C.Surface).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(r.subjectName, color = subjectColor(r.subjectColor), fontSize = 14.sp,
                            modifier = Modifier.weight(1f).padding(end = 12.dp))
                        Text(TimeFmt.fmtDate(r.completedAt), color = C.Sub, fontSize = 12.sp)
                    }
                    Text(r.content.ifBlank { if (r.imagePaths.isNotEmpty()) "图片作业" else "" }, color = C.Text, fontSize = 14.sp, lineHeight = 22.sp,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp))
                    if (r.imagePaths.isNotEmpty()) ThumbnailStrip(
                        r.imagePaths, Modifier.padding(top = 8.dp),
                        onOpen = { viewerPaths = r.imagePaths; viewerIndex = it },
                    )
                }
            }
        }
    }
    if (viewerPaths.isNotEmpty()) AttachmentViewer(viewerPaths, viewerIndex) { viewerPaths = emptyList() }
}
