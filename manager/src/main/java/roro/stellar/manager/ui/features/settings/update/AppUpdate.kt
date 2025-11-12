package roro.stellar.manager.ui.features.settings.update

/**
 * 应用更新数据模型
 */
data class AppUpdate(
    val versionCode: Int,
    val url: String
)

/**
 * 检查是否有新版本
 */
fun AppUpdate.isNewerThan(currentVersionCode: Int): Boolean {
    return this.versionCode > currentVersionCode
}

