package com.nils.artifact.label

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.print.PrintHelper
import android.content.Context
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.nils.artifact.data.ArtifactItem
import com.nils.artifact.data.CollectionSettings
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object ArtifactLabelRenderer {
    const val WIDTH = 384
    const val HEIGHT = 240

    fun render(
        item: ArtifactItem,
        settings: CollectionSettings,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = false
        }

        canvas.drawColor(Color.WHITE)
        drawQr(
            canvas = canvas,
            payload = qrPayload(ownerName = settings.ownerName, uid = item.catalogUid),
            x = 16,
            y = 18,
            size = 128,
            paint = paint,
        )
        drawUidUnderQr(
            canvas = canvas,
            uid = item.catalogUid,
            qrLeft = 16f,
            qrWidth = 128f,
            baseline = 176f,
            paint = paint,
        )
        drawDetails(canvas = canvas, item = item, paint = paint)
        drawPropertyLine(canvas = canvas, ownerName = settings.ownerName, paint = paint)
        return thresholdBlackWhite(bitmap)
    }

    fun pack2Bpp(bitmap: Bitmap): ByteArray {
        require(bitmap.width == WIDTH && bitmap.height == HEIGHT) {
            "Label bitmap must be ${WIDTH}x${HEIGHT}"
        }

        val output = ByteArray(WIDTH * HEIGHT / 4)
        var outIndex = 0
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH step 4) {
                var byte = 0
                for (offset in 0 until 4) {
                    val pixel = bitmap.getPixel(x + offset, y)
                    val value = if (isBlack(pixel)) 0 else 3
                    byte = byte or (value shl ((3 - offset) * 2))
                }
                output[outIndex++] = byte.toByte()
            }
        }
        return output
    }

    fun print(
        context: Context,
        bitmap: Bitmap,
        uid: Long,
    ) {
        PrintHelper(context).apply {
            colorMode = PrintHelper.COLOR_MODE_MONOCHROME
            orientation = PrintHelper.ORIENTATION_LANDSCAPE
            scaleMode = PrintHelper.SCALE_MODE_FIT
        }.printBitmap("Artifact label #$uid", bitmap)
    }

    private fun drawQr(
        canvas: Canvas,
        payload: String,
        x: Int,
        y: Int,
        size: Int,
        paint: Paint,
    ) {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        )
        val matrix = MultiFormatWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints,
        )
        val rect = Rect()
        for (row in 0 until matrix.height) {
            for (col in 0 until matrix.width) {
                if (!matrix[col, row]) continue
                rect.set(x + col, y + row, x + col + 1, y + row + 1)
                canvas.drawRect(rect, paint)
            }
        }
    }

    private fun drawDetails(
        canvas: Canvas,
        item: ArtifactItem,
        paint: Paint,
    ) {
        val left = 164f
        val right = 372f
        var y = 22f

        y = drawField(
            canvas = canvas,
            paint = paint,
            label = "MANUFACTURER",
            value = item.manufacturer.ifBlank { "-" },
            left = left,
            right = right,
            y = y,
        )
        y = drawField(
            canvas = canvas,
            paint = paint,
            label = "MODEL",
            value = item.itemType.ifBlank { "-" },
            left = left,
            right = right,
            y = y,
        )
        drawField(
            canvas = canvas,
            paint = paint,
            label = "YEAR",
            value = productionYearsLabel(item.productionYearStart, item.productionYearEnd) ?: "-",
            left = left,
            right = right,
            y = y,
        )
    }

    private fun drawUidUnderQr(
        canvas: Canvas,
        uid: Long,
        qrLeft: Float,
        qrWidth: Float,
        baseline: Float,
        paint: Paint,
    ) {
        val value = "#$uid"
        paint.isFakeBoldText = true
        paint.textSize = 24f
        val text = fitSingleLine(paint, value, qrWidth)
        val textWidth = paint.measureText(text)
        canvas.drawText(text, qrLeft + (qrWidth - textWidth) / 2f, baseline, paint)
        paint.isFakeBoldText = false
    }

    private fun drawField(
        canvas: Canvas,
        paint: Paint,
        label: String,
        value: String,
        left: Float,
        right: Float,
        y: Float,
    ): Float {
        paint.isFakeBoldText = true
        paint.textSize = 13f
        canvas.drawText(label, left, y + 11f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 20f
        val lines = wrapText(
            paint = paint,
            text = value.uppercase(Locale.getDefault()),
            maxWidth = right - left,
            maxLines = 2,
        )
        var valueY = y + 33f
        lines.forEach { line ->
            canvas.drawText(line, left, valueY, paint)
            valueY += 21f
        }
        return max(valueY + 5f, y + 54f)
    }

    private fun drawPropertyLine(
        canvas: Canvas,
        ownerName: String,
        paint: Paint,
    ) {
        val value = if (ownerName.isBlank()) {
            "PROPERTY OF"
        } else {
            "PROPERTY OF ${ownerName.uppercase(Locale.getDefault())}"
        }
        paint.isFakeBoldText = true
        paint.textSize = 18f
        val text = fitSingleLine(paint, value, 360f)
        val textWidth = paint.measureText(text)
        canvas.drawText(text, (WIDTH - textWidth) / 2f, 224f, paint)
        paint.isFakeBoldText = false
    }

    private fun wrapText(
        paint: Paint,
        text: String,
        maxWidth: Float,
        maxLines: Int,
    ): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return listOf("-")

        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotBlank()) {
                    lines += current
                    current = word
                } else {
                    lines += fitSingleLine(paint, word, maxWidth)
                    current = ""
                }
            }
        }
        if (current.isNotBlank()) lines += current

        return lines.take(maxLines).mapIndexed { index, line ->
            if (index == maxLines - 1 && lines.size > maxLines) {
                fitSingleLine(paint, "$line...", maxWidth)
            } else {
                fitSingleLine(paint, line, maxWidth)
            }
        }
    }

    private fun fitSingleLine(
        paint: Paint,
        text: String,
        maxWidth: Float,
    ): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "...") > maxWidth) {
            end -= 1
        }
        return text.substring(0, max(1, end)) + "..."
    }

    private fun thresholdBlackWhite(source: Bitmap): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        for (y in 0 until output.height) {
            for (x in 0 until output.width) {
                output.setPixel(x, y, if (isBlack(output.getPixel(x, y))) Color.BLACK else Color.WHITE)
            }
        }
        return output
    }

    private fun isBlack(pixel: Int): Boolean {
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)
        return (red + green + blue) / 3 < 160
    }

    private fun qrPayload(ownerName: String, uid: Long): String =
        JSONObject()
            .put("owner", ownerName)
            .put("uid", uid)
            .toString()

    private fun productionYearsLabel(start: Int?, end: Int?): String? =
        when {
            start != null && end != null && start == end -> start.toString()
            start != null && end != null -> "$start-$end"
            start != null -> "$start-"
            end != null -> "-$end"
            else -> null
        }
}
