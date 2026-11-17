package com.huskydg.localproxydetector.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huskydg.localproxydetector.ui.theme.MagicTheme
import com.huskydg.localproxydetector.ui.theme.MonoFamily

// ───────────────────────── Icons (no material-icons dependency) ─────────────────────────

object MagicIcons {
    private fun vec(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()

    val Info: ImageVector by lazy {
        vec("info", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2L11,7h2v2z")
    }
    val ExpandMore: ImageVector by lazy {
        vec("expand_more", "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z")
    }
}

// ───────────────────────── Status chip (.status-badge) ─────────────────────────

enum class StatusKind { Neutral, Busy, Active, Warning, Inactive }

@Composable
fun StatusChip(
    text: String,
    kind: StatusKind,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val ext = MagicTheme.colors
    val cs = MaterialTheme.colorScheme
    val accent = when (kind) {
        StatusKind.Neutral -> cs.outline
        StatusKind.Busy -> cs.primary
        StatusKind.Active -> ext.success
        StatusKind.Warning -> ext.warning
        StatusKind.Inactive -> cs.error
    }
    val textColor = if (kind == StatusKind.Neutral) cs.onSurfaceVariant else accent
    val container = if (kind == StatusKind.Neutral) ext.surface3 else accent.copy(alpha = 0.08f)
    val border = if (kind == StatusKind.Neutral) cs.outlineVariant else accent
    val pulsing = kind == StatusKind.Active || kind == StatusKind.Busy

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 3f,
        targetValue = if (pulsing) 6f else 3f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseRadius",
    )

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .border(1.dp, border, CircleShape)
            .padding(
                start = if (small) 8.dp else 8.dp,
                end = if (small) 10.dp else 12.dp,
                top = if (small) 3.dp else 4.dp,
                bottom = if (small) 3.dp else 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            // soft halo (pulses while active / scanning)
            if (kind != StatusKind.Neutral && kind != StatusKind.Inactive && kind != StatusKind.Warning) {
                Box(
                    Modifier
                        .size((8 + pulse * 2 - 6).dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.2f)),
                )
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        }
        Text(
            text = text,
            color = textColor,
            fontSize = if (small) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ───────────────────────── Start / Stop buttons (.btn-start / .btn-stop) ─────────────────────────

@Composable
fun ControlButton(
    text: String,
    isStop: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = MagicTheme.colors
    val cs = MaterialTheme.colorScheme
    val shape = if (ext.isDark) CircleShape else RoundedCornerShape(16.dp)

    val (textColor, bgModifier) = if (ext.isDark) {
        val bg = if (isStop) cs.errorContainer else ext.successContainer
        val fg = if (isStop) cs.onErrorContainer else ext.onSuccessContainer
        fg to Modifier.background(bg, shape)
    } else {
        val brush = if (isStop) {
            Brush.verticalGradient(listOf(Color(0xFFE5483F), Color(0xFFC5302A)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFF2E9E57), Color(0xFF1F8043)))
        }
        Color.White to Modifier.background(brush, shape)
    }

    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .then(bgModifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (ext.isDark) FontWeight.SemiBold else FontWeight.Bold,
            letterSpacing = 0.2.sp,
            maxLines = 1,
        )
    }
}

// ───────────────────────── Cards ─────────────────────────

/** `.settings-group-box`: rounded card with an uppercase primary-coloured heading. */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MagicTheme.colors.surface1)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(20.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        content()
    }
}

/** `.category-group`: bordered card with a tinted header strip (title left, optional widget right). */
@Composable
fun CategoryCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val ext = MagicTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ext.surface1)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ext.surface2)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title.uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (trailing != null) {
                Box(Modifier.padding(start = 12.dp)) { trailing() }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}

/** `.pinfo-row`: label on the left, bold value on the right, hairline below. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    mono: Boolean = false,
    divider: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = if (mono) 13.sp else 14.sp,
                fontFamily = if (mono) MonoFamily else null,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** `.config-item`: list row with a 3dp accent bar on the left. */
@Composable
fun AccentRow(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    divider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
        }
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Small pill (`.hosts-ip-chip`, `.routing-rule-outbound-badge`). */
@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    mono: Boolean = true,
) {
    Text(
        text = text,
        color = content,
        fontSize = 11.sp,
        fontFamily = if (mono) MonoFamily else null,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

enum class NoticeKind { Info, Success, Warning, Error }

/** `.restart-notice`: tinted box with a coloured left edge. */
@Composable
fun Notice(
    text: String,
    modifier: Modifier = Modifier,
    kind: NoticeKind = NoticeKind.Info,
) {
    val ext = MagicTheme.colors
    val cs = MaterialTheme.colorScheme
    val edge = when (kind) {
        NoticeKind.Info -> cs.primary
        NoticeKind.Success -> ext.success
        NoticeKind.Warning -> ext.warning
        NoticeKind.Error -> cs.error
    }
    val bg = if (kind == NoticeKind.Error) {
        cs.errorContainer.copy(alpha = if (ext.isDark) 0.35f else 0.8f)
    } else ext.surface2
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(4.dp))
            .background(bg),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(edge))
        Text(
            text = text,
            color = if (kind == NoticeKind.Error) cs.onErrorContainer else cs.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

// ───────────────────────── Settings controls ─────────────────────────

/** `.setting-item-row.toggle-row`: title + hint on the left, MD3 switch on the right. */
@Composable
fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (description != null) {
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/** `.edit-item-field`: MD3 filled text field (tinted, primary underline on focus). */
@Composable
fun MagicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val ext = MagicTheme.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ext.surface3,
            unfocusedContainerColor = ext.surface2,
            disabledContainerColor = ext.surface2,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** `<details><summary>`-style row used for collapsible lists. */
@Composable
fun ExpandRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            imageVector = MagicIcons.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp).rotate(if (expanded) 180f else 0f),
        )
    }
}

/** `.modal-section-divider`-style small uppercase caption inside a card. */
@Composable
fun SectionCaption(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

/** `.dash-title` */
@Composable
fun DashTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier,
    )
}

/** Plain muted line used for empty / not-tested states inside a card. */
@Composable
fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
