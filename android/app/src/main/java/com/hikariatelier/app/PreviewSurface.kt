package com.hikariatelier.app

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun PreviewSurface(
    preview: PreviewController,
    isError: Boolean,
    fullscreen: Boolean,
    logicalSize: IntSize?,
    modifier: Modifier,
    onClearEditorFocus: () -> Unit,
    controls: @Composable BoxWithConstraintsScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = if (fullscreen) modifier else modifier.border(1.dp,
            if (isError) colors.error else colors.outlineVariant, RoundedCornerShape(18.dp)),
        shape = if (fullscreen) RectangleShape else RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            PreviewWebView(preview, logicalSize, onClearEditorFocus)
            var showLoading by remember { mutableStateOf(false) }
            LaunchedEffect(preview.isLoading) {
                showLoading = false
                if (preview.isLoading) { delay(150); showLoading = true }
            }
            if (preview.isLoading) {
                preview.loadingThumbnail?.let {
                    Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                if (showLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            controls()
        }
    }
}

@Composable
internal fun PreviewWebView(preview: PreviewController, logicalSize: IntSize?, onClearEditorFocus: () -> Unit) {
    key(preview.generation) { AndroidView(
        factory = { context -> preview.createHost(context, onClearEditorFocus) },
        modifier = Modifier.fillMaxSize(),
        update = { host -> host.setLogicalSize(logicalSize?.width, logicalSize?.height) },
        onRelease = { host -> if (preview.webView === host.preview) host.preview.onPause() }
    ) }
}
