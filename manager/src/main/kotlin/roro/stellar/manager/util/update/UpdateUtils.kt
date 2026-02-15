package roro.stellar.manager.util.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class UpdateSource(val displayName: String) {
    GITHUB("GitHub"),
    GITEE("Gitee")
}

object UpdateUtils {

    private const val TAG = "UpdateUtils"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val GITHUB_API_URL =
        "https://api.github.com/repos/roro2239/Stellar/releases/latest"
    private const val GITEE_API_URL =
        "https://gitee.com/api/v5/repos/su-su2239/Stellar/releases/latest"

    private fun parseVersionCode(tagName: String): Int {
        val match = Regex("\\((\\d+)\\)").find(tagName)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    private fun parseVersionName(tagName: String): String {
        return tagName.removePrefix("v").replace(Regex("\\(\\d+\\)"), "")
    }

    private fun isInChina(): Boolean {
        return java.util.Locale.getDefault().country == "CN"
    }

    suspend fun getPreferredSource(): UpdateSource = withContext(Dispatchers.IO) {
        if (isInChina()) UpdateSource.GITEE else UpdateSource.GITHUB
    }

    suspend fun checkUpdate(source: UpdateSource? = null): AppUpdate? = withContext(Dispatchers.IO) {
        val actualSource = source ?: getPreferredSource()
        when (actualSource) {
            UpdateSource.GITHUB -> checkUpdateFromGitHub()
            UpdateSource.GITEE -> checkUpdateFromGitee()
        }
    }

    private suspend fun checkUpdateFromGitHub(): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "GitHub 获取更新信息失败，响应码: ${response.code}")
                return@withContext null
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e(TAG, "响应内容为空")
                return@withContext null
            }

            val json = JSONObject(responseBody)
            val tagName = json.optString("tag_name", "")
            val body = json.optString("body", "")
            val versionCode = parseVersionCode(tagName)
            val versionName = parseVersionName(tagName)

            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            if (assets != null && assets.length() > 0) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
                if (downloadUrl.isEmpty()) {
                    downloadUrl = assets.getJSONObject(0)
                        .optString("browser_download_url", "")
                }
            }

            if (versionCode > 0) {
                AppUpdate(versionName, versionCode, body, downloadUrl)
            } else {
                Log.e(TAG, "解析 versionCode 失败，tag: $tagName")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "GitHub 检查更新失败: ${e.message}", e)
            null
        }
    }

    private suspend fun checkUpdateFromGitee(): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITEE_API_URL)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Gitee 获取更新信息失败，响应码: ${response.code}")
                return@withContext null
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e(TAG, "响应内容为空")
                return@withContext null
            }

            val json = JSONObject(responseBody)
            val tagName = json.optString("tag_name", "")
            val body = json.optString("body", "")
            val versionCode = parseVersionCode(tagName)
            val versionName = parseVersionName(tagName)

            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            if (assets != null && assets.length() > 0) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
                if (downloadUrl.isEmpty()) {
                    downloadUrl = assets.getJSONObject(0)
                        .optString("browser_download_url", "")
                }
            }

            if (versionCode > 0) {
                AppUpdate(versionName, versionCode, body, downloadUrl)
            } else {
                Log.e(TAG, "解析 versionCode 失败，tag: $tagName")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gitee 检查更新失败: ${e.message}", e)
            null
        }
    }
}
