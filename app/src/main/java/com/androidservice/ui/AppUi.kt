package com.androidservice.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import com.androidservice.R
import com.androidservice.data.ServiceStatus
import com.androidservice.data.SingBoxTrafficStats
import com.androidservice.singbox.TrafficFormatter

private object AppMotion {
    val stateChange = tween<Float>(240, easing = FastOutSlowInEasing)
    val colorChange = tween<Color>(240, easing = FastOutSlowInEasing)
    val fade = tween<Float>(180, easing = LinearOutSlowInEasing)
    val valueChange = tween<Float>(200, easing = FastOutSlowInEasing)
}

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
    val serviceSwitchHeight = 38.dp
    val serviceSwitchThumb = 28.dp
    val serviceSwitchInset = 5.dp
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
fun ServicePowerSwitch(
    status: ServiceStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onStartState by rememberUpdatedState(onStart)
    val onStopState by rememberUpdatedState(onStop)
    val resources = LocalResources.current
    val offLabel = stringResource(R.string.service_switch_off)
    val onLabel = stringResource(R.string.service_switch_on)

    val thumbTarget = when (status) {
        ServiceStatus.RUNNING -> 1f
        ServiceStatus.STARTING, ServiceStatus.STOPPING -> 0.5f
        ServiceStatus.STOPPED, ServiceStatus.ERROR -> 0f
    }
    val thumbOffset by animateFloatAsState(
        targetValue = thumbTarget,
        animationSpec = AppMotion.stateChange,
        label = "serviceSwitchThumb",
    )
    val trackColor by animateColorAsState(
        targetValue = when (status) {
            ServiceStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
            ServiceStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
            ServiceStatus.STARTING, ServiceStatus.STOPPING -> MaterialTheme.colorScheme.tertiaryContainer
            ServiceStatus.STOPPED -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = AppMotion.colorChange,
        label = "serviceSwitchTrack",
    )
    val thumbColor by animateColorAsState(
        targetValue = when (status) {
            ServiceStatus.RUNNING -> MaterialTheme.colorScheme.primary
            ServiceStatus.ERROR -> MaterialTheme.colorScheme.error
            ServiceStatus.STARTING, ServiceStatus.STOPPING -> MaterialTheme.colorScheme.tertiary
            ServiceStatus.STOPPED -> MaterialTheme.colorScheme.surface
        },
        animationSpec = AppMotion.colorChange,
        label = "serviceSwitchThumbColor",
    )
    val offLabelColor by animateColorAsState(
        targetValue = when (status) {
            ServiceStatus.RUNNING -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = AppMotion.colorChange,
        label = "serviceSwitchOffLabel",
    )
    val onLabelColor by animateColorAsState(
        targetValue = when (status) {
            ServiceStatus.RUNNING -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = AppMotion.colorChange,
        label = "serviceSwitchOnLabel",
    )
    val enabled = status != ServiceStatus.STOPPING
    val switchDescription = remember(status, resources) {
        when (status) {
            ServiceStatus.RUNNING -> resources.getString(R.string.service_switch_stop)
            ServiceStatus.STARTING -> resources.getString(R.string.service_switch_cancel)
            ServiceStatus.STOPPED, ServiceStatus.ERROR -> resources.getString(R.string.service_switch_start)
            ServiceStatus.STOPPING -> resources.getString(R.string.status_stopping)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.serviceSwitchHeight)
                .clip(RoundedCornerShape(AppDimens.serviceSwitchHeight / 2))
                .background(trackColor)
                .semantics {
                    role = Role.Switch
                    contentDescription = switchDescription
                }
                .clickable(enabled = enabled) {
                    when (status) {
                        ServiceStatus.RUNNING, ServiceStatus.STARTING -> onStopState()
                        ServiceStatus.STOPPED, ServiceStatus.ERROR -> onStartState()
                        ServiceStatus.STOPPING -> Unit
                    }
                },
        ) {
            val travel = (maxWidth - AppDimens.serviceSwitchThumb - AppDimens.serviceSwitchInset * 2)
                .coerceAtLeast(0.dp)
            val travelPx = with(LocalDensity.current) { travel.toPx() }
            val insetPx = with(LocalDensity.current) { AppDimens.serviceSwitchInset.toPx() }
            val showThumbBorder = status == ServiceStatus.STOPPED || status == ServiceStatus.ERROR
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = insetPx + travelPx * thumbOffset
                        translationY = insetPx
                    }
                    .size(AppDimens.serviceSwitchThumb)
                    .clip(CircleShape)
                    .then(
                        if (showThumbBorder) {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .background(thumbColor),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = offLabel,
                style = MaterialTheme.typography.labelSmall,
                color = offLabelColor,
            )
            Text(
                text = onLabel,
                style = MaterialTheme.typography.labelSmall,
                color = onLabelColor,
            )
        }
    }
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
    Crossfade(
        targetState = value,
        animationSpec = AppMotion.valueChange,
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
        enter = fadeIn(AppMotion.fade),
        exit = fadeOut(tween(120, easing = LinearOutSlowInEasing)),
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

@Composable
fun TrafficStatsPanel(
    stats: SingBoxTrafficStats,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val downloadSpeed = remember(stats.downlinkSpeed, active) {
        if (active) TrafficFormatter.formatSpeed(stats.downlinkSpeed) else "—"
    }
    val uploadSpeed = remember(stats.uplinkSpeed, active) {
        if (active) TrafficFormatter.formatSpeed(stats.uplinkSpeed) else "—"
    }
    val downloadTotal = remember(stats.downlinkTotal, active) {
        if (active) TrafficFormatter.formatTotal(stats.downlinkTotal) else "—"
    }
    val uploadTotal = remember(stats.uplinkTotal, active) {
        if (active) TrafficFormatter.formatTotal(stats.uplinkTotal) else "—"
    }
    val accentAlpha by animateFloatAsState(
        targetValue = when {
            !active -> 0.45f
            stats.hasTraffic -> 1f
            else -> 0.78f
        },
        animationSpec = AppMotion.stateChange,
        label = "trafficAccent",
    )

    FlatPanel(modifier = modifier) {
        SectionTitle(stringResource(R.string.home_traffic))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrafficStatColumn(
                label = stringResource(R.string.traffic_download),
                value = downloadSpeed,
                icon = Icons.Filled.ArrowDownward,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = accentAlpha),
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            )
            TrafficStatColumn(
                label = stringResource(R.string.traffic_upload),
                value = uploadSpeed,
                icon = Icons.Filled.ArrowUpward,
                tint = MaterialTheme.colorScheme.tertiary.copy(alpha = accentAlpha),
                modifier = Modifier.weight(1f),
            )
        }
        SoftDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TrafficTotalLabel(
                label = stringResource(R.string.traffic_total_download),
                value = downloadTotal,
            )
            TrafficTotalLabel(
                label = stringResource(R.string.traffic_total_upload),
                value = uploadTotal,
            )
        }
        if (active && !stats.available) {
            Text(
                text = stringResource(R.string.traffic_idle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrafficStatColumn(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = tint,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlatAnimatedText(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FlatAnimatedText(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight,
    color: Color,
) {
    Crossfade(
        targetState = text,
        animationSpec = AppMotion.valueChange,
        label = "flatAnimatedText",
    ) { target ->
        Text(
            text = target,
            style = style,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrafficTotalLabel(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlatAnimatedText(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
