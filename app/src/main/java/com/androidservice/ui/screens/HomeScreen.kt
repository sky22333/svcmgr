package com.androidservice.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidservice.R
import com.androidservice.data.ServiceState
import com.androidservice.data.ServiceStatus
import com.androidservice.singbox.SingBoxConstants
import com.androidservice.singbox.SingBoxRunMode
import com.androidservice.ui.AnimatedCount
import com.androidservice.ui.AppDimens
import com.androidservice.ui.CompactIconButton
import com.androidservice.ui.FlatPanel
import com.androidservice.ui.MetricRow
import com.androidservice.ui.PageHeader
import com.androidservice.ui.SectionTitle
import com.androidservice.ui.ServicePowerSwitch
import com.androidservice.ui.SoftDivider
import com.androidservice.ui.TrafficStatsPanel
import com.androidservice.ui.screenHorizontalPadding
import com.androidservice.ui.rememberDateTimeFormatter
import com.androidservice.ui.theme.ThemeSeed
import com.androidservice.ui.theme.ThemeSeedOptions
import com.androidservice.viewmodel.MainViewModel
import java.util.Date

@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(),
    seedColor: Color,
    onSeedColorChange: (Color) -> Unit,
) {
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val serviceStatus by viewModel.serviceStatus.collectAsStateWithLifecycle()
    val serviceError by viewModel.serviceErrorMessage.collectAsStateWithLifecycle()
    val logCount by viewModel.logCount.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()
    val singBoxRunMode by viewModel.singBoxRunMode.collectAsStateWithLifecycle()
    val singBoxListen by viewModel.singBoxListenEndpoint.collectAsStateWithLifecycle()
    val singBoxTraffic by viewModel.singBoxTraffic.collectAsStateWithLifecycle()
    val timeFormatter = rememberDateTimeFormatter("yyyy-MM-dd HH:mm:ss")
    var showSeedDialog by remember { mutableStateOf(false) }
    val isSingBox = config.binaryName == SingBoxConstants.BINARY_NAME

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .screenHorizontalPadding()
            .padding(top = AppDimens.screenTop, bottom = AppDimens.screenBottom),
        verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing),
    ) {
        PageHeader(
            title = stringResource(R.string.home_title),
            subtitle = stringResource(R.string.home_subtitle),
            trailing = {
                CompactIconButton(
                    onClick = { showSeedDialog = true },
                    contentDescription = stringResource(R.string.action_theme),
                    icon = Icons.Filled.Palette,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )

        ServicePanel(
            status = serviceStatus,
            state = serviceState,
            isSingBox = isSingBox,
            singBoxRunMode = singBoxRunMode,
            errorMessage = serviceError,
            onStart = viewModel::startService,
            onStop = viewModel::stopService,
        )

        AnimatedVisibility(
            visible = isSingBox,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160)),
        ) {
            TrafficStatsPanel(
                stats = singBoxTraffic,
                active = serviceStatus == ServiceStatus.RUNNING,
            )
        }

        FlatPanel {
            SectionTitle(stringResource(R.string.home_overview))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SummaryMetric(stringResource(R.string.home_program), config.binaryName.ifBlank { stringResource(R.string.home_not_selected) })
                SummaryMetric(stringResource(R.string.home_logs), logCount.toString())
                if (!isSingBox) {
                    SummaryMetric(stringResource(R.string.home_restarts), serviceState.restartCount.toString())
                }
            }
            SoftDivider()
            if (isSingBox) {
                MetricRow(
                    stringResource(R.string.home_config_file),
                    config.configFileName.ifBlank { stringResource(R.string.home_not_selected) },
                )
                MetricRow(
                    stringResource(R.string.settings_singbox_config),
                    singBoxRunMode?.let { modeLabel(it) } ?: stringResource(R.string.singbox_mode_unknown),
                )
                if (singBoxRunMode == SingBoxRunMode.PROXY && !singBoxListen.isNullOrBlank()) {
                    MetricRow(stringResource(R.string.singbox_listen), singBoxListen!!)
                }
            } else {
                MetricRow(
                    stringResource(R.string.home_process_id),
                    serviceState.processId?.toString() ?: stringResource(R.string.home_not_running),
                )
            }
            MetricRow(
                stringResource(R.string.home_start_time),
                serviceState.startTime?.let { timeFormatter.format(Date(it)) } ?: "-",
            )
        }
    }

    if (showSeedDialog) {
        ThemeSeedDialog(
            selectedColor = seedColor,
            onSeedSelected = {
                onSeedColorChange(it.color)
                showSeedDialog = false
            },
            onDismiss = { showSeedDialog = false },
        )
    }
}

@Composable
private fun modeLabel(mode: SingBoxRunMode): String = when (mode) {
    SingBoxRunMode.VPN -> stringResource(R.string.singbox_mode_vpn)
    SingBoxRunMode.PROXY -> stringResource(R.string.singbox_mode_proxy)
}

@Composable
private fun ThemeSeedDialog(
    selectedColor: Color,
    onSeedSelected: (ThemeSeed) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.theme_dialog_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeSeedOptions.forEach { seed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeedSelected(seed) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Spacer(
                            modifier = Modifier
                                .size(if (seed.color == selectedColor) 16.dp else 12.dp)
                                .background(seed.color, CircleShape),
                        )
                        Text(
                            text = seed.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (seed.color == selectedColor) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        },
        confirmButton = {
            CompactIconButton(
                onClick = onDismiss,
                contentDescription = stringResource(R.string.action_done),
                icon = Icons.Filled.Check,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun ServicePanel(
    status: ServiceStatus,
    state: ServiceState,
    isSingBox: Boolean,
    singBoxRunMode: SingBoxRunMode?,
    errorMessage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    FlatPanel {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.home_service_status),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                statusLabel(status, isSingBox, singBoxRunMode),
                style = MaterialTheme.typography.bodySmall,
                color = if (status == ServiceStatus.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (status == ServiceStatus.ERROR && !errorMessage.isNullOrBlank()) {
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        ServicePowerSwitch(
            status = status,
            onStart = onStart,
            onStop = onStop,
        )

        MetricRow(
            stringResource(R.string.home_current_program),
            state.binaryName.ifBlank { stringResource(R.string.home_not_running) },
        )
    }
}

@Composable
private fun statusLabel(
    status: ServiceStatus,
    isSingBox: Boolean,
    singBoxRunMode: SingBoxRunMode?,
): String {
    return when (status) {
        ServiceStatus.STOPPED -> stringResource(R.string.status_stopped)
        ServiceStatus.STARTING -> stringResource(R.string.status_starting)
        ServiceStatus.RUNNING -> when {
            isSingBox && singBoxRunMode == SingBoxRunMode.VPN -> stringResource(R.string.status_vpn_running)
            isSingBox && singBoxRunMode == SingBoxRunMode.PROXY -> stringResource(R.string.status_proxy_running)
            else -> stringResource(R.string.status_running)
        }
        ServiceStatus.STOPPING -> stringResource(R.string.status_stopping)
        ServiceStatus.ERROR -> stringResource(R.string.status_error)
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedCount(value)
    }
}
