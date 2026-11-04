package de.wortschatz.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 从照片里识别德语单词，并给出每个词在图片上的位置。
 *
 * 使用 ML Kit 的**内置**拉丁字母识别模型（`text-recognition-bundled-common`），
 * 模型随 APK 分发，不需要 Google Play 服务，也不需要联网。
 *
 * 位置信息是为了「在原图上用词块盖住识别到的词」这个交互——识别与显示必须用同一张
 * Bitmap，否则坐标要跨分辨率换算，稍有偏差词块就会飘到别的词上。
 */
object PhotoOcr {

    /** 德语词形：字母（含变音字母）、内部可带连字符 */
    private val TOKEN = Regex("[A-Za-zÄÖÜäöüß][A-Za-zÄÖÜäöüß\\-]{1,29}")

    /**
     * 高频虚词，几乎每行都有，识别进来没有学习价值。
     *
     * 注意只放纯功能词：`bitte`、`danke` 这类虽然常出现在练习指令里，但本身就是教材词汇，
     * 早前误放在这里，导致它们永远进不了单词书（表现为「文本里明明有，导入却没有」）。
     */
    private val STOPWORDS = setOf(
        "der", "die", "das", "den", "dem", "des", "und", "oder", "aber", "ich", "du",
        "er", "sie", "es", "wir", "ihr", "mich", "dich", "sich", "uns", "euch",
        "ist", "sind", "bin", "bist", "seid", "war", "waren", "hat", "haben", "habe",
        "ein", "eine", "einen", "einem", "einer", "eines", "nicht", "kein", "keine",
        "zu", "im", "in", "am", "an", "auf", "mit", "von", "vom", "für", "bei", "aus",
        "nach", "vor", "über", "unter", "zum", "zur", "auch", "noch", "nur", "schon",
        "sehr", "wie", "was", "wer", "wo", "wann", "warum", "dass", "wenn", "dann",
        "hier", "dort"
    )

    /** 一个识别到的词元及其在图片上的位置（像素，原点在图片左上角） */
    data class OcrWord(
        val token: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    /**
     * 扫描结果。
     *
     * [tokens] 是全部候选词（含功能词），[stops] 是其中命中停用词表的部分，
     * [words] 带位置，只有图片识别才有（文本导入为空）。
     * 哪些真的丢掉由调用方结合词库决定——见 AppViewModel。
     */
    data class Scan(
        val tokens: List<String>,
        val stops: Set<String>,
        val words: List<OcrWord> = emptyList()
    )

    /**
     * 识别一张已经解码好的图片。
     *
     * 传 Bitmap 而不是 Uri：界面要把词块画在这张图上，两边用同一份像素才不会错位。
     */
    suspend fun recognize(bitmap: Bitmap): Scan =
        suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result ->
                        recognizer.close()
                        if (cont.isActive) cont.resume(scan(result))
                    }
                    .addOnFailureListener { e ->
                        recognizer.close()
                        if (cont.isActive) cont.resumeWithException(e)
                    }
            } catch (e: Exception) {
                recognizer.close()
                if (cont.isActive) cont.resumeWithException(e)
            }
        }

    /** 从 ML Kit 结果里取出带位置的候选词，顺序与阅读顺序一致 */
    private fun scan(result: Text): Scan {
        val words = mutableListOf<OcrWord>()
        val seen = LinkedHashSet<String>()
        val stops = mutableSetOf<String>()

        for (block in result.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    val token = clean(element.text) ?: continue
                    words.add(
                        OcrWord(
                            token = token,
                            left = box.left.toFloat(),
                            top = box.top.toFloat(),
                            right = box.right.toFloat(),
                            bottom = box.bottom.toFloat()
                        )
                    )
                    if (token.lowercase() in STOPWORDS) stops.add(token)
                    seen.add(token)
                }
            }
        }
        return Scan(seen.toList(), stops, words)
    }

    /** 词元清洗：去掉标点，跳过单字母与短全大写（多为缩写、等级标记 A1/CD） */
    private fun clean(raw: String): String? {
        val m = TOKEN.find(raw.trim()) ?: return null
        val token = m.value.trim('-')
        if (token.length < 2) return null
        if (token.length <= 3 && token.all { it.isUpperCase() }) return null
        return token
    }

    /**
     * 从文本里挑出候选德语词：去重、跳过虚词与纯数字/缩写。
     *
     * 被跳过的虚词数量单独返回，用于向用户解释「为什么识别出的词比看到的少」。
     */
    fun scan(text: String): Scan {
        val seen = LinkedHashSet<String>()
        val stops = mutableSetOf<String>()
        TOKEN.findAll(text).forEach { m ->
            val raw = m.value.trim('-')
            if (raw.length < 2) return@forEach
            // 全大写且很短的多为缩写或等级标记（A1、CD、DVD）
            if (raw.length <= 3 && raw.all { it.isUpperCase() }) return@forEach
            if (raw.lowercase() in STOPWORDS) stops.add(raw)
            seen.add(raw)
        }
        return Scan(seen.toList(), stops)
    }
}

/**
 * 导入用的图片解码。
 *
 * 两个必须做的处理：
 * - **按 EXIF 摆正**：竖着拍的照片像素其实是横的，不转过来 OCR 拿到的是躺着的字，词块位置也会跟着歪。
 * - **限制长边**：教材照片动辄 4000px，原样解码要几十 MB，这里缩到 [MAX_SIDE] 以内；
 *   OCR 与显示都用这张缩小图，坐标天然一致。
 */
object PhotoBitmap {

    /**
     * 长边上限。一张 1600px 的位图约 7MB 堆内存，多选几张也撑得住；
     * 再大对识别精度没有实质提升，只是更容易 OOM。
     */
    const val MAX_SIDE = 1600

    fun load(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap? {
        // 一次性读成字节：相册（Photo Picker）的 content:// 不适合两次 openInputStream，
        // 第一次用 inJustDecodeBounds 探测后，第二次常见拿不到流，表现为「图片解码失败」
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null

        val degrees = readRotation(bytes)
        if (degrees == 0) return decoded
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun readRotation(bytes: ByteArray): Int {
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }
}
