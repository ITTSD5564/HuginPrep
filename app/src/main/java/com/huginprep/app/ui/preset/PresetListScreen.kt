package com.huginprep.app.ui.preset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huginprep.app.R
import com.huginprep.app.data.CameraPreset

/**
 * 预设管理列表页。
 *
 * @param onNavigateToEdit 跳转到新建（null）或编辑（presetId）页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetListScreen(
    onNavigateToEdit: (presetId: String?) -> Unit,
    viewModel: PresetViewModel = viewModel(factory = PresetViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 从编辑页返回时刷新列表
    LaunchedEffect(Unit) { viewModel.loadPresets() }

    // 一次性错误提示（Snackbar）
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preset_title)) },
                actions = {
                    IconButton(onClick = { onNavigateToEdit(null) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.preset_add))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            // 首次加载中
            uiState.isLoading && uiState.presets.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // 空状态
            uiState.presets.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.preset_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 预设列表
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.presets, key = { it.id }) { preset ->
                        PresetCard(
                            preset = preset,
                            onEdit = { onNavigateToEdit(preset.id) },
                            onSetDefault = { viewModel.setDefault(preset.id) },
                            onDelete = { viewModel.deletePreset(preset) }
                        )
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (uiState.showDeleteDialog) {
        PresetDeleteDialog(
            presetName = uiState.presetToDelete?.name.orEmpty(),
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDeleteDialog() }
        )
    }
}

/** 单个预设卡片：名称 + 参数摘要 + 默认标签 + 编辑/设默认/删除 */
@Composable
private fun PresetCard(
    preset: CameraPreset,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (preset.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.preset_default_badge),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.preset_summary,
                        preset.focalLength,
                        preset.sensorWidth,
                        preset.sensorHeight,
                        preset.hfov
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.preset_edit_action))
            }
            IconButton(onClick = onSetDefault) {
                Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.preset_set_default_action))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.preset_delete_action))
            }
        }
    }
}

/** 删除确认对话框 */
@Composable
private fun PresetDeleteDialog(
    presetName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_delete_title)) },
        text = { Text(stringResource(R.string.preset_delete_message, presetName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.preset_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.preset_cancel))
            }
        }
    )
}
