package roro.stellar.manager.util.update

data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val body: String,
    val downloadUrl: String
)
