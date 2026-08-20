package com.huginprep.app.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huginprep.app.R
import com.huginprep.app.ui.preset.PresetSelectionDialog
import java.io.File
import java.util.Locale

/**
 * 实时拍摄页：全屏预览 + 顶部参数浮层 + 底部快门/参数/完成。
 *
 * @param onFinish 点击「完成」，回调本会话项目目录（供 ProjectPreviewScreen 使用）
 * @param onNewPreset 弹窗中点击「新建预设」，跳转到预设编辑页
 */
@Composable
fun CameraPreviewScreen(
    onFinish: (File) -> Unit,
    onNewPreset: () -> Unit,
    viewModel: CameraCaptureViewModel = viewModel(factory = CameraCaptureViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 相机权限
    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    // 本次会话的项目目录（pano_时间戳/images）
    val projectFolder = remember { viewModel.createProjectFolder(context) }

    // 离开页面时释放相机
    DisposableEffect(lifecycleOwner) {
        onDispose { viewModel.closeCamera() }
    }

    // 未授权则申请；已授权则启动相机
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            viewModel.startCamera(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ===== 全屏相机预览 =====
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { pv ->
                    pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                    viewModel.attachPreviewView(pv, lifecycleOwner)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ===== 顶部参数浮层（半透明） =====
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.selectedPresetName ?: stringResource(R.string.capture_no_preset),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(
                            R.string.capture_focal_hfov,
                            uiState.currentFocalLength?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                            uiState.currentHfov?.let { String.format(Locale.US, "%.1f", it) } ?: "-"
                        ),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // AE/AF 锁定开关
                IconButton(
                    onClick = {
                        if (uiState.isAeAfLocked) viewModel.unlockExposureAndFocus()
                        else viewModel.lockExposureAndFocus()
                    }
                ) {
                    Icon(
                        imageVector = if (uiState.isAeAfLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = stringResource(
                            if (uiState.isAeAfLocked) R.string.capture_unlock_aeaf
                            else R.string.capture_lock_aeaf
                        ),
                        tint = Color.White
                    )
                }
                Text(
                    text = stringResource(R.string.capture_count, uiState.capturedCount),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        // ===== 快门按钮（底部居中，按下缩放动画） =====
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val shutterScale by animateFloatAsState(
            targetValue = if (isPressed) 0.88f else 1f,
            label = "shutterScale"
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .size(76.dp)
                .graphicsLayer { scaleX = shutterScale; scaleY = shutterScale }
                .clip(CircleShape)
                .background(Color.White)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = uiState.isCameraReady && !uiState.isCapturing
                ) {
                    viewModel.captureImage(context, projectFolder)
                },
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color(0xFF999999), CircleShape)
                )
            }
        }

        // ===== 左下：参数按钮 =====
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 48.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f)
        ) {
            Text(
                text = stringResource(R.string.capture_params_button),
                modifier = Modifier
                    .clickable { viewModel.showPresetDialog() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }

        // ===== 右下：完成按钮 =====
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 48.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f)
        ) {
            Text(
                text = stringResource(R.string.capture_finish_button),
                modifier = Modifier
                    .clickable { onFinish(projectFolder) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }

        // ===== 相机启动失败 / 无相机 =====
        if (!uiState.isCameraReady && uiState.errorMessage != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = { viewModel.startCamera(context) }) {
                        Text(stringResource(R.string.capture_retry))
                    }
                }
            }
        }

        // ===== 未授予相机权限 =====
        if (!hasCameraPermission) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.capture_permission_needed),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.capture_permission_request))
                    }
                }
            }
        }
    }

    // ===== 预设选择弹窗 =====
    if (uiState.showPresetDialog) {
        PresetSelectionDialog(
            presets = uiState.presets,
            onPresetSelected = { viewModel.setPreset(it) },
            onNewPresetClick = {
                viewModel.dismissPresetDialog()
                onNewPreset()
            },
            onDismiss = { viewModel.dismissPresetDialog() }
        )
    }
}
