package com.huginprep.app.ui.donate

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.huginprep.app.R
import kotlinx.coroutines.launch

/**
 * 打赏页（纯 Compose）：
 * - 展示微信 / 支付宝收款码（微信为真实赞赏码 qr_wechat.png；支付宝暂未开通，
 *   占位图 qr_alipay.xml 待替换）；
 * - 点击卡片唤起对应 App（weixin:// / alipays://，未安装时提示保存二维码手动扫码）；
 * - 「保存二维码」将图片写入系统相册。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.donate_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.preset_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.donate_thanks),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.donate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // ===== 微信（赞赏码） =====
            DonateCard(
                title = stringResource(R.string.donate_wechat),
                qrRes = R.drawable.qr_wechat,
                hint = stringResource(R.string.donate_wechat_hint),
                onClick = {
                    openScheme(context, "weixin://") {
                        showMessage(context.getString(R.string.donate_app_missing))
                    }
                },
                onSaveQr = {
                    if (saveQrToGallery(context, R.drawable.qr_wechat, "huginprep_wechat_qr.png")) {
                        showMessage(context.getString(R.string.donate_saved))
                    }
                }
            )

            // ===== 支付宝（暂未开通） =====
            DonateCard(
                title = stringResource(R.string.donate_alipay),
                qrRes = R.drawable.qr_alipay,
                hint = stringResource(R.string.donate_alipay_hint),
                onClick = {
                    showMessage(context.getString(R.string.donate_alipay_hint))
                },
                onSaveQr = null
            )
        }
    }
}

/** 打赏卡片：收款码 + 提示语 + 保存二维码 */
@Composable
private fun DonateCard(
    title: String,
    qrRes: Int,
    hint: String,
    onClick: () -> Unit,
    onSaveQr: (() -> Unit)? = null
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Image(
                painter = painterResource(qrRes),
                contentDescription = title,
                modifier = Modifier.size(180.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (onSaveQr != null) {
                TextButton(onClick = onSaveQr) {
                    Text(stringResource(R.string.donate_save_qr))
                }
            }
        }
    }
}

/** 尝试用 scheme 唤起 App；无可用 App 时回调 onMissing */
private fun openScheme(context: Context, uri: String, onMissing: () -> Unit) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
    if (intent.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(intent) }
            .onFailure { onMissing() }
    } else {
        onMissing()
    }
}

/** 将 drawable 矢量图栅格化后保存到系统相册 */
private fun saveQrToGallery(context: Context, drawableRes: Int, fileName: String): Boolean =
    runCatching {
        val drawable = ContextCompat.getDrawable(context, drawableRes) ?: return false
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return false
        context.contentResolver.openOutputStream(uri)?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        true
    }.getOrDefault(false)
