package roro.stellar.manager.ktx

import android.content.Context
import android.os.UserManager
import roro.stellar.manager.StellarApplication

val Context.application: StellarApplication
    get() {
        return applicationContext as StellarApplication
    }

fun Context.createDeviceProtectedStorageContextCompat(): Context {
    return createDeviceProtectedStorageContext()
}

fun Context.createDeviceProtectedStorageContextCompatWhenLocked(): Context {
    return if (getSystemService(UserManager::class.java)?.isUserUnlocked != true) {
        createDeviceProtectedStorageContext()
    } else {
        this
    }
}

