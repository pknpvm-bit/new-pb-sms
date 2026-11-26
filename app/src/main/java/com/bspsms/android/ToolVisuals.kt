package com.bspsms.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Two-colour gradient that gives every tool its own recognisable identity. */
data class ToolPalette(val start: Color, val end: Color)

val HubPalette = ToolPalette(Color(0xFF1E5FA8), Color(0xFF123B70))

fun ToolDestination.palette(): ToolPalette = when (this) {
    ToolDestination.FORMATIVE -> ToolPalette(Color(0xFF3B82F6), Color(0xFF1D4ED8))
    ToolDestination.TALK -> ToolPalette(Color(0xFF22D3EE), Color(0xFF0E7490))
    ToolDestination.LPCD -> ToolPalette(Color(0xFFFBBF24), Color(0xFFD97706))
    ToolDestination.BCO -> ToolPalette(Color(0xFFA78BFA), Color(0xFF6D28D9))
    ToolDestination.EXCEL -> ToolPalette(Color(0xFF4ADE80), Color(0xFF15803D))
    ToolDestination.BULK_MARKSHEET -> ToolPalette(Color(0xFFF87171), Color(0xFFB91C1C))
    ToolDestination.RESULT_READY -> ToolPalette(Color(0xFF818CF8), Color(0xFF4338CA))
    ToolDestination.HPRC_SCAN -> ToolPalette(Color(0xFFF472B6), Color(0xFFBE185D))
}

/** Rounded gradient tile with a white glyph and a soft highlight: the tool "logo". */
@Composable
fun GradientLogo(
    icon: ImageVector,
    palette: ToolPalette,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    enabled: Boolean = true
) {
    val brush = if (enabled) {
        Brush.linearGradient(listOf(palette.start, palette.end))
    } else {
        Brush.linearGradient(listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8)))
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.30f), Color.Transparent)
                    )
                )
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}

@Composable
fun ToolLogo(
    tool: ToolDestination,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    enabled: Boolean = true
) {
    GradientLogo(
        icon = tool.icon,
        palette = tool.palette(),
        modifier = modifier,
        size = size,
        enabled = enabled
    )
}
