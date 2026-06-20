package com.androidservice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidservice.R
import com.androidservice.data.LogEntry
import com.androidservice.data.LogLevel
import com.androidservice.ui.AppDimens
import com.androidservice.ui.CompactIconButton
import com.androidservice.ui.EmptyState
import com.androidservice.ui.PageHeader
import com.androidservice.ui.rememberDateTimeFormatter
import com.androidservice.ui.screenHorizontalPadding
import com.androidservice.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date

private enum class LogFilter { ALL, WARN, ERROR }

@Composable
fun LogsScreen(viewModel: MainViewModel = viewModel()) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val timeFormatter = rememberDateTimeFormatter("HH:mm:ss")
    var followLatest by rememberSaveable { mutableStateOf(true) }
    var logFilter by rememberSaveable { mutableStateOf(LogFilter.ALL) }

    val filteredLogs = remember(logs, logFilter) {
        when (logFilter) {
            LogFilter.ALL -> logs
            LogFilter.WARN -> logs.filter { it.level == LogLevel.WARN || it.level == LogLevel.ERROR }
            LogFilter.ERROR -> logs.filter { it.level == LogLevel.ERROR }
        }
    }

    LaunchedEffect(filteredLogs.size, followLatest) {
        if (followLatest && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenHorizontalPadding()
            .padding(top = AppDimens.screenTop, bottom = AppDimens.screenBottom),
        verticalArrangement = Arrangement.spacedBy(AppDimens.sectionSpacing),
    ) {
        PageHeader(
            title = stringResource(R.string.logs_title),
            subtitle = stringResource(R.string.logs_subtitle),
            trailing = {
                Row {
                    CompactIconButton(
                        onClick = { followLatest = !followLatest },
                        contentDescription = stringResource(R.string.action_follow_logs),
                        icon = Icons.Filled.VerticalAlignBottom,
                        tint = if (followLatest) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    CompactIconButton(
                        onClick = viewModel::clearLogs,
                        contentDescription = stringResource(R.string.action_clear_logs),
                        icon = Icons.Filled.DeleteSweep,
                    )
                }
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LogFilterChip(
                label = stringResource(R.string.logs_filter_all),
                selected = logFilter == LogFilter.ALL,
                onClick = { logFilter = LogFilter.ALL },
            )
            LogFilterChip(
                label = stringResource(R.string.logs_filter_warn),
                selected = logFilter == LogFilter.WARN,
                onClick = { logFilter = LogFilter.WARN },
            )
            LogFilterChip(
                label = stringResource(R.string.logs_filter_error),
                selected = logFilter == LogFilter.ERROR,
                onClick = { logFilter = LogFilter.ERROR },
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.small,
        ) {
            if (filteredLogs.isEmpty()) {
                EmptyState(
                    Icons.AutoMirrored.Filled.List,
                    stringResource(R.string.logs_empty_title),
                    stringResource(R.string.logs_empty_desc),
                )
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filteredLogs, key = { it.id }) { entry ->
                            LogEntryItem(entry, timeFormatter)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = null,
                    modifier = Modifier.padding(0.dp),
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun LogEntryItem(entry: LogEntry, timeFormatter: SimpleDateFormat) {
    val time = timeFormatter.format(Date(entry.timestamp))
    val levelColor = when (entry.level) {
        LogLevel.DEBUG -> MaterialTheme.colorScheme.outline
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                time,
                modifier = Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                entry.level.name,
                modifier = Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = levelColor,
            )
        }
        Text(
            entry.message,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
