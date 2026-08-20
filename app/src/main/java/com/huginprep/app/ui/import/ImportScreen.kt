package com.huginprep.app.ui.import

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huginprep.app.R
import com.huginprep.app.ui.preset.PresetSelectionDialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地照片导入页。
 *
 * @param onOpenProject 导入完成后点击「查看项目」，跳转到项目预览页
 * @param onNewPreset 弹窗中点击「新建预设」，跳转到预设编辑页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onOpenProject: (File) -> Unit,
    onNewPreset: () -> Unit,
    viewModel: LocalImportViewModel = viewModel(factory = LocalImportViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 系统照片选择器（Photo Picker，Android 13+ 原生，旧版本自动降级）
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> viewModel.onPhotosPicked(uris) }

    // 监听 ViewModel 的 pick 请求并触发系统选择器
    LaunchedEffect(Unit) {
        viewModel.pickRequests.collect {
            pickLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                actions = {
                    if (uiState.selectedUris.isNotEmpty() && !uiState.isProcessing) {
                        TextButton(onClick = { viewModel.clearSelection() }) {
                            Text(stringResource(R.string.import_clear))
                        }
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
            // ===== 选择照片 =====
            Button(
                onClick = { viewModel.pickPhotos() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isProcessing
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.import_pick_photos))
            }

            // ===== 缩略图网格（最多 6 张，其余显示 +N） =====
            if (uiState.selectedUris.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.import_selected_count, uiState.selectedUris.size),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.selectedUris.take(6).forEach { uri ->
                        Thumbnail(
                            uri = uri,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.medium)
                        )
                    }
                    if (uiState.selectedUris.size > 6) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.import_more_count, uiState.selectedUris.size - 6),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // ===== 预设显示 + 选择入口 =====
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.import_preset_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = uiState.selectedPreset?.name ?: stringResource(R.string.import_no_preset),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(onClick = { viewModel.showPresetDialog() }) {
                        Text(stringResource(R.string.import_choose_preset))
                    }
                }
            }

            // ===== 导入按钮 / 进度 =====
            if (uiState.isProcessing) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.import_progress, uiState.importedCount, uiState.selectedUris.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Button(
                    onClick = { viewModel.applyPresetToPhotos(context) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.selectedPreset != null && uiState.selectedUris.isNotEmpty()
                ) {
                    Text(stringResource(R.string.import_apply_and_import))
                }
                if (uiState.selectedPreset == null && uiState.selectedUris.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.import_error_no_preset),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ===== 导入完成 → 查看项目 =====
            uiState.importedProjectDir?.let { dir ->
                Button(
                    onClick = { onOpenProject(dir) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.import_open_project))
                }
            }

            // ===== 错误提示 =====
            uiState.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    // ===== 预设选择弹窗（选择照片后自动弹出） =====
    if (uiState.showPresetDialog) {
        PresetSelectionDialog(
            presets = uiState.presets,
            onPresetSelected = { viewModel.selectPreset(it) },
            onNewPresetClick = {
                viewModel.dismissPresetDialog()
                onNewPreset()
            },
            onDismiss = { viewModel.dismissPresetDialog() }
        )
    }
}

/**
 * 通过 content:// Uri 异步加载压缩后的缩略图。
 * 使用 ImageDecoder（API 28+）/ BitmapFactory 采样（API 26-27），避免大图 OOM。
 */
@Composable
private fun Thumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadThumbnail(context, uri) }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

/** 加载压缩后的缩略图（最长边约 [targetSize] px） */
private fun loadThumbnail(context: Context, uri: Uri, targetSize: Int = 512): Bitmap? {
    val resolver = context.contentResolver
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(resolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(targetSize, targetSize)
        }
    } else {
        // API 26/27：先读边界计算采样率，再解码，避免一次性解出全尺寸图
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = maxOf(1, bounds.outWidth / targetSize, bounds.outHeight / targetSize)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
