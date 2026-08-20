package com.huginprep.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huginprep.app.R
import com.huginprep.app.update.UpdateViewModel

/**
 * 设置页（Compose 实现，SharedPreferences 持久化）。
 *
 * 设置项：
 * - 默认项目保存路径（SAF 目录选择器）
 * - 照片命名规则（前缀开关）
 * - 启动时自动检查更新（开关）
 * - GitHub 检查更新（下一轮实现，暂为占位）
 * - 关于本软件 → 关于页
 * - 打赏（下一轮实现，暂为占位）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onNavigateToDonate: () -> Unit,
    updateViewModel: UpdateViewModel,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 系统目录选择器（SAF）：选择默认项目保存路径
    val dirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.saveDefaultProjectPath(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ===== 通用 =====
            SettingSection(stringResource(R.string.settings_section_general)) {
                SettingItem(
                    title = stringResource(R.string.settings_default_path_title),
                    subtitle = uiState.defaultProjectPath
                        ?: stringResource(R.string.settings_default_path_default),
                    onClick = { dirPicker.launch(null) }
                )
                SettingItem(
                    title = stringResource(R.string.settings_photo_prefix_title),
                    subtitle = stringResource(R.string.settings_photo_prefix_summary),
                    trailing = {
                        Switch(
                            checked = uiState.photoPrefixEnabled,
                            onCheckedChange = { viewModel.setPhotoPrefixEnabled(context, it) }
                        )
                    }
                )
            }

            // ===== 更新 =====
            SettingSection(stringResource(R.string.settings_section_update)) {
                SettingItem(
                    title = stringResource(R.string.settings_auto_check_title),
                    subtitle = stringResource(R.string.settings_auto_check_summary),
                    trailing = {
                        Switch(
                            checked = uiState.autoCheckUpdate,
                            onCheckedChange = { viewModel.setAutoCheckUpdate(context, it) }
                        )
                    }
                )
                SettingItem(
                    title = stringResource(R.string.settings_check_update_title),
                    subtitle = stringResource(R.string.settings_check_update_summary),
                    onClick = { updateViewModel.checkForUpdate(context) }
                )
            }

            // ===== 其他 =====
            SettingSection(stringResource(R.string.settings_section_other)) {
                SettingItem(
                    title = stringResource(R.string.settings_about_title),
                    subtitle = stringResource(R.string.settings_about_summary),
                    onClick = onNavigateToAbout
                )
                SettingItem(
                    title = stringResource(R.string.settings_donate_title),
                    subtitle = stringResource(R.string.settings_donate_summary),
                    onClick = onNavigateToDonate
                )
            }
        }
    }
}

/** 设置分组：组标题 + 一组设置行 */
@Composable
private fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

/** 单个设置行：标题 + 副标题 + 尾部（开关或箭头） */
@Composable
private fun SettingItem(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val base = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp)
    val modifier = if (onClick != null) base.clickable(onClick = onClick) else base

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
        if (onClick != null && trailing == null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
