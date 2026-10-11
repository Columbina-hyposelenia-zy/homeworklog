package com.zy.homeworklog.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_ATTACHMENTS = 9
private const val THUMBNAIL_EDGE = 256
private const val VIEWER_EDGE = 2048

@Composable
fun AttachmentEditor(
    paths: List<String>,
    enabled: Boolean,
    onAddUris: (List<Uri>) -> Unit,
    onRemove: (String) -> Unit,
) {
    val context = LocalContext.current
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnAddUris by rememberUpdatedState(onAddUris)
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS),
    ) { uris ->
        if (latestEnabled && uris.isNotEmpty()) {
            latestOnAddUris(uris)
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = pendingCameraPath
        pendingCameraPath = null
        val file = path?.let(::File) ?: return@rememberLauncherForActivityResult
        if (saved && file.length() > 0L && latestEnabled) {
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.onSuccess { uri -> latestOnAddUris(listOf(uri)) }
                .onFailure {
                    file.delete()
                    Toast.makeText(context, "无法读取拍摄的图片", Toast.LENGTH_SHORT).show()
                }
        } else {
            file.delete()
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("图片附件", color = C.Sub, fontSize = 14.sp)
                Text("${paths.size}/$MAX_ATTACHMENTS", color = C.Text, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AttachmentAction("相册", enabled && paths.size < MAX_ATTACHMENTS) {
                if (paths.size < MAX_ATTACHMENTS) picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            AttachmentAction("拍照", enabled && paths.size < MAX_ATTACHMENTS) {
                val cameraDir = File(context.cacheDir, "camera")
                var file: File? = null
                try {
                    if (!cameraDir.exists() && !cameraDir.mkdirs()) error("无法创建拍照目录")
                    file = File.createTempFile("attachment_", ".jpg", cameraDir)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    pendingCameraPath = file.absolutePath
                    camera.launch(uri)
                } catch (_: Exception) {
                    file?.delete()
                    pendingCameraPath = null
                    Toast.makeText(context, "无法打开相机", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (paths.isNotEmpty()) {
            paths.chunked(3).forEachIndexed { rowIndex, rowPaths ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowPaths.forEachIndexed { columnIndex, path ->
                        val index = rowIndex * 3 + columnIndex
                        AttachmentGridItem(
                            path = path,
                            index = index,
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                            onOpen = { viewerIndex = index },
                            onRemove = { onRemove(path) },
                        )
                    }
                    repeat(3 - rowPaths.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    viewerIndex?.let { start ->
        AttachmentViewer(paths, start, onDismiss = { viewerIndex = null })
    }
}

@Composable
private fun AttachmentAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) C.Blue else C.Sub, fontSize = 12.sp)
    }
}

@Composable
fun ThumbnailStrip(
    paths: List<String>,
    modifier: Modifier = Modifier,
    onOpen: (Int) -> Unit,
) {
    if (paths.isEmpty()) return
    BoxWithConstraints(modifier) {
        val slotCount = if (maxWidth == Dp.Infinity) 3
        else ((maxWidth + 8.dp) / 56.dp).toInt().coerceIn(1, 3)
        val cellSize = minOf(48.dp, maxWidth)
        val hasOverflow = paths.size > slotCount
        val imageCount = if (hasOverflow) slotCount - 1 else paths.size
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            paths.take(imageCount).forEachIndexed { index, path ->
                AttachmentThumbnail(path, index, onOpen, size = cellSize)
            }
            if (hasOverflow) {
                AttachmentThumbnail(
                    path = paths[imageCount],
                    index = imageCount,
                    onOpen = onOpen,
                    overlayText = "+${paths.size - slotCount}",
                    size = cellSize,
                )
            }
        }
    }
}

@Composable
private fun AttachmentThumbnail(
    path: String,
    index: Int,
    onOpen: (Int) -> Unit,
    overlayText: String? = null,
    size: Dp = 48.dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(C.SurfaceHigh)
            .border(1.dp, C.Hair, RoundedCornerShape(8.dp))
            .semantics { contentDescription = "打开第${index + 1}张图片" }
            .clickable(role = Role.Button) { onOpen(index) },
        contentAlignment = Alignment.Center,
    ) {
        AttachmentImage(path, Modifier.fillMaxSize(), THUMBNAIL_EDGE, ContentScale.Crop)
        if (overlayText != null) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Text(overlayText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AttachmentGridItem(
    path: String,
    index: Int,
    enabled: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(C.SurfaceHigh),
    ) {
        Box(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "预览第${index + 1}张图片" }
                .clickable(role = Role.Button, onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            AttachmentImage(path, Modifier.fillMaxSize(), THUMBNAIL_EDGE, ContentScale.Crop)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .semantics { contentDescription = "移除第${index + 1}张图片" }
                .clickable(enabled = enabled, role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(Color(0xCC737A84)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(16.dp)) {
                    val stroke = 2.dp.toPx()
                    val inset = size.minDimension * 0.22f
                    val end = size.minDimension - inset
                    drawLine(Color.White, Offset(inset, inset), Offset(end, end), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(Color.White, Offset(end, inset), Offset(inset, end), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
fun AttachmentMarker(modifier: Modifier) {
    val markerColor = C.Sub
    Canvas(modifier.size(14.dp)) {
        val stroke = 1.15.dp.toPx()
        val inset = stroke / 2f
        drawRoundRect(
            color = markerColor,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(stroke),
        )
        drawCircle(
            color = markerColor,
            radius = 1.dp.toPx(),
            center = Offset(size.width * 0.67f, size.height * 0.34f),
        )
        val mountain = Path().apply {
            moveTo(size.width * 0.14f, size.height * 0.78f)
            lineTo(size.width * 0.42f, size.height * 0.48f)
            lineTo(size.width * 0.57f, size.height * 0.63f)
            lineTo(size.width * 0.72f, size.height * 0.48f)
            lineTo(size.width * 0.87f, size.height * 0.78f)
        }
        drawPath(mountain, color = markerColor, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AttachmentViewer(paths: List<String>, initialIndex: Int, onDismiss: () -> Unit) {
    if (paths.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(paths.indices),
        pageCount = { paths.size },
    )
    var zoomed by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) { zoomed = false }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(top = 56.dp),
                key = { paths[it] },
                userScrollEnabled = !zoomed,
            ) { page ->
                val path = paths[page]
                var scale by remember(path) { mutableFloatStateOf(1f) }
                var offset by remember(path) { mutableStateOf(Offset.Zero) }
                androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
                    if (page == pagerState.currentPage) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
                    scale = nextScale
                    zoomed = nextScale > 1f
                    offset = if (nextScale > 1f) offset + panChange else Offset.Zero
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AttachmentImage(
                        path = path,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                            .transformable(transformState, canPan = { scale > 1f })
                            .pointerInput(path) {
                                detectTapGestures(onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        zoomed = false
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                        zoomed = true
                                    }
                                })
                            },
                        maxDimension = VIEWER_EDGE,
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewerAction("关闭", onDismiss)
                Text(
                    "${pagerState.currentPage + 1}/${paths.size}",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}

@Composable
private fun ViewerAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("×", color = Color.White, fontSize = 28.sp)
    }
}

@Composable
private fun AttachmentImage(
    path: String,
    modifier: Modifier,
    maxDimension: Int,
    contentScale: ContentScale,
) {
    val bitmap by produceState<ImageBitmap?>(null, path, maxDimension) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBitmap(path, maxDimension)?.asImageBitmap()
        }
    }
    if (bitmap == null) {
        Box(modifier.background(C.SurfaceHigh), contentAlignment = Alignment.Center) {
            AttachmentMarker(Modifier)
        }
    } else {
        Image(bitmap = bitmap!!, contentDescription = null, modifier = modifier, contentScale = contentScale)
    }
}

private fun decodeSampledBitmap(path: String, maxDimension: Int): Bitmap? {
    var decoded: Bitmap? = null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        decoded = BitmapFactory.decodeFile(path, options) ?: return null
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val transform = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    setRotate(180f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    setRotate(270f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        if (transform.isIdentity) decoded
        else Bitmap.createBitmap(decoded!!, 0, 0, decoded!!.width, decoded!!.height, transform, true)
            .also { if (it !== decoded) decoded?.recycle() }
    } catch (_: OutOfMemoryError) {
        decoded?.recycle()
        null
    } catch (_: Exception) {
        decoded?.recycle()
        null
    }
}
