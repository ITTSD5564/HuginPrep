package com.huginprep.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huginprep.app.data.AppDatabase
import com.huginprep.app.ui.navigation.NavGraph
import com.huginprep.app.update.UpdateViewModel
import com.huginprep.app.util.PermissionHelper
import com.huginprep.app.util.SettingsManager
import com.huginprep.app.util.rememberPermissionRequester
import java.io.File

/**
 * 应用入口：
 * 1. 初始化 Room 数据库单例与 projects/ 根目录；
 * 2. 组装 Compose 导航（底部 4 Tab + 全屏页）；
 * 3. 启动时申请相机权限，并按设置自动检查更新（弹窗全局显示）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化数据库单例（幂等）
        AppDatabase.getInstance(this)

        // 确保项目根目录存在（ProjectFolderManager 为无状态单例，目录由它统一管理）
        File(getExternalFilesDir(null), "projects").mkdirs()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    // Activity 作用域：设置页手动检查与启动自动检查共用同一实例
    val updateViewModel: UpdateViewModel = viewModel()

    var cameraPermissionGranted by rememberSaveable {
        mutableStateOf(PermissionHelper.hasCapturePermissions(context))
    }

    // 多权限请求器（PermissionHelper 提供）：全部授予 / 部分拒绝
    val requestPermissions = rememberPermissionRequester(
        onAllGranted = { cameraPermissionGranted = true },
        onDenied = {
            // 初版策略：拒绝后不强制，拍摄页会显示相机不可用提示
            cameraPermissionGranted = false
        }
    )

    LaunchedEffect(Unit) {
        // 首次进入自动申请相机权限
        if (!cameraPermissionGranted) {
            requestPermissions(PermissionHelper.capturePermissions())
        }
        // 启动时自动检查更新（设置中可关闭）
        if (SettingsManager.isAutoCheckUpdateEnabled(context)) {
            updateViewModel.checkForUpdate(context)
        }
    }

    // 更新检查结果弹窗（新版本 / 已最新 / 失败）
    UpdateDialogs(updateViewModel)

    NavGraph(updateViewModel = updateViewModel)
}

/** 更新检查结果弹窗：新版本可下载 / 已是最新 / 检查失败 */
@Composable
private fun UpdateDialogs(viewModel: UpdateViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 发现新版本 → 下载 / 稍后
    uiState.updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { Text(stringResource(R.string.update_dialog_title, info.latestVersion)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = info.releaseNotes ?: stringResource(R.string.update_dialog_no_notes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.startDownload(context) }) {
                    Text(stringResource(R.string.update_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdate() }) {
                    Text(stringResource(R.string.update_later))
                }
            }
        )
    }

    // 已是最新版本
    uiState.noUpdateMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissNoUpdate() },
            title = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissNoUpdate() }) {
                    Text(stringResource(R.string.update_ok))
                }
            }
        )
    }

    // 检查失败
    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text(stringResource(R.string.update_ok))
                }
            }
        )
    }
}
