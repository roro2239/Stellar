package roro.stellar.manager.ktx

import android.content.Context
import android.os.UserManager
import roro.stellar.manager.StellarApplication

val Context.application: StellarApplication
    get() = applicationContext as StellarApplication

fun Context.createDeviceProtectedStorageContextCompat(): Context =
    createDeviceProtectedStorageContext()

fun Context.createDeviceProtectedStorageContextCompatWhenLocked(): Context =
    if (getSystemService(UserManager::class.java)?.isUserUnlocked != true) {
        createDeviceProtectedStorageContext()
    } else {
        this
    }

