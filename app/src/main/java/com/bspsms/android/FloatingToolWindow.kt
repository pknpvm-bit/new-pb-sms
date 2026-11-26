package com.bspsms.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Geometry + mode of one floating tool window. Every tool owns its own instance, so each tool
 * "remembers" where the user put it, how big it is, and whether it is minimized to a bubble.
 * All values are in pixels.
 */
@Stable
class FloatingWindowState {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(0f)
    var height by mutableFloatStateOf(0f)
    var bubbleX by mutableFloatStateOf(-1f)
    var bubbleY by mutableFloatStateOf(-1f)
    var minimized by mutableStateOf(false)
    var maximized by mutableStateOf(false)
    var initialized by mutableStateOf(false)
}

/**
 * A floating window drawn above the portal. The portal stays visible and usable around it.
 * The content is ALWAYS kept composed (even when minimized) so a running tool is never torn down.
 *
 * Must be placed inside a parent that fills the area the window may move in.
 */
@Composable
fun FloatingToolWindow(
    state: FloatingWindowState,
    title: String,
    subtitle: String,
    icon: ImageVector,
    palette: ToolPalette,
    onClose: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val parentW = with(density) { maxWidth.toPx() }
        val parentH = with(density) { maxHeight.toPx() }
        val margin = with(density) { 8.dp.toPx() }
        val bubble = with(density) { 58.dp.toPx() }
        val minW = with(density) { 300.dp.toPx() }.coerceAtMost((parentW - 2f * margin).coerceAtLeast(1f))
        val minH = with(density) { 280.dp.toPx() }.coerceAtMost((parentH - 2f * margin).coerceAtLeast(1f))
        val maxW = (parentW - 2f * margin).coerceAtLeast(minW)
        val maxH = (parentH - 2f * margin).coerceAtLeast(minH)

        // First open: a comfortable window in the lower part of the screen, portal still visible above.
        LaunchedEffect(parentW, parentH) {
            if (!state.initialized && parentW > 0f && parentH > 0f) {
                val w = (parentW * 0.94f).coerceIn(minW, maxW).coerceAtMost(with(density) { 460.dp.toPx() }.coerceAtLeast(minW))
                val h = (parentH * 0.72f).coerceIn(minH, maxH)
                state.width = w
                state.height = h
                state.x = ((parentW - w) / 2f).coerceAtLeast(0f)
                state.y = (parentH - h - margin).coerceAtLeast(0f)
                state.initialized = true
            }
        }

        if (state.initialized) {
            val full = state.maximized
            val w = if (full) maxW else state.width.coerceIn(minW, maxW)
            val h = if (full) maxH else state.height.coerceIn(minH, maxH)
            val x = if (full) margin else state.x.coerceIn(0f, (parentW - w).coerceAtLeast(0f))
            val y = if (full) margin else state.y.coerceIn(0f, (parentH - h).coerceAtLeast(0f))

            // ---- The window itself (moved far off-screen while minimized so the tool keeps running) ----
            Surface(
                modifier = Modifier
                    .offset { IntOffset(if (state.minimized) -100000 else x.roundToInt(), y.roundToInt()) }
                    .size(width = with(density) { w.toDp() }, height = with(density) { h.toDp() }),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 20.dp,
                border = BorderStroke(1.dp, Color(0xFFD5E0EF))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Title bar: drag handle + window buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(Brush.horizontalGradient(listOf(palette.end, palette.start)))
                                .pointerInput(parentW, parentH, minW, maxW) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        if (!state.maximized) {
                                            val cw = state.width.coerceIn(minW, maxW)
                                            val ch = state.height.coerceIn(minH, maxH)
                                            state.x = (state.x + drag.x).coerceIn(0f, (parentW - cw).coerceAtLeast(0f))
                                            state.y = (state.y + drag.y).coerceIn(0f, (parentH - ch).coerceAtLeast(0f))
                                        }
                                    }
                                }
                                .padding(start = 10.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GradientLogo(
                                icon = icon,
                                palette = ToolPalette(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.12f)),
                                size = 34.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        color = Color.White.copy(alpha = 0.82f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (onBack != null) {
                                WindowButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back to tools") { onBack() }
                            }
                            WindowButton(Icons.Outlined.Remove, "Minimize") { state.minimized = true }
                            WindowButton(
                                if (state.maximized) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                if (state.maximized) "Restore size" else "Maximize"
                            ) { state.maximized = !state.maximized }
                            WindowButton(Icons.Outlined.Close, "Close") { onClose() }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC))
                        ) {
                            content()
                        }
                    }

                    // Resize grip (bottom-right corner)
                    if (!state.maximized) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                                .pointerInput(parentW, parentH, minW, maxW) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        val cx = state.x.coerceIn(0f, (parentW - minW).coerceAtLeast(0f))
                                        val cy = state.y.coerceIn(0f, (parentH - minH).coerceAtLeast(0f))
                                        state.width = (state.width.coerceIn(minW, maxW) + drag.x)
                                            .coerceIn(minW, (parentW - cx).coerceAtLeast(minW))
                                        state.height = (state.height.coerceIn(minH, maxH) + drag.y)
                                            .coerceIn(minH, (parentH - cy).coerceAtLeast(minH))
                                    }
                                },
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Canvas(modifier = Modifier.padding(8.dp).size(14.dp)) {
                                val c = Color(0xFF64748B).copy(alpha = 0.75f)
                                drawLine(c, Offset(size.width, size.height * 0.35f), Offset(size.width * 0.35f, size.height), 3f, StrokeCap.Round)
                                drawLine(c, Offset(size.width, size.height * 0.75f), Offset(size.width * 0.75f, size.height), 3f, StrokeCap.Round)
                            }
                        }
                    }
                }
            }

            // ---- Minimized bubble: draggable, tap to restore ----
            if (state.minimized) {
                val bx = if (state.bubbleX < 0f) parentW - bubble - margin else state.bubbleX
                val by = if (state.bubbleY < 0f) parentH * 0.55f else state.bubbleY
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                bx.coerceIn(0f, (parentW - bubble).coerceAtLeast(0f)).roundToInt(),
                                by.coerceIn(0f, (parentH - bubble).coerceAtLeast(0f)).roundToInt()
                            )
                        }
                        .size(58.dp)
                        .shadow(12.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(palette.start, palette.end)))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { state.minimized = false })
                        }
                        .pointerInput(parentW, parentH) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val curX = if (state.bubbleX < 0f) parentW - bubble - margin else state.bubbleX
                                val curY = if (state.bubbleY < 0f) parentH * 0.55f else state.bubbleY
                                state.bubbleX = (curX + drag.x).coerceIn(0f, (parentW - bubble).coerceAtLeast(0f))
                                state.bubbleY = (curY + drag.y).coerceIn(0f, (parentH - bubble).coerceAtLeast(0f))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Restore $title",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WindowButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(19.dp)
        )
    }
}
