package com.huginprep.app.ui.preset

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huginprep.app.R
import com.huginprep.app.data.CameraPreset

/**
 * 预设选择弹窗：拍摄页 / 导入页选择或新建预设时复用。
 *
 * @param presets 可选预设列表
 * @param onPresetSelected 用户点击某个预设（单选即触发）
 * @param onNewPresetClick 用户点击「新建预设」，调用方负责关闭弹窗并跳转
 * @param onDismiss 关闭弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetSelectionDialog(
    presets: List<CameraPreset>,
    onPresetSelected: (CameraPreset) -> Unit,
    onNewPresetClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_dialog_title)) },
        text = {
            if (presets.isEmpty()) {
                Text(stringResource(R.string.preset_dialog_empty))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(presets, key = { it.id }) { preset ->
                        ListItem(
                            headlineContent = { Text(preset.name) },
                            supportingContent = {
                                Text(stringResource(R.string.preset_dialog_summary, preset.hfov))
                            },
                            trailingContent = {
                                if (preset.isDefault) {
                                    Text(stringResource(R.string.preset_default_badge))
                                }
                            },
                            modifier = Modifier.clickable { onPresetSelected(preset) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNewPresetClick) {
                Text(stringResource(R.string.preset_dialog_new))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.preset_cancel))
            }
        }
    )
}
