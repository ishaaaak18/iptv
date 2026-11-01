package io.github.ishaaaak18.iptv.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Accent = Color(0xFF00C2FF)
val Accent2 = Color(0xFFFF8C00)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    secondary = Accent2,
    background = Color(0xFF0B0F1C),
    surface = Color(0xFF151B2C),
    surfaceVariant = Color(0xFF1E2538),
    onBackground = Color(0xFFF0F4FF),
    onSurface = Color(0xFFF0F4FF),
    outline = Color(0xFF2A3144),
)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

/** Clickable with a visible focus ring, for D-pad / remote navigation on TV. */
fun Modifier.tvClickable(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    this
        .border(
            BorderStroke(if (focused) 3.dp else 0.dp, if (focused) Accent else Color.Transparent),
            RoundedCornerShape(12.dp),
        )
        .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
}
