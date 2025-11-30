package roro.stellar.manager.ui.features.settings.update

data class AppUpdate(
    val versionCode: Int,
    val url: String
)

fun AppUpdate.isNewerThan(currentVersionCode: Int): Boolean {
    return this.versionCode > currentVersionCode
}

