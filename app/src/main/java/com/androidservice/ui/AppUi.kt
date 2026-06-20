package com.androidservice.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidservice.R

object AppDimens {
    val screenHorizontal = 14.dp
    val screenTop = 10.dp
    val screenBottom = 8.dp
    val sectionSpacing = 10.dp
    val panelPadding = 12.dp
    val panelSpacing = 8.dp
    val fabClearance = 64.dp
    val iconButtonSize = 32.dp
    val iconSize = 18.dp
}

fun Modifier.screenHorizontalPadding(): Modifier = padding(horizontal = AppDimens.screenHorizontal)

@Composable
fun rememberFormatRefreshFailure(): (String) -> String {
    val resources = LocalResources.current
    return remember(resources) {
        { message -> resources.getString(R.string.config_remote_refresh_failed, message) }
    }
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun FlatPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.panelPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.panelSpacing),
            content = content,
        )
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatusDot(color: Color, active: Boolean, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.18f else 1f,
        animationSpec = tween(280),
        label = "statusDotScale",
    )
    Spacer(
        modifier = modifier
            .size(8.dp)
            .scale(scale)
            .background(color, CircleShape),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AnimatedCount(value: String, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(initialScale = 0.96f))
                .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.96f))
                .using(SizeTransform(clip = false))
        },
        label = "animatedCount",
        modifier = modifier,
    ) { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SectionTitle(title: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        trailing?.invoke()
    }
}

@Composable
fun SoftDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

@Composable
fun VisibilityFade(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.98f),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.98f),
    ) {
        content()
    }
}

@Composable
fun CompactIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(AppDimens.iconButtonSize),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(AppDimens.iconSize),
        )
    }
}

@Composable
fun ModeChip(label: String, selected: Boolean = true) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            disabledLabelColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        border = null,
    )
}
