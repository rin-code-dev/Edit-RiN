package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hikariatelier.app.ui.theme.AppThemeMode

@Composable
internal fun SettingsSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = UNIT_CARD_SHAPE,
            color = colors.surface,
            contentColor = colors.onSurface,
            tonalElevation = 0.dp,
            border = BorderStroke(
                UNIT_BORDER_WIDTH,
                colors.outlineVariant.copy(alpha = 0.65f)
            ),
            content = {
                Column(content = content)
            }
        )
    }
}

@Composable
internal fun ThemeChip(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        shape = UNIT_CONTROL_SHAPE,
        colors = unitFilterChipColors(),
        border = unitFilterChipBorder(selected),
        label = {
            AutoFitSingleLineText(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    )
}

@Composable
internal fun ThemeSwatchCard(
    mode: AppThemeMode,
    label: String,
    selected: Boolean,
    customBackground: Color,
    customAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = UNIT_CONTROL_SHAPE,
        color = if (selected) colors.primaryContainer.copy(alpha = 0.28f) else colors.surfaceContainerLow,
        border = BorderStroke(
            if (selected) 2.dp else UNIT_BORDER_WIDTH,
            if (selected) colors.primary else colors.outlineVariant
        ),
        modifier = modifier
            .width(86.dp)
            .semantics { this.selected = selected }
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(UNIT_COMPACT_CONTROL_SHAPE)
                    .border(
                        UNIT_BORDER_WIDTH,
                        colors.outlineVariant.copy(alpha = 0.6f),
                        UNIT_COMPACT_CONTROL_SHAPE
                    )
            ) {
                val w = size.width
                val h = size.height
                if (mode == AppThemeMode.SYSTEM) {
                    drawPath(
                        path = Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(0f, h); close() },
                        color = Color(0xFFF2EFE7)
                    )
                    drawPath(
                        path = Path().apply { moveTo(w, 0f); lineTo(w, h); lineTo(0f, h); close() },
                        color = Color(0xFF09090B)
                    )
                    drawLine(
                        color = Color(0xFF81776D).copy(alpha = 0.45f),
                        start = Offset(w, 0f),
                        end = Offset(0f, h),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawCircle(color = Color(0xFF9F4029), radius = 3.5.dp.toPx(), center = Offset(w * 0.28f, h * 0.34f))
                    drawCircle(color = Color(0xFFE7A083), radius = 3.5.dp.toPx(), center = Offset(w * 0.72f, h * 0.66f))
                } else {
                    val isDarkCustom = customBackground.luminance() < 0.5f
                    val (bg, header, ink, accent) = when (mode) {
                        AppThemeMode.LIGHT -> arrayOf(Color(0xFFF2EFE7), Color(0xFFE2DFD4), Color(0xFF2B2825), Color(0xFF9F4029))
                        AppThemeMode.SUMI -> arrayOf(Color(0xFF333132), Color(0xFF3D3B3C), Color(0xFFF2EFE7), Color(0xFFE7A083))
                        AppThemeMode.DARK -> arrayOf(Color(0xFF09090B), Color(0xFF16161A), Color(0xFFE6E6E9), Color(0xFFE7A083))
                        AppThemeMode.CUSTOM -> arrayOf(
                            customBackground,
                            if (isDarkCustom) lerp(customBackground, Color.White, 0.08f) else lerp(customBackground, Color.Black, 0.06f),
                            if (isDarkCustom) Color(0xFFF2EFE7) else Color(0xFF2B2825),
                            customAccent
                        )
                        AppThemeMode.SYSTEM -> arrayOf(Color.Transparent, Color.Transparent, Color.Transparent, Color.Transparent)
                    }
                    drawRect(bg)
                    val headerH = h * 0.28f
                    drawRect(header, size = Size(w, headerH))
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(w * 0.10f, headerH + h * 0.14f),
                        size = Size(w * 0.26f, 4.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                    drawRoundRect(
                        color = ink.copy(alpha = 0.75f),
                        topLeft = Offset(w * 0.10f, headerH + h * 0.38f),
                        size = Size(w * 0.56f, 2.5.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                    drawRoundRect(
                        color = ink.copy(alpha = 0.45f),
                        topLeft = Offset(w * 0.10f, headerH + h * 0.55f),
                        size = Size(w * 0.38f, 2.5.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = UNIT_HAIRLINE_ALPHA)
    )
}

@Composable
internal fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    colors.onSurface
                } else {
                    colors.onSurfaceVariant.copy(alpha = 0.55f)
                }
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.55f
                )
            )
        }

        Spacer(Modifier.width(14.dp))

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            colors = unitSwitchColors(colors)
        )
    }
}
