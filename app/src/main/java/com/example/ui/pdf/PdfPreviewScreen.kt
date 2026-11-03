package com.example.ui.pdf

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.reports.RText
import com.example.ui.reports.RptPalette
import com.example.ui.reports.pressClickable
import com.example.ui.viewmodel.PdfNotice
import com.example.ui.viewmodel.PdfUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders the pages of the generated PDF file with [PdfRenderer]. This is the real file that is
 * saved and shared, not a Compose imitation. PdfRenderer allows one open page at a time, so
 * renders are serialised; the renderer and file descriptor are closed on dispose.
 */
private class PdfPagesHolder(file: File) : AutoCloseable {
    private val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = try { PdfRenderer(pfd) } catch (e: Throwable) { pfd.close(); throw e }
    private val lock = Mutex()
    val pageCount: Int = renderer.pageCount

    suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
        withContext(Dispatchers.IO) {
            val page = renderer.openPage(index)
            try {
                val h = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(AColor.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            } finally {
                page.close()
            }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

@Composable
fun PdfPreviewScreen(
    p: RptPalette,
    state: PdfUiState.Preview,
    notice: PdfNotice?,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onDismissNotice: () -> Unit,
    reduced: Boolean = false
) {
    val holder = remember(state.file) { runCatching { PdfPagesHolder(state.file) } }
    DisposableEffect(state.file) { onDispose { holder.getOrNull()?.close() } }
    var zoomed by remember { mutableStateOf(false) }
    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()
    // Pages that have already played their entrance, so scrolling back never replays it.
    val seenPages = remember(state.file) { mutableSetOf<Int>() }
    var showBurst by remember(state.file) { mutableStateOf(true) }
    LaunchedEffect(state.file) {
        delay(if (reduced) 600 else 1500)
        showBurst = false
    }
    // Success notices fade away by themselves; errors stay until tapped.
    var lastNotice by remember { mutableStateOf<PdfNotice?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            lastNotice = notice
            if (!notice.isError) {
                delay(3500)
                onDismissNotice()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(horizontal = 16.dp).staggerIn(0, reduced, state.file),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    RText(state.fileName, 12f, p.textSec, maxLines = 1)
                    RText("${state.pageCount} page${if (state.pageCount == 1) "" else "s"} · A4", 11f, p.textMuted)
                }
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(p.trackBg)
                        .pressClickable { zoomed = !zoomed }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (zoomed) Icons.Default.ZoomOut else Icons.Default.ZoomIn, null, tint = p.text, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    AnimatedContent(
                        targetState = zoomed,
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                        label = "zoomLabel"
                    ) { z -> RText(if (z) "Fit page" else "Zoom", 12f, p.text, weight = FontWeight.SemiBold) }
                }
            }
            Spacer(Modifier.height(8.dp))

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                val baseW = maxWidth - 32.dp
                val targetW = if (zoomed) baseW * 1.8f else baseW
                // Render at the final width; only the on-screen width animates (no re-render per frame).
                val widthPx = with(density) { targetW.roundToPx() }.coerceIn(200, 2200)
                val pageW by animateDpAsState(
                    targetW,
                    if (reduced) tween(0) else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                    label = "pageW"
                )
                val h = holder.getOrNull()
                if (h == null) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        RText("The preview couldn't be opened, but the PDF was created. You can still save or share it.", 13f, p.textSec)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 56.dp)
                    ) {
                        items((0 until h.pageCount).toList()) { index ->
                            val bmp by produceState<Bitmap?>(null, h, index, widthPx) {
                                value = runCatching { h.render(index, widthPx) }.getOrNull()
                            }
                            val already = remember(index) { index in seenPages }
                            LaunchedEffect(index) { seenPages.add(index) }
                            val imgAlpha by animateFloatAsState(
                                if (bmp != null) 1f else 0f, tween(if (reduced) 0 else 420), label = "pageFade"
                            )
                            Box(
                                Modifier.fillMaxWidth()
                                    .then(if (already) Modifier else Modifier.staggerIn(index + 1, reduced, state.file))
                                    .then(if (zoomed) Modifier.horizontalScroll(hScroll) else Modifier),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Box(
                                    Modifier.then(if (zoomed) Modifier.padding(horizontal = 16.dp) else Modifier)
                                        .width(pageW).aspectRatio(595f / 842f)
                                        .shadow(8.dp, RoundedCornerShape(4.dp))
                                        .background(Color.White)
                                        .border(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    // Skeleton while the page renders, then the real page fades in.
                                    Box(Modifier.fillMaxSize().shimmer(Color(0xFFF1F5F9), Color.White, reduced))
                                    val b = bmp
                                    if (b != null) {
                                        Image(
                                            b.asImageBitmap(),
                                            contentDescription = "Page ${index + 1} of ${h.pageCount}",
                                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = imgAlpha }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Floating page indicator.
                    val current by remember { derivedStateOf { listState.firstVisibleItemIndex } }
                    if (h.pageCount > 1) {
                        Box(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(50)).background(p.card)
                                .border(1.dp, p.border, RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            AnimatedContent(
                                targetState = current,
                                transitionSpec = {
                                    val up = targetState > initialState
                                    (fadeIn(tween(160)) + slideInVertically(tween(200)) { if (up) it else -it }) togetherWith
                                        (fadeOut(tween(120)) + slideOutVertically(tween(160)) { if (up) -it else it })
                                },
                                label = "pageIndicator"
                            ) { i -> RText("Page ${i + 1} of ${h.pageCount}", 11.5f, p.text, weight = FontWeight.SemiBold, tnum = true) }
                        }
                    }
                }
            }

            // Real outcome of the last save/share attempt (never claims success that didn't happen).
            AnimatedVisibility(
                visible = notice != null,
                enter = fadeIn(tween(220)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(220)) { it / 2 }
            ) {
                val n = notice ?: lastNotice
                if (n != null) {
                    val tone = if (n.isError) p.rose else p.emerald
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp)).background(tone.copy(alpha = 0.14f))
                            .border(1.dp, tone.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .pressClickable { onDismissNotice() }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (n.isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, null, tint = tone, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        RText(n.text, 12f, tone, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.staggerIn(2, reduced, state.file), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PdfButton(p, "Download PDF", Modifier.weight(1f), icon = Icons.Default.Save, onClick = onSave)
                    PdfButton(p, "Share", Modifier.weight(1f), icon = Icons.Default.Share, filled = false, onClick = onShare)
                }
                Row(Modifier.staggerIn(3, reduced, state.file), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PdfButton(p, "Change options", Modifier.weight(1f), filled = false, onClick = onEdit)
                    PdfButton(p, "Back to Reports", Modifier.weight(1f), filled = false, onClick = onClose)
                }
            }
        }

        // One-shot "report ready" moment; tap to skip.
        AnimatedVisibility(
            visible = showBurst,
            enter = fadeIn(tween(if (reduced) 0 else 200)),
            exit = fadeOut(tween(if (reduced) 0 else 420)) + scaleOut(tween(420), targetScale = 1.08f)
        ) {
            Box(
                Modifier.fillMaxSize().background(p.bg.copy(alpha = 0.88f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showBurst = false },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SuccessBurst(p, reduced)
                    RText("Report ready", 20f, p.text, weight = FontWeight.Bold, display = true)
                    RText("${state.pageCount} page${if (state.pageCount == 1) "" else "s"} · tap to continue", 12f, p.textSec)
                }
            }
        }
    }
}
