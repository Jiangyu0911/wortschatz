package de.wortschatz.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import de.wortschatz.app.AppViewModel
import de.wortschatz.app.ImportBlock
import de.wortschatz.app.ImportPhoto
import java.io.File

/**
 * 拍照 / 相册导入单词。
 *
 * 识别结果不再是一张列表，而是**直接盖在照片上**：每个识别到的词在原位置生成一个词块，
 * 点一下取消 / 选上。这样判断「这个词识别对了没有」不需要来回比对列表和照片——
 * 原词就在词块下面，位置对不对一眼就能看出。
 *
 * 三种状态：
 * - 命中词库且选中：实心色块，盖住原词，块上写词库词形
 * - 命中词库但没选中：只留细边框，原文露出来
 * - 词库未收录：红色块，选上后到下方补填释义才会入库
 */
@Composable
fun PhotoImportScreen(vm: AppViewModel, onBack: () -> Unit, onLookup: (String) -> Unit = {}) {
    // 不接系统返回键的话，按返回会直接退出整个应用
    BackHandler { onBack() }
    val context = LocalContext.current
    // 词库未收录的词：用户自己补的释义，key 为原文词形
    val meanings = remember { mutableStateMapOf<String, String>() }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var missedExpanded by remember { mutableStateOf(false) }

    // 新一轮识别后丢掉上一轮的补填内容
    LaunchedEffect(vm.importMissed) {
        val alive = vm.importMissed.toSet()
        meanings.keys.retainAll(alive)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            pendingUri?.let { uri ->
                vm.recognizePhotos(listOf(uri), replace = vm.importPhotoCount == 0)
            }
        }
    }
    // 相册支持一次多选；第一次选图时替换旧结果，之后再选则追加
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.recognizePhotos(uris, replace = vm.importPhotoCount == 0)
        }
    }

    fun takePhoto() {
        val dir = File(context.cacheDir, "ocr").apply { mkdirs() }
        val file = File.createTempFile("shot_", ".jpg", dir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingUri = uri
        cameraLauncher.launch(uri)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                // 未收录词的释义输入框在底部，键盘弹起时要把内容顶上去，否则输入框被盖住
                .imePadding()
        ) {
            // ---- 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "拍照导入单词",
                        fontFamily = FontFamily.Default,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "识别结果已盖在原文上，点词块即可选中或取消",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (vm.importPhotos.isNotEmpty()) {
                    TextButton(onClick = { meanings.clear(); vm.clearImport() }) {
                        Text(
                            text = "清除",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---- 拍照 / 相册
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { takePhoto() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Rounded.CameraAlt, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("拍照", fontSize = 14.sp)
                }
                Button(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("相册", fontSize = 14.sp)
                }
            }

            // ---- 概要与全选
            if (vm.importPhotos.isNotEmpty() && !vm.importLoading) {
                val hit = vm.importWords.size
                val picked = vm.importWords.count { it.id in vm.importPicked }
                val allPicked = picked == hit && vm.importPickedTerms.size >= vm.importMissed.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildString {
                            append("命中 $hit 个 · 已选 $picked")
                            if (vm.importMissed.isNotEmpty()) {
                                append(" · 未收录 ${vm.importMissed.size}")
                            }
                        },
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.setAllImportsPicked(!allPicked) }) {
                        Text(
                            text = if (allPicked) "全不选" else "全选",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "实心＝导入 · 空框＝跳过 · 红色＝词库未收录（补释义后导入）",
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp),
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            vm.importNotice?.let { notice ->
                Text(
                    text = notice,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ---- 主体
            when {
                vm.importLoading -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "正在识别…",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                vm.importError != null -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = vm.importError ?: "",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                vm.importPhotos.isEmpty() -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "拍教材或笔记上的单词表，识别结果会直接盖在照片上；" +
                            "可以连拍多张、或从相册一次多选，结果自动合并去重。",
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(vm.importPhotos) { photo ->
                        PhotoCard(
                            photo = photo,
                            vm = vm,
                            onPickMissed = { missedExpanded = true }
                        )
                    }

                    if (vm.importMissed.isNotEmpty()) {
                        item {
                            MissedPanel(
                                vm = vm,
                                meanings = meanings,
                                expanded = missedExpanded,
                                onToggleExpand = { missedExpanded = !missedExpanded },
                                onRemove = { term ->
                                    meanings.remove(term)
                                    vm.removeImportMissed(term)
                                }
                            )
                        }
                    }

                    if (vm.importIgnored > 0) {
                        item {
                            Text(
                                text = "另有 ${vm.importIgnored} 个虚词（der/und/ist 等）已忽略",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            if (vm.importPhotos.isNotEmpty() && !vm.importLoading) {
                ImportConfirmBar(
                    vm = vm,
                    checked = vm.importPicked,
                    defaultName = "拍照导入",
                    onDone = onBack,
                    meanings = meanings,
                    pickedTerms = vm.importPickedTerms
                )
            }
        }
    }
}

/**
 * 一张照片与它上面的词块。
 *
 * 照片尽量占满：左右只留 4dp，高度上限取屏幕的 92%（超过说明是长截图，按高度适配）。
 * 图越大，原词在屏幕上的像素越多，词块就能贴着原文画而不用放大——
 * 早先给词块设了「至少 19dp 高」的下限，结果块比原词还高，压到上下行。
 *
 * 图片用 FillBounds 而不是 Fit：让「图上像素 → 屏幕 dp」是一个固定倍数，
 * 词块坐标乘同一个系数就能对准。
 */
@Composable
private fun PhotoCard(
    photo: ImportPhoto,
    vm: AppViewModel,
    onPickMissed: () -> Unit
) {
    val ratio = photo.width.toFloat() / photo.height.toFloat()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        val maxH = screenHeight * 0.92f
        val availW = maxWidth
        var drawW = availW
        var drawH = drawW / ratio
        if (drawH > maxH) {
            drawH = maxH
            drawW = maxH * ratio
        }
        // 一个图片像素对应多少 dp
        val unit: Dp = drawW / photo.width

        Box(
            modifier = Modifier
                .width(drawW)
                .height(drawH)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    RoundedCornerShape(10.dp)
                )
        ) {
            Image(
                bitmap = photo.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            photo.blocks.forEach { block ->
                // 被当作虚词丢掉（der/und/ist 这类词库也没有的功能词）不画：
                // 它们永远不会进词书，画出来只会在页面上散布一堆不能用的红框
                val importable = block.word != null ||
                    vm.importMissed.any { it.equals(block.token, ignoreCase = true) }
                if (importable) {
                    WordBlock(
                        block = block,
                        unit = unit,
                        vm = vm,
                        onPickMissed = onPickMissed
                    )
                }
            }
        }
    }
}

/**
 * 盖在一个词上的词块。
 *
 * 选中时盖住原文并写出词库词形；没选中时只留一道细边框，露出原文——
 * 这样「不导入」不会让人以为这个词没被识别到。
 *
 * 尺寸严格跟着 OCR 给的原词框走，不再设最小宽高：块比原词大就会压到相邻行，
 * 看着像一片色块贴在书上。块变小后点击面积会不够，所以外面套一层透明的
 * 点击层（上下左右各外扩 [TOUCH_PAD]），视觉不动、热区变大。
 */
@Composable
private fun WordBlock(
    block: ImportBlock,
    unit: Dp,
    vm: AppViewModel,
    onPickMissed: () -> Unit
) {
    val word = block.word
    val picked = if (word != null) vm.isImportPicked(word.id) else vm.isImportTermPicked(block.token)
    val missed = word == null

    val left = unit * block.left
    val top = unit * block.top
    val width = (unit * (block.right - block.left)).coerceAtLeast(6.dp)
    val height = (unit * (block.bottom - block.top)).coerceAtLeast(7.dp)

    val accent = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val fill = if (picked) accent else Color.Transparent
    val borderColor = if (picked) Color.Transparent else accent.copy(alpha = 0.5f)
    val label = word?.lemma ?: block.token
    // 字号同时受块高和块宽限制，让字一定落在块内：
    // 德语字符平均宽度约为字号的 0.56 倍，据此反推块宽能容纳多大字号
    val byHeight = height.value * 0.74f
    val byWidth = width.value / (label.length.coerceAtLeast(1) * 0.56f)
    val fontSize = minOf(byHeight, byWidth).coerceIn(5f, 11f).sp
    val shape = RoundedCornerShape(3.dp)

    Box(
        modifier = Modifier
            .offset(x = left - TOUCH_PAD, y = top - TOUCH_PAD)
            .size(width + TOUCH_PAD * 2, height + TOUCH_PAD * 2)
            .zIndex(if (picked) 1f else 0f)
            .clickable {
                if (word != null) {
                    vm.toggleImportWord(word.id)
                } else {
                    vm.toggleImportTerm(block.token)
                    onPickMissed()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(TOUCH_PAD)
                .clip(shape)
                .background(fill, shape)
                .border(if (picked) 0.dp else 0.8.dp, borderColor, shape),
            contentAlignment = Alignment.Center
        ) {
            if (picked) {
                Text(
                    text = label,
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

/** 词块点击热区的外扩量：视觉不跟着放大，只是更好点中 */
private val TOUCH_PAD = 4.dp

/**
 * 未收录词：默认收起，点开后可勾选并补填释义。
 *
 * 不展开也允许从照片上的红色词块点选——两种入口对应两种习惯：
 * 想逐词核对就点照片，想批量处理就展开这个面板。
 */
@Composable
private fun MissedPanel(
    vm: AppViewModel,
    meanings: MutableMap<String, String>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onRemove: (String) -> Unit
) {
    val total = vm.importMissed.size
    val picked = vm.importMissed.count { vm.isImportTermPicked(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "词库未收录 $total 个" + if (picked > 0) "（已选 $picked）" else "",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp
                else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
        if (!expanded) {
            Text(
                text = "红色词块就是这些词，点一下可选；选上后填释义就会一起导入",
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Spacer(Modifier.height(4.dp))
        vm.importMissed.forEach { term ->
            val isPicked = vm.isImportTermPicked(term)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isPicked,
                    onCheckedChange = { vm.toggleImportTerm(term) }
                )
                Text(
                    text = term,
                    modifier = Modifier.width(96.dp),
                    fontFamily = FontFamily.Default,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = meanings[term] ?: "",
                    onValueChange = { meanings[term] = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("补填中文释义", fontSize = 11.5.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    shape = RoundedCornerShape(9.dp)
                )
                IconButton(onClick = { onRemove(term) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "删除 $term",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
