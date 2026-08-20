package com.huginprep.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.huginprep.app.R
import com.huginprep.app.ui.capture.CameraPreviewScreen
import com.huginprep.app.ui.donate.DonateScreen
import com.huginprep.app.ui.import.ImportScreen
import com.huginprep.app.ui.preset.PresetEditScreen
import com.huginprep.app.ui.preset.PresetListScreen
import com.huginprep.app.ui.preview.ProjectPreviewScreen
import com.huginprep.app.ui.settings.AboutScreen
import com.huginprep.app.ui.settings.SettingsScreen
import com.huginprep.app.update.UpdateViewModel
import java.io.File

/** 全局路由表 */
object Routes {
    // 底部导航四个 Tab
    const val CAPTURE = "capture"
    const val IMPORT = "import"
    const val PRESETS = "presets"
    const val SETTINGS = "settings"

    // 预设编辑（全屏，不在底部导航）
    const val PRESET_EDIT = "preset_edit"
    const val PRESET_EDIT_ARG = "presetId"
    const val PRESET_EDIT_PATTERN = "preset_edit/{presetId}"

    // 项目预览（全屏，不在底部导航）
    const val PROJECT_PREVIEW = "project_preview"
    const val PROJECT_PREVIEW_ARG = "projectPath"
    const val PROJECT_PREVIEW_PATTERN = "project_preview/{projectPath}"

    // 关于 / 打赏（全屏，不在底部导航）
    const val ABOUT = "about"
    const val DONATE = "donate"

    /** 新建（null）/ 编辑（presetId）预设页路由 */
    fun presetEdit(presetId: String?): String =
        if (presetId == null) PRESET_EDIT else "$PRESET_EDIT/$presetId"

    /** 项目预览路由：项目路径含 "/"，必须 Uri.encode 后作为参数传递 */
    fun projectPreview(projectPath: String): String =
        "$PROJECT_PREVIEW/${Uri.encode(projectPath)}"
}

/**
 * 应用导航：底部 4 个 Tab（拍摄 / 导入 / 预设 / 设置）+ 全屏页
 * （预设编辑、项目预览、关于、打赏，均不包含在底部导航中）。
 *
 * @param updateViewModel Activity 作用域的更新检查 ViewModel（设置页手动检查共用）
 */
@Composable
fun NavGraph(
    updateViewModel: UpdateViewModel,
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 仅四个 Tab 页面显示底部导航栏（全屏页自动隐藏）
    val showBottomBar = currentRoute in setOf(
        Routes.CAPTURE, Routes.IMPORT, Routes.PRESETS, Routes.SETTINGS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.CAPTURE,
                        onClick = { navController.navigateToTab(Routes.CAPTURE) },
                        icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_capture)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.IMPORT,
                        onClick = { navController.navigateToTab(Routes.IMPORT) },
                        icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_import)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.PRESETS,
                        onClick = { navController.navigateToTab(Routes.PRESETS) },
                        icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_presets)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { navController.navigateToTab(Routes.SETTINGS) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_settings)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CAPTURE,
            modifier = Modifier.padding(padding)
        ) {
            // ===== Tab 1：实时拍摄 =====
            composable(Routes.CAPTURE) {
                CameraPreviewScreen(
                    onFinish = { projectDir ->
                        navController.navigate(Routes.projectPreview(projectDir.absolutePath))
                    },
                    onNewPreset = {
                        navController.navigate(Routes.presetEdit(null))
                    }
                )
            }

            // ===== Tab 2：本地导入 =====
            composable(Routes.IMPORT) {
                ImportScreen(
                    onOpenProject = { projectDir ->
                        navController.navigate(Routes.projectPreview(projectDir.absolutePath))
                    },
                    onNewPreset = {
                        navController.navigate(Routes.presetEdit(null))
                    }
                )
            }

            // ===== Tab 3：预设管理 =====
            composable(Routes.PRESETS) {
                PresetListScreen(
                    onNavigateToEdit = { presetId ->
                        navController.navigate(Routes.presetEdit(presetId))
                    }
                )
            }

            // ===== Tab 4：设置 =====
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
                    onNavigateToDonate = { navController.navigate(Routes.DONATE) },
                    updateViewModel = updateViewModel
                )
            }

            // ===== 全屏：预设新建/编辑（presetId 可空） =====
            composable(
                route = Routes.PRESET_EDIT_PATTERN,
                arguments = listOf(
                    navArgument(Routes.PRESET_EDIT_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val presetId = entry.arguments?.getString(Routes.PRESET_EDIT_ARG)
                PresetEditScreen(
                    presetId = presetId,
                    onBack = { navController.popBackStack() }
                )
            }

            // ===== 全屏：项目预览/导出 =====
            composable(
                route = Routes.PROJECT_PREVIEW_PATTERN,
                arguments = listOf(
                    navArgument(Routes.PROJECT_PREVIEW_ARG) { type = NavType.StringType }
                )
            ) { entry ->
                val encoded = entry.arguments?.getString(Routes.PROJECT_PREVIEW_ARG)
                    ?: return@composable
                ProjectPreviewScreen(projectDir = File(Uri.decode(encoded)))
            }

            // ===== 全屏：关于 =====
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            // ===== 全屏：打赏 =====
            composable(Routes.DONATE) {
                DonateScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** 底部 Tab 切换：回到起始目的地并保存/恢复各 Tab 状态 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
