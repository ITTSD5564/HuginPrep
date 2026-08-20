package com.huginprep.app.ui.preset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huginprep.app.R

/**
 * 预设新建/编辑页。
 *
 * @param presetId null 表示新建模式；非空表示编辑模式（按 id 预填表单）
 * @param onBack 返回上一页（保存成功后也会回调）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditScreen(
    presetId: String?,
    onBack: () -> Unit,
    viewModel: PresetViewModel = viewModel(factory = PresetViewModel.Factory)
) {
    val editState by viewModel.editState.collectAsStateWithLifecycle()

    // 预填表单（编辑模式）
    LaunchedEffect(presetId) { viewModel.loadPresetForEdit(presetId) }

    // 保存成功 → 返回上一页
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PresetEvent.Saved -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (presetId == null) R.string.preset_edit_title_new
                            else R.string.preset_edit_title_existing
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.preset_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 名称
            OutlinedTextField(
                value = editState.name,
                onValueChange = { viewModel.onEditFieldChange(EditField.NAME, it) },
                label = { Text(stringResource(R.string.preset_name)) },
                isError = editState.nameError != null,
                supportingText = { editState.nameError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 焦距
            OutlinedTextField(
                value = editState.focalLengthText,
                onValueChange = { viewModel.onEditFieldChange(EditField.FOCAL_LENGTH, it) },
                label = { Text(stringResource(R.string.preset_focal_length)) },
                isError = editState.numericError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 传感器宽度
            OutlinedTextField(
                value = editState.sensorWidthText,
                onValueChange = { viewModel.onEditFieldChange(EditField.SENSOR_WIDTH, it) },
                label = { Text(stringResource(R.string.preset_sensor_width)) },
                isError = editState.numericError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 传感器高度
            OutlinedTextField(
                value = editState.sensorHeightText,
                onValueChange = { viewModel.onEditFieldChange(EditField.SENSOR_HEIGHT, it) },
                label = { Text(stringResource(R.string.preset_sensor_height)) },
                isError = editState.numericError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // HFOV 实时预览
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = editState.hfovPreview?.let { hfov ->
                        stringResource(R.string.preset_hfov_preview, hfov)
                    } ?: stringResource(R.string.preset_hfov_placeholder),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // 数值校验错误提示
            editState.numericError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 底部按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    enabled = !editState.isSaving
                ) {
                    Text(stringResource(R.string.preset_cancel))
                }
                Button(
                    onClick = { viewModel.validateAndSave() },
                    modifier = Modifier.weight(1f),
                    enabled = !editState.isSaving
                ) {
                    if (editState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.preset_save))
                    }
                }
            }
        }
    }
}
