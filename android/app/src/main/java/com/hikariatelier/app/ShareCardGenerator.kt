package com.hikariatelier.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.zip.Deflater

internal enum class QrStatus {
    AVAILABLE,
    CONTAINS_ASSETS,
    TOO_LARGE
}

internal data class ShareCardConfig(
    val title: String,
    val fullCode: String,
    val includeCode: Boolean = true,
    val snippetCode: String = "",
    val snippetStartLine: Int = 1,
    val includeQr: Boolean = true,
    val qrStatus: QrStatus = QrStatus.AVAILABLE
)

internal object ShareCardGenerator {

    private const val WEB_VIEWER_BASE_URL = "https://rin-code-dev.github.io/Edit-RiN/share/#c="

    /**
     * Checks if a QR code can be generated for this sketch.
     */
    fun checkQrStatus(code: String, hasAssets: Boolean): QrStatus {
        if (hasAssets) return QrStatus.CONTAINS_ASSETS
        return try {
            val compressed = compressCodeForUrl(code)
            val url = WEB_VIEWER_BASE_URL + compressed
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L
            )
            QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 200, 200, hints)
            QrStatus.AVAILABLE
        } catch (_: Exception) {
            QrStatus.TOO_LARGE
        }
    }

    /**
     * Compresses JavaScript source code using zlib Deflate (RFC 1951) and encodes with URL-safe Base64.
     */
    fun compressCodeForUrl(code: String): String {
        val input = code.toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(input)
        deflater.finish()

        val output = ByteArray(input.size + 256)
        val compressedSize = deflater.deflate(output)
        deflater.end()

        val trimmed = output.copyOf(compressedSize)
        return Base64.encodeToString(trimmed, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Generates a QR code bitmap for the given URL.
     */
    fun generateQrBitmap(url: String, size: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L
        )
        val bitMatrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    /**
     * Creates a rounded corner square bitmap center-cropped from an existing bitmap.
     */
    private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Float, targetWidth: Int, targetHeight: Int): Bitmap {
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dstRect = Rect(0, 0, targetWidth, targetHeight)
        val rectF = RectF(dstRect)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)

        val srcW = bitmap.width
        val srcH = bitmap.height
        val minDim = minOf(srcW, srcH)
        val srcLeft = (srcW - minDim) / 2
        val srcTop = (srcH - minDim) / 2
        val srcRect = Rect(srcLeft, srcTop, srcLeft + minDim, srcTop + minDim)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)

        return output
    }

    /**
     * Renders a customizable 16:9 share card (1280x720).
     */
    fun renderShareCard(
        artwork: Bitmap,
        config: ShareCardConfig
    ): Bitmap {
        val width = 1280
        val height = 720
        val card = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(card)

        // 1. Background (Edit:RiN dark theme #101014)
        val bgPaint = Paint().apply {
            color = Color.parseColor("#101014")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Artwork (Left area: 580x580 at x=50, y=70)
        val artSize = 580
        val artX = 50f
        val artY = 70f
        val roundedArt = getRoundedCornerBitmap(artwork, 24f, artSize, artSize)
        canvas.drawBitmap(roundedArt, artX, artY, null)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#262833")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(artX, artY, artX + artSize, artY + artSize, 24f, 24f, borderPaint)

        // 3. Right Area Layout
        val rightX = 660f
        val contentWidth = width - rightX - 50f

        // Title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val safeTitle = config.title.trim().ifBlank { "Untitled" }
        val displayTitle = TextUtils.ellipsize(
            safeTitle,
            TextPaint(titlePaint),
            contentWidth,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(displayTitle, rightX, 110f, titlePaint)

        // Subtitle / Credit
        val creditPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8E919A")
            textSize = 19f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        canvas.drawText("Created with Edit:RiN", rightX, 142f, creditPaint)

        // Divider
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#262833")
            strokeWidth = 1.5f
        }
        canvas.drawLine(rightX, 162f, rightX + contentWidth, 162f, dividerPaint)

        val hasQr = config.includeQr && config.qrStatus == QrStatus.AVAILABLE
        val hasCode = config.includeCode && config.snippetCode.isNotBlank()

        if (hasCode && hasQr) {
            // Layout A: Both Code and QR
            val qrCardSize = 170f
            val qrX = rightX + contentWidth - qrCardSize
            val qrY = 720f - 70f - qrCardSize

            // Code Box above QR
            val codeBoxH = qrY - 180f - 16f
            drawCodeBox(canvas, rightX, 180f, contentWidth, codeBoxH, config.snippetCode, config.snippetStartLine)

            // QR Area at Bottom Right
            drawQrCard(canvas, qrX, qrY, qrCardSize, config.fullCode)

            // Info beside QR
            val hintTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 21f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("Scan to run sketch", rightX, qrY + 45f, hintTitlePaint)

            val hintSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8E919A")
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("Runs in browser with p5.js", rightX, qrY + 76f, hintSubPaint)

            val urlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#5A5E70")
                textSize = 14f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }
            canvas.drawText("rin-code-dev.github.io/Edit-RiN", rightX, qrY + 104f, urlPaint)

        } else if (hasCode) {
            // Layout B: Code only (Full height)
            val codeBoxH = (720f - 70f) - 180f
            drawCodeBox(canvas, rightX, 180f, contentWidth, codeBoxH, config.snippetCode, config.snippetStartLine)

        } else if (hasQr) {
            // Layout C: QR only (Minimal spacious layout)
            val qrCardSize = 250f
            val qrPadding = 14f
            val qrX = rightX
            val qrY = 240f

            drawQrCard(canvas, qrX, qrY, qrCardSize, config.fullCode, qrPadding)

            val hintTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("Scan to run sketch", qrX + qrCardSize + 30f, qrY + 80f, hintTitlePaint)

            val hintSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8E919A")
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("Runs live in browser with p5.js", qrX + qrCardSize + 30f, qrY + 115f, hintSubPaint)

        } else {
            // Layout D: Ultra-minimal
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#636675")
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("Creative Coding with p5.js", rightX, 260f, emptyPaint)
        }

        return card
    }

    private fun drawQrCard(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        code: String,
        padding: Float = 10f
    ) {
        val qrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(x, y, x + size, y + size, 18f, 18f, qrBgPaint)

        val qrUrl = WEB_VIEWER_BASE_URL + compressCodeForUrl(code)
        val rawQrSize = (size - padding * 2).toInt()
        val qrBitmap = generateQrBitmap(qrUrl, rawQrSize)
        canvas.drawBitmap(qrBitmap, x + padding, y + padding, null)
    }

    private fun drawCodeBox(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        snippet: String,
        startLine: Int
    ) {
        // 1. Box background & border
        val boxBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#14141B")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(x, y, x + w, y + h, 16f, 16f, boxBg)

        val boxBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#262833")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(x, y, x + w, y + h, 16f, 16f, boxBorder)

        // 2. Header bar with window dots
        val headerH = 34f
        val dotRadius = 4f
        val dotY = y + 17f

        val dotColors = listOf("#FF5F56", "#FFBD2E", "#27C93F")
        dotColors.forEachIndexed { i, hex ->
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(hex)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(x + 18f + (i * 14f), dotY, dotRadius, dotPaint)
        }

        val tabTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8E919A")
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        canvas.drawText("sketch.js", x + 68f, dotY + 5f, tabTitlePaint)

        val lines = snippet.lines()
        val endLine = startLine + lines.size - 1
        val rangeText = if (lines.size > 1) "lines $startLine–$endLine" else "line $startLine"
        val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5A5E70")
            textSize = 13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val rangeW = rangePaint.measureText(rangeText)
        canvas.drawText(rangeText, x + w - rangeW - 16f, dotY + 5f, rangePaint)

        // Header divider
        val hDividerPaint = Paint().apply {
            color = Color.parseColor("#1F212B")
            strokeWidth = 1f
        }
        canvas.drawLine(x, y + headerH, x + w, y + headerH, hDividerPaint)

        // 3. Code Lines
        val lineH = 25f
        val contentTop = y + headerH + 20f
        val maxLines = ((h - headerH - 24f) / lineH).toInt().coerceAtLeast(1)
        val visibleLines = lines.take(maxLines)

        val lineNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#494C5C")
            textSize = 16f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D4D7E2")
            textSize = 16f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val textPaint = TextPaint(codePaint)
        val maxCodeW = w - 75f

        visibleLines.forEachIndexed { i, line ->
            val curLineY = contentTop + (i * lineH)
            val lineNum = (startLine + i).toString()
            val numW = lineNumPaint.measureText(lineNum)
            canvas.drawText(lineNum, x + 44f - numW, curLineY, lineNumPaint)

            val displayLine = TextUtils.ellipsize(
                line,
                textPaint,
                maxCodeW,
                TextUtils.TruncateAt.END
            ).toString()
            canvas.drawText(displayLine, x + 56f, curLineY, codePaint)
        }
    }
}
