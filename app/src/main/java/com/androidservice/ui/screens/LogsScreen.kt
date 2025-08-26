package com.androidservice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidservice.data.LogEntry
import com.androidservice.data.LogLevel
import com.androidservice.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogsScreen(
    viewModel: MainViewModel = viewModel()
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var lastScrollTime by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastScrollTime > 200) { // 200ms节流
                listState.animateScrollToItem(logs.size - 1)
                lastScrollTime = currentTime
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 顶部间距，避免被状态栏遮挡
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "实时日志",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            if (logs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "暂无日志",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(logs) { entry ->
                            LogEntryItem(entry = entry)
                        }
                    }
                }
            }
        }
        
        // 底部间距，确保内容不被导航栏遮挡
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LogEntryItem(entry: LogEntry) {
    val formattedTime = remember(entry.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp))
    }
    val levelColor = when (entry.level) {
        LogLevel.DEBUG -> MaterialTheme.colorScheme.outline
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = formattedTime,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(60.dp),
            fontFamily = FontFamily.Monospace
        )
        
        Text(
            text = entry.level.name,
            style = MaterialTheme.typography.bodySmall,
            color = levelColor,
            modifier = Modifier.width(50.dp),
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
        
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace
        )
    }
}