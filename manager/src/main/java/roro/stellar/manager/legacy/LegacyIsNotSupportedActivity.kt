package roro.stellar.manager.legacy

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import roro.stellar.manager.MainActivity
import roro.stellar.manager.ui.theme.StellarTheme

class LegacyIsNotSupportedActivity : ComponentActivity() {

    companion object {
        private inline val RESULT_CANCELED get() = Activity.RESULT_CANCELED
        private const val RESULT_ERROR = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callingComponent = callingActivity
        if (callingComponent == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val ai = try {
            packageManager.getApplicationInfo(callingComponent.packageName, PackageManager.GET_META_DATA)
        } catch (e: Throwable) {
            finish()
            return
        }

        val label = try {
            ai.loadLabel(packageManager).toString()
        } catch (e: Exception) {
            ai.packageName
        }

        val v3Support = ai.metaData?.getBoolean("com.stellar.client.V3_SUPPORT") == true

        setContent {
            StellarTheme {
                LegacyNotSupportedDialog(
                    appName = label,
                    v3Support = v3Support,
                    onDismiss = {
                        setResult(RESULT_ERROR)
                        finish()
                    },
                    onOpenStellar = {
                        startActivity(Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        setResult(RESULT_ERROR)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun LegacyNotSupportedDialog(
    appName: String,
    v3Support: Boolean,
    onDismiss: () -> Unit,
    onOpenStellar: () -> Unit
) {
    if (v3Support) {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = onOpenStellar) {
                    Text("打开 Stellar")
                }
            },
            title = {
                Text("$appName 正在请求旧式 Stellar")
            },
            text = {
                Text("$appName 支持现代 Stellar，但它正在请求旧式 Stellar。这可能是因为 Stellar 没有运行，请在 Stellar 应用内检查。\n\n旧式 Stellar 已于 2019 年 3 月被弃用。")
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("确定")
                }
            },
            title = {
                Text("$appName 不支持现代 Stellar")
            },
            text = {
                Text("旧式 Stellar 已于 2019 年 3 月被弃用。此外，旧式 Stellar 无法在较新版本的 Android 系统上工作。\n\n请要求 $appName 的开发者进行更新。")
            }
        )
    }
}

