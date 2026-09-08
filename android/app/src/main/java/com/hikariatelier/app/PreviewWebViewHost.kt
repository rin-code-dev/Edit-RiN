package com.hikariatelier.app

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView

/** Native child transforms keep Android touch dispatch and the displayed viewport aligned. */
internal class PreviewWebViewHost(context: Context, val preview: WebView) : ViewGroup(context) {
    private var logicalWidth: Int? = null
    private var logicalHeight: Int? = null

    init {
        addView(preview)
        clipChildren = true
    }

    fun setLogicalSize(width: Int?, height: Int?) {
        val valid = width != null && height != null && width > 0 && height > 0
        val nextWidth = width.takeIf { valid }
        val nextHeight = height.takeIf { valid }
        if (logicalWidth == nextWidth && logicalHeight == nextHeight) return
        logicalWidth = nextWidth
        logicalHeight = nextHeight
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        preview.measure(
            MeasureSpec.makeMeasureSpec(logicalWidth ?: width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(logicalHeight ?: height, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val childWidth = preview.measuredWidth.coerceAtLeast(1)
        val childHeight = preview.measuredHeight.coerceAtLeast(1)
        val scale = minOf(width.toFloat() / childWidth, height.toFloat() / childHeight)
        preview.layout(0, 0, childWidth, childHeight)
        preview.pivotX = 0f
        preview.pivotY = 0f
        preview.scaleX = scale
        preview.scaleY = scale
        preview.translationX = (width - childWidth * scale) / 2f
        preview.translationY = (height - childHeight * scale) / 2f
    }
}
