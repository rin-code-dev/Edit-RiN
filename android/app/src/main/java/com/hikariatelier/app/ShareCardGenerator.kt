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

internal enum class ShareCardTheme(
    val id: String,
    val bgColor: String,
    val artBorderColor: String,
    val titleColor: String,
    val subtitleColor: String,
    val dividerColor: String,
    val codeBoxBg: String,
    val codeBoxBorder: String,
    val codeTextColor: String,
    val codeLineNumColor: String,
    val urlColor: String
) {
    DARK(
        id = "dark",
        bgColor = "#101014",
        artBorderColor = "#262833",
        titleColor = "#FFFFFF",
        subtitleColor = "#8E919A",
        dividerColor = "#262833",
        codeBoxBg = "#14141B",
        codeBoxBorder = "#262833",
        codeTextColor = "#D4D7E2",
        codeLineNumColor = "#494C5C",
        urlColor = "#5A5E70"
    ),
    MIDNIGHT(
        id = "midnight",
        bgColor = "#0A0E1A",
        artBorderColor = "#1E283D",
        titleColor = "#FFFFFF",
        subtitleColor = "#7E8B9F",
        dividerColor = "#1E283D",
        codeBoxBg = "#0F1626",
        codeBoxBorder = "#1E283D",
        codeTextColor = "#D6E0F0",
        codeLineNumColor = "#43526E",
        urlColor = "#50617F"
    ),
    CYBER(
        id = "cyber",
        bgColor = "#130D22",
        artBorderColor = "#311F54",
        titleColor = "#FFFFFF",
        subtitleColor = "#A685D4",
        dividerColor = "#311F54",
        codeBoxBg = "#1A122E",
        codeBoxBorder = "#311F54",
        codeTextColor = "#E5DAF7",
        codeLineNumColor = "#624B82",
        urlColor = "#795B9E"
    ),
    LIGHT(
        id = "light",
        bgColor = "#F3F4F8",
        artBorderColor = "#D5D8E2",
        titleColor = "#12151F",
        subtitleColor = "#606677",
        dividerColor = "#D5D8E2",
        codeBoxBg = "#FFFFFF",
        codeBoxBorder = "#D5D8E2",
        codeTextColor = "#1E2333",
        codeLineNumColor = "#9FA5B5",
        urlColor = "#7E8496"
    )
}

internal data class ShareCardConfig(
    val title: String,
    val author: String = "",
    val fullCode: String,
    val theme: ShareCardTheme = ShareCardTheme.DARK,
    val includeCode: Boolean = true,
    val snippetCode: String = "",
    val snippetStartLine: Int = 1,
    val includeQr: Boolean = true,
    val qrStatus: QrStatus = QrStatus.AVAILABLE,
    val allowWebCodeView: Boolean = true
)

internal object ShareCardGenerator {

    private const val WEB_VIEWER_BASE_URL = "https://rin-code-dev.github.io/Edit-RiN/share/#c="

    /**
     * Cleans code of non-functional overhead (comments, extra empty lines, sourceURL)
     * to minimize data payload and maximize QR module size, preserving // @rin declarations.
     */
    private fun cleanCodeForQr(code: String): String {
        return code
            .replace(Regex("""//#\s*sourceURL=[^\r\n]*"""), "")
            .lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() && (!line.startsWith("//") || line.startsWith("// @rin"))
            }
            .joinToString("\n")
    }

    /**
     * Checks if a QR code can be generated for this sketch.
     */
    fun checkQrStatus(code: String, hasAssets: Boolean, allowWebCodeView: Boolean = true): QrStatus {
        if (hasAssets) return QrStatus.CONTAINS_ASSETS
        val cleaned = cleanCodeForQr(code)
        return try {
            val compressed = compressCodeForUrl(cleaned)
            val suffix = if (!allowWebCodeView) "&src=0" else ""
            val url = WEB_VIEWER_BASE_URL + compressed + suffix
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
     * Generates a high-contrast QR code bitmap using ErrorCorrectionLevel.L to maximize module size.
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
     * Renders a high-resolution 1920x1080 (16:9 Full HD) share card with themes and author credit.
     */
    fun renderShareCard(
        artwork: Bitmap,
        config: ShareCardConfig
    ): Bitmap {
        val width = 1920
        val height = 1080
        val card = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(card)
        val theme = config.theme

        // 1. Background
        val bgPaint = Paint().apply {
            color = Color.parseColor(theme.bgColor)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Artwork (Left area: 880x880 at x=80, y=100)
        val artSize = 880
        val artX = 80f
        val artY = 100f
        val roundedArt = getRoundedCornerBitmap(artwork, 32f, artSize, artSize)
        canvas.drawBitmap(roundedArt, artX, artY, null)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.artBorderColor)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(artX, artY, artX + artSize, artY + artSize, 32f, 32f, borderPaint)

        // 3. Right Area Layout
        val rightX = 1020f
        val contentWidth = width - rightX - 80f

        // Title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.titleColor)
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val safeTitle = config.title.trim().ifBlank { "Untitled" }
        val displayTitle = TextUtils.ellipsize(
            safeTitle,
            TextPaint(titlePaint),
            contentWidth,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(displayTitle, rightX, 160f, titlePaint)

        // Subtitle / Author Credit
        val creditPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.subtitleColor)
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val creditText = if (config.author.isNotBlank()) {
            val clean = config.author.trim()
            val handle = if (clean.startsWith("@")) clean else "@$clean"
            "by $handle  ·  Created with Edit:RiN"
        } else {
            "Created with Edit:RiN"
        }
        val displayCredit = TextUtils.ellipsize(
            creditText,
            TextPaint(creditPaint),
            contentWidth,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(displayCredit, rightX, 206f, creditPaint)

        // Divider
        val dividerPaint = Paint().apply {
            color = Color.parseColor(theme.dividerColor)
            strokeWidth = 2f
        }
        canvas.drawLine(rightX, 236f, rightX + contentWidth, 236f, dividerPaint)

        val hasQr = config.includeQr && config.qrStatus == QrStatus.AVAILABLE
        val hasCode = config.includeCode && config.snippetCode.isNotBlank()
        val cleanedQrCode = cleanCodeForQr(config.fullCode)

        if (hasCode && hasQr) {
            // Layout A: Both Code and QR (Expanded 390x390 QR)
            val qrCardSize = 390f
            val qrPadding = 22f
            val qrX = rightX + contentWidth - qrCardSize
            val qrY = height - 50f - qrCardSize

            // Code Box above QR
            val codeBoxH = qrY - 260f - 20f
            drawCodeBox(canvas, rightX, 260f, contentWidth, codeBoxH, config.snippetCode, config.snippetStartLine, theme)

            // QR Card at Bottom Right
            drawQrCard(canvas, qrX, qrY, qrCardSize, cleanedQrCode, padding = qrPadding, allowWebCodeView = config.allowWebCodeView)

            // Scan Info to the left of the QR
            val hintTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(theme.titleColor)
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("Scan to run sketch", rightX, qrY + 120f, hintTitlePaint)

            val hintSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(theme.subtitleColor)
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("Runs in browser with p5.js", rightX, qrY + 165f, hintSubPaint)

            val urlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(theme.urlColor)
                textSize = 19f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }
            canvas.drawText("rin-code-dev.github.io/Edit-RiN", rightX, qrY + 205f, urlPaint)

        } else if (hasCode) {
            // Layout B: Code only (Full height)
            val codeBoxH = (height - 100f) - 260f
            drawCodeBox(canvas, rightX, 260f, contentWidth, codeBoxH, config.snippetCode, config.snippetStartLine, theme)

        } else if (hasQr) {
            // Layout C: QR only (Extra large 380x380 QR)
            val qrCardSize = 380f
            val qrPadding = 24f
            val qrX = rightX
            val qrY = 320f

            drawQrCard(canvas, qrX, qrY, qrCardSize, cleanedQrCode, qrPadding, allowWebCodeView = config.allowWebCodeView)

            val hintTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(theme.titleColor)
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("Scan to run sketch", qrX + qrCardSize + 40f, qrY + 120f, hintTitlePaint)

            val hintSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(theme.subtitleColor)
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("Runs live in browser with p5.js", qrX + qrCardSize + 40f, qrY + 175f, hintSubPaint)

        } else {
            // Layout D: Minimal
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(theme.subtitleColor)
                textSize = 28f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }
            canvas.drawText("Creative Coding with p5.js", rightX, 360f, emptyPaint)
        }

        return card
    }

    private fun drawQrCard(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        code: String,
        padding: Float = 22f,
        allowWebCodeView: Boolean = true
    ) {
        val qrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(x, y, x + size, y + size, 28f, 28f, qrBgPaint)

        val suffix = if (!allowWebCodeView) "&src=0" else ""
        val qrUrl = WEB_VIEWER_BASE_URL + compressCodeForUrl(code) + suffix
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
        startLine: Int,
        theme: ShareCardTheme
    ) {
        // 1. Box background & border
        val boxBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.codeBoxBg)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(x, y, x + w, y + h, 20f, 20f, boxBg)

        val boxBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.codeBoxBorder)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(x, y, x + w, y + h, 20f, 20f, boxBorder)

        // 2. Header bar with window dots
        val headerH = 46f
        val dotRadius = 6f
        val dotY = y + 23f

        val dotColors = listOf("#FF5F56", "#FFBD2E", "#27C93F")
        dotColors.forEachIndexed { i, hex ->
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(hex)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(x + 24f + (i * 20f), dotY, dotRadius, dotPaint)
        }

        val tabTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.subtitleColor)
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        canvas.drawText("sketch.js", x + 96f, dotY + 7f, tabTitlePaint)

        val lines = snippet.lines()
        val endLine = startLine + lines.size - 1
        val rangeText = if (lines.size > 1) "lines $startLine–$endLine" else "line $startLine"
        val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.urlColor)
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val rangeW = rangePaint.measureText(rangeText)
        canvas.drawText(rangeText, x + w - rangeW - 24f, dotY + 7f, rangePaint)

        // Header divider
        val hDividerPaint = Paint().apply {
            color = Color.parseColor(theme.codeBoxBorder)
            strokeWidth = 1.5f
        }
        canvas.drawLine(x, y + headerH, x + w, y + headerH, hDividerPaint)

        // 3. Code Lines
        val lineH = 34f
        val contentTop = y + headerH + 28f
        val maxLines = ((h - headerH - 32f) / lineH).toInt().coerceAtLeast(1)
        val visibleLines = lines.take(maxLines)

        val lineNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.codeLineNumColor)
            textSize = 21f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(theme.codeTextColor)
            textSize = 21f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val textPaint = TextPaint(codePaint)
        val maxCodeW = w - 100f

        visibleLines.forEachIndexed { i, line ->
            val curLineY = contentTop + (i * lineH)
            val lineNum = (startLine + i).toString()
            val numW = lineNumPaint.measureText(lineNum)
            canvas.drawText(lineNum, x + 58f - numW, curLineY, lineNumPaint)

            val displayLine = TextUtils.ellipsize(
                line,
                textPaint,
                maxCodeW,
                TextUtils.TruncateAt.END
            ).toString()
            canvas.drawText(displayLine, x + 74f, curLineY, codePaint)
        }
    }
}
