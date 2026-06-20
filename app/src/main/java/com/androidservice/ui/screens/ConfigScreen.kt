package com.androidservice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidservice.R
import com.androidservice.manager.RemoteConfigRefreshResult
import com.androidservice.singbox.SingBoxConstants
import com.androidservice.singbox.SingBoxRunMode
import com.androidservice.ui.AppDimens
import com.androidservice.ui.CompactIconButton
import com.androidservice.ui.FlatPanel
import com.androidservice.ui.ModeChip
import com.androidservice.ui.PageHeader
import com.androidservice.ui.SectionTitle
import com.androidservice.ui.VisibilityFade
import com.androidservice.ui.rememberFormatRefreshFailure
import com.androidservice.ui.screenHorizontalPadding
import com.androidservice.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: MainViewModel = viewModel(),
    snackbarHostState: SnackbarHostState,
    onNavigateToConfigEdit: (String?) -> Unit,
) {
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()
    val availableNames by viewModel.availableBinaryNames.collectAsStateWithLifecycle()
    val appConfigFiles by viewModel.appConfigFiles.collectAsStateWithLifecycle()
    val refreshingRemoteFiles by viewModel.refreshingRemoteFiles.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val formatRefreshFailure = rememberFormatRefreshFailure()
    val refreshSuccessMessage = stringResource(R.string.config_remote_refresh_success)
    val isSingBox = config.binaryName == SingBoxConstants.BINARY_NAME
    val singBoxRunMode by viewModel.singBoxRunMode.collectAsStateWithLifecycle()
    val singBoxListen by viewModel.singBoxListenEndpoint.collectAsStateWithLifecycle()
    val selectedConfigFile = appConfigFiles.find { it.fileName == config.configFileName }
    val isRefreshingSelected = config.configFileName in refreshingRemoteFiles

    var expanded by remember { mutableStateOf(false) }
    var configExpanded by remember { mutableStateOf(false) }
    var argumentsText by remember(config.argumentsString) { mutableStateOf(config.argumentsString) }
    var remoteHeadersText by remember(config.remoteConfigHeaders) {
        mutableStateOf(formatRemoteHeaders(config.remoteConfigHeaders))
    }
    var restartDelayText by remember(config.restartDelay) { mutableStateOf((config.restartDelay / 1000).toString()) }
    var maxRestartsText by remember(config.maxRestarts) {
        mutableStateOf(config.maxRestarts.takeIf { it >= 0 }?.toString().orEmpty())
    }
    var userEdited by remember { mutableStateOf(false) }
    val latestConfig by rememberUpdatedState(config)

    LaunchedEffect(Unit) {
        viewModel.loadAvailableBinaryNames()
    }

    LaunchedEffect(isSingBox, config.configFileName) {
        if (isSingBox) viewModel.refreshSingBoxRunMode()
    }

    LaunchedEffect(userEdited, argumentsText, remoteHeadersText, restartDelayText, maxRestartsText) {
        if (!userEdited) return@LaunchedEffect
        delay(800)
        val next = latestConfig.copy(
            argumentsString = argumentsText,
            remoteConfigHeaders = parseRemoteHeaders(remoteHeadersText),
            restartDelay = restartDelayText.toLongOrNull()?.coerceAtLeast(1)?.times(1000) ?: latestConfig.restartDelay,
            maxRestarts = maxRestartsText.toIntOrNull() ?: -1,
        )
        if (next != latestConfig) viewModel.updateConfig(next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .screenHorizontalPadding()
            .padding(top = AppDimens.screenTop, bottom = AppDimens.screenBottom),
        verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing),
    ) {
        PageHeader(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
        )

        FlatPanel {
            SectionTitle(stringResource(R.string.settings_program))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = config.binaryName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_program), style = MaterialTheme.typography.bodySmall) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                    colors = compactTextFieldColors(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (availableNames.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_no_binaries), style = MaterialTheme.typography.bodySmall) },
                            enabled = false,
                            onClick = {},
                        )
                    } else {
                        availableNames.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name, style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    viewModel.updateConfig(config.copy(binaryName = name))
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        if (isSingBox) {
            FlatPanel {
                SectionTitle(
                    title = stringResource(R.string.settings_singbox_config),
                    trailing = {
                        Row {
                            if (config.configFileName.isNotBlank()) {
                                CompactIconButton(
                                    onClick = { onNavigateToConfigEdit(config.configFileName) },
                                    contentDescription = stringResource(R.string.action_edit),
                                    icon = Icons.Filled.Edit,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (!selectedConfigFile?.remoteUrl.isNullOrBlank()) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            when (val result = viewModel.refreshRemoteConfigFile(config.configFileName)) {
                                                is RemoteConfigRefreshResult.Success -> {
                                                    snackbarHostState.showSnackbar(refreshSuccessMessage)
                                                }
                                                is RemoteConfigRefreshResult.Failure -> {
                                                    snackbarHostState.showSnackbar(formatRefreshFailure(result.message))
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isRefreshingSelected,
                                    modifier = Modifier.size(AppDimens.iconButtonSize),
                                ) {
                                    if (isRefreshingSelected) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Sync,
                                            contentDescription = stringResource(R.string.config_remote_refresh),
                                            modifier = Modifier.size(AppDimens.iconSize),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
                ExposedDropdownMenuBox(
                    expanded = configExpanded,
                    onExpandedChange = { configExpanded = !configExpanded },
                ) {
                    OutlinedTextField(
                        value = config.configFileName.ifBlank { stringResource(R.string.home_not_selected) },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.settings_config_file), style = MaterialTheme.typography.bodySmall) },
                        placeholder = { Text(stringResource(R.string.settings_config_placeholder), style = MaterialTheme.typography.bodySmall) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(configExpanded) },
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth(),
                        colors = compactTextFieldColors(),
                    )
                    ExposedDropdownMenu(expanded = configExpanded, onDismissRequest = { configExpanded = false }) {
                        val jsonFiles = appConfigFiles.filter { it.fileName.endsWith(".json", ignoreCase = true) }
                        if (jsonFiles.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_no_json), style = MaterialTheme.typography.bodySmall) },
                                enabled = false,
                                onClick = {},
                            )
                        } else {
                            jsonFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file.fileName, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        viewModel.updateConfig(config.copy(configFileName = file.fileName))
                                        configExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeChip(
                        label = when (singBoxRunMode) {
                            SingBoxRunMode.VPN -> stringResource(R.string.singbox_mode_vpn)
                            SingBoxRunMode.PROXY -> stringResource(R.string.singbox_mode_proxy)
                            null -> stringResource(R.string.singbox_mode_unknown)
                        },
                    )
                }
                Text(
                    text = when (singBoxRunMode) {
                        SingBoxRunMode.VPN -> stringResource(R.string.singbox_mode_vpn_desc)
                        SingBoxRunMode.PROXY -> stringResource(R.string.singbox_mode_proxy_desc)
                        null -> stringResource(R.string.singbox_mode_unknown)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (singBoxRunMode == SingBoxRunMode.PROXY && !singBoxListen.isNullOrBlank()) {
                    Text(
                        text = "${stringResource(R.string.singbox_listen)}: $singBoxListen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            FlatPanel {
                SectionTitle(
                    title = stringResource(R.string.settings_arguments),
                    trailing = {
                        Row {
                            CompactIconButton(
                                onClick = {
                                    scope.launch {
                                        clipboard.getPlainText()?.let {
                                            userEdited = true
                                            argumentsText = it
                                        }
                                    }
                                },
                                contentDescription = stringResource(R.string.action_paste),
                                icon = Icons.Filled.ContentPaste,
                            )
                            CompactIconButton(
                                onClick = { viewModel.updateConfig(config.copy(argumentsString = argumentsText)) },
                                contentDescription = stringResource(R.string.action_save),
                                icon = Icons.Filled.Save,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
                OutlinedTextField(
                    value = argumentsText,
                    onValueChange = {
                        userEdited = true
                        argumentsText = it
                    },
                    label = { Text(stringResource(R.string.settings_arguments), style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text(stringResource(R.string.settings_arguments_hint), style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    colors = compactTextFieldColors(),
                )
                VisibilityFade(argumentsText.isNotBlank()) {
                    Text(
                        text = stringResource(
                            R.string.settings_arguments_exec,
                            config.binaryName.ifBlank { stringResource(R.string.settings_program) },
                            argumentsText,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlatPanel {
                SectionTitle(stringResource(R.string.settings_auto_restart))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_auto_restart_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Switch(
                        checked = config.autoRestart,
                        onCheckedChange = { viewModel.updateConfig(config.copy(autoRestart = it)) },
                    )
                }
                VisibilityFade(config.autoRestart) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = restartDelayText,
                            onValueChange = {
                                userEdited = true
                                restartDelayText = it.filter(Char::isDigit)
                            },
                            label = { Text(stringResource(R.string.settings_restart_delay), style = MaterialTheme.typography.bodySmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                            colors = compactTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = maxRestartsText,
                            onValueChange = {
                                userEdited = true
                                maxRestartsText = it.filter(Char::isDigit)
                            },
                            label = { Text(stringResource(R.string.settings_max_restarts), style = MaterialTheme.typography.bodySmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                            colors = compactTextFieldColors(),
                        )
                    }
                }
            }
        }

        FlatPanel {
            SectionTitle(stringResource(R.string.settings_remote_headers))
            OutlinedTextField(
                value = remoteHeadersText,
                onValueChange = {
                    userEdited = true
                    remoteHeadersText = it
                },
                label = { Text(stringResource(R.string.settings_remote_headers), style = MaterialTheme.typography.bodySmall) },
                placeholder = { Text(stringResource(R.string.settings_remote_headers_hint), style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = compactTextFieldColors(),
            )
            Text(
                text = stringResource(R.string.settings_remote_headers_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FlatPanel {
            SectionTitle(stringResource(R.string.settings_background))
            Text(
                text = stringResource(R.string.settings_background_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CompactIconButton(
                onClick = viewModel::requestIgnoreBatteryOptimizations,
                contentDescription = stringResource(R.string.action_battery_saver),
                icon = Icons.Filled.BatterySaver,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        FlatPanel {
            SectionTitle(
                title = stringResource(R.string.settings_runtime_backup),
                trailing = {
                    Row {
                        CompactIconButton(
                            onClick = viewModel::loadConfig,
                            contentDescription = stringResource(R.string.action_load),
                            icon = Icons.Filled.Download,
                        )
                        CompactIconButton(
                            onClick = viewModel::saveConfig,
                            contentDescription = stringResource(R.string.action_export),
                            icon = Icons.Filled.Upload,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }

        Spacer(Modifier.padding(bottom = 4.dp))
    }
}

@Composable
private fun compactTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
)

private fun formatRemoteHeaders(headers: Map<String, String>): String =
    headers.entries.joinToString("\n") { (name, value) -> "$name: $value" }

private fun parseRemoteHeaders(text: String): Map<String, String> =
    text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val name = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            if (name.isEmpty() || value.isEmpty()) null else name to value
        }
        .toMap()
