package roro.stellar.manager.ui.features.settings.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri

object UpdateUtils {
    
    private const val TAG = "UpdateUtils"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private const val BASE_URL = "https://gitee.com/api/v5/repos/lovelmxa/stellar-service/contents/"
    private const val ACCESS_TOKEN = "538b3ffc6d4e462525e80850122a455e"
    
    suspend fun checkUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val url = "${BASE_URL}update/update.json?access_token=$ACCESS_TOKEN"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "获取更新信息失败，响应码: ${response.code}")
                return@withContext null
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e(TAG, "响应内容为空")
                return@withContext null
            }
            
            val base64Content = extractBase64Content(responseBody)
            if (base64Content == null) {
                Log.e(TAG, "提取base64内容失败")
                return@withContext null
            }
            
            val decodedContent = String(Base64.decode(base64Content, Base64.DEFAULT))
            Log.d(TAG, "解码后的更新信息: $decodedContent")
            
            val versionCode = Regex(""""version_code"\s*:\s*(\d+)""").find(decodedContent)?.groupValues?.get(1)?.toIntOrNull()
            val downloadUrl = Regex(""""url"\s*:\s*"([^"]+)"""").find(decodedContent)?.groupValues?.get(1)
            
            if (versionCode != null && !downloadUrl.isNullOrEmpty()) {
                AppUpdate(versionCode, downloadUrl)
            } else {
                Log.e(TAG, "解析更新信息失败")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败: ${e.message}", e)
            null
        }
    }
    
    private fun extractBase64Content(responseBody: String): String? {
        return try {
            val startIndex = responseBody.indexOf("\"content\":\"") + 11
            val endIndex = responseBody.indexOf("\"", startIndex)
            if (startIndex > 10 && endIndex > startIndex) {
                responseBody.substring(startIndex, endIndex).replace("\\n", "")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析Base64内容失败", e)
            null
        }
    }
    
    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        try {
            val downloadDir = File(context.filesDir, "download")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val apkFile = File(downloadDir, "update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }
            
            Log.d(TAG, "开始下载更新: $downloadUrl")
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "开始下载更新", Toast.LENGTH_SHORT).show()
            }
            
            val request = Request.Builder()
                .url(downloadUrl)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "下载失败，响应码: ${response.code}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "下载失败，请检查网络连接", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }
            
            response.body.let { responseBody ->
                val totalBytes = responseBody?.contentLength()
                var downloadedBytes = 0L
                
                FileOutputStream(apkFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    val inputStream = responseBody?.byteStream()

                    if (inputStream != null) {
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            totalBytes?.let {
                                if (it > 0) {
                                    val progress = (downloadedBytes * 100 / totalBytes).toInt()
                                    withContext(Dispatchers.Main) {
                                        onProgress(progress)
                                    }
                                }
                            }
                        }
                    }

                    outputStream.flush()
                }

                Log.d(TAG, "下载完成，文件大小: ${apkFile.length()} 字节")

                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新失败: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "更新失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun hasInstallPermission(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }
    
    fun requestInstallPermission(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(context, "请授予安装未知应用权限", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "跳转安装权限设置失败: ${e.message}", e)
            Toast.makeText(context, "无法打开设置页面", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun installApk(context: Context, apkFile: File) {
        try {
            if (!hasInstallPermission(context)) {
                Log.w(TAG, "没有安装权限，跳转设置页面")
                requestInstallPermission(context)
                return
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            context.startActivity(intent)
            
            Log.d(TAG, "启动安装器")
        } catch (e: Exception) {
            Log.e(TAG, "安装失败: ${e.message}", e)
            Toast.makeText(context, "安装失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

