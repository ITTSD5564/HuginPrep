package com.huginprep.app.ui.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 项目预览 / 导出页：展示项目摘要、照片缩略图、参数卡片，
 * 支持重新生成 .pto、导出 ZIP 并分享。
 *
 * @param projectDir 项目根目录（拍摄完成或导入完成后跳转进入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPreviewScreen(
    projectDir: File,
    viewModel: ProjectPreviewViewModel = viewModel(factory = ProjectPreviewViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 加载项目（扫描图片 + 解析预设）
    LaunchedEffect(projectDir) { viewModel.loadProject(projectDir) }

    // 分享 Intent 一次性消费
    LaunchedEffect(uiState.shareIntent) {
        uiState.shareIntent?.let {
            context.startActivity(it)
            viewModel.clearShareIntent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.projectName.ifBlank { stringResource(R.string.project_title) })
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
            // ===== 项目摘要 =====
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.project_photo_count, uiState.totalPhotos),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.project_preset,
                            uiState.preset?.name ?: stringResource(R.string.project_no_preset)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== 参数摘要卡片 =====
            uiState.preset?.let { preset ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.project_params_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.project_params,
                                preset.focalLength,
                                preset.sensorWidth,
                                preset.sensorHeight,
                                preset.hfov
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // ===== 缩略图网格（每行 3 张） =====
            if (uiState.imageFiles.isNotEmpty()) {
                uiState.imageFiles.chunked(3).forEach { rowFiles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowFiles.forEach { file ->
                            FileThumbnail(
                                file = file,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.project_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ===== 生成状态提示 =====
            uiState.ptoFile?.let {
                Text(
                    text = stringResource(R.string.project_pto_generated),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            uiState.zipFile?.let {
                Text(
                    text = stringResource(R.string.project_zip_ready),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ===== 操作按钮 =====
            Button(
                onClick = { uiState.preset?.let { viewModel.generatePtoAndExif(projectDir, it) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.preset != null && !uiState.isGenerating
            ) {
                Text(
                    if (uiState.isGenerating) stringResource(R.string.project_generating)
                    else stringResource(R.string.project_regenerate_pto)
                )
            }
            Button(
                onClick = { viewModel.exportAndShare(projectDir, context) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.totalPhotos > 0 && !uiState.isZipping
            ) {
                Text(
                    if (uiState.isZipping) stringResource(R.string.project_zipping)
                    else stringResource(R.string.project_export_zip)
                )
            }
            if (uiState.zipFile != null) {
                TextButton(
                    onClick = { viewModel.shareZip(projectDir, context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.project_share_again))
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
}

/** 本地文件缩略图（采样解码，避免 OOM） */
@Composable
private fun FileThumbnail(file: File, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val sample = maxOf(1, bounds.outWidth / 512, bounds.outHeight / 512)
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }.getOrNull()
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
