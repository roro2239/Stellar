package roro.stellar.userservice

import android.os.Bundle

data class UserServiceArgs(
    val className: String,
    val processNameSuffix: String = "userservice",
    val debug: Boolean = false,
    val use32Bit: Boolean = false,
    val versionCode: Long = 0,
    val tag: String? = null,
    val serviceMode: ServiceMode = ServiceMode.ONE_TIME,
    val useStandaloneDex: Boolean = false
) {
    companion object {
        internal const val ARG_PACKAGE_NAME = "stellar:userservice-package"
        internal const val ARG_CLASS_NAME = "stellar:userservice-class"
        internal const val ARG_PROCESS_NAME_SUFFIX = "stellar:userservice-process-suffix"
        internal const val ARG_DEBUG = "stellar:userservice-debug"
        internal const val ARG_USE_32_BIT = "stellar:userservice-use32bit"
        internal const val ARG_VERSION_CODE = "stellar:userservice-version"
        internal const val ARG_TAG = "stellar:userservice-tag"
        internal const val ARG_SERVICE_MODE = "stellar:userservice-mode"
        internal const val ARG_VERIFICATION_TOKEN = "stellar:userservice-verification-token"
    }

    fun toBundle(packageName: String): Bundle {
        return Bundle().apply {
            putString(ARG_PACKAGE_NAME, packageName)
            putString(ARG_CLASS_NAME, className)
            putString(ARG_PROCESS_NAME_SUFFIX, processNameSuffix)
            putBoolean(ARG_DEBUG, debug)
            putBoolean(ARG_USE_32_BIT, use32Bit)
            putLong(ARG_VERSION_CODE, versionCode)
            tag?.let { putString(ARG_TAG, it) }
            putInt(ARG_SERVICE_MODE, serviceMode.value)
        }
    }

    class Builder(private val serviceClass: Class<*>) {
        private var processNameSuffix: String = "userservice"
        private var debug: Boolean = false
        private var use32Bit: Boolean = false
        private var versionCode: Long = 0
        private var tag: String? = null
        private var serviceMode: ServiceMode = ServiceMode.ONE_TIME
        private var useStandaloneDex: Boolean = false

        fun processNameSuffix(suffix: String) = apply { this.processNameSuffix = suffix }
        fun debug(debug: Boolean) = apply { this.debug = debug }
        fun use32Bit(use32Bit: Boolean) = apply { this.use32Bit = use32Bit }
        fun versionCode(code: Long) = apply { this.versionCode = code }
        fun tag(tag: String) = apply { this.tag = tag }
        fun serviceMode(mode: ServiceMode) = apply { this.serviceMode = mode }
        fun useStandaloneDex(use: Boolean) = apply { this.useStandaloneDex = use }

        fun build(): UserServiceArgs {
            return UserServiceArgs(
                className = serviceClass.name,
                processNameSuffix = processNameSuffix,
                debug = debug,
                use32Bit = use32Bit,
                versionCode = versionCode,
                tag = tag,
                serviceMode = serviceMode,
                useStandaloneDex = useStandaloneDex
            )
        }
    }
}
