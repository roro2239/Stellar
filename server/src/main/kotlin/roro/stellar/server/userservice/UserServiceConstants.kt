package roro.stellar.server.userservice

object UserServiceConstants {
    const val ARG_PACKAGE_NAME = "stellar:userservice-package"
    const val ARG_CLASS_NAME = "stellar:userservice-class"
    const val ARG_PROCESS_NAME_SUFFIX = "stellar:userservice-process-suffix"
    const val ARG_DEBUG = "stellar:userservice-debug"
    const val ARG_USE_32_BIT = "stellar:userservice-use32bit"
    const val ARG_VERSION_CODE = "stellar:userservice-version"
    const val ARG_TAG = "stellar:userservice-tag"
    const val ARG_SERVICE_MODE = "stellar:userservice-mode"
    const val ARG_USE_STANDALONE_DEX = "stellar:userservice-standalone-dex"
    const val ARG_VERIFICATION_TOKEN = "stellar:userservice-verification-token"

    const val MODE_ONE_TIME = 0
    const val MODE_DAEMON = 1

    const val OPT_TOKEN = "stellar:userservice-token"
    const val OPT_PACKAGE_NAME = "stellar:userservice-opt-package"
    const val OPT_CLASS_NAME = "stellar:userservice-opt-class"
    const val OPT_UID = "stellar:userservice-opt-uid"
    const val OPT_PID = "stellar:userservice-opt-pid"
    const val OPT_SERVICE_MODE = "stellar:userservice-opt-mode"
    const val OPT_USE_STANDALONE_DEX = "stellar:userservice-opt-standalone-dex"
    const val OPT_VERIFICATION_TOKEN = "stellar:userservice-opt-verification-token"

    const val ERROR_PERMISSION_DENIED = 1
    const val ERROR_PACKAGE_NOT_FOUND = 2
    const val ERROR_CLASS_NOT_FOUND = 3
    const val ERROR_PROCESS_START_FAILED = 4
    const val ERROR_SERVICE_TIMEOUT = 5
    const val ERROR_ALREADY_RUNNING = 6
    const val ERROR_INVALID_ARGS = 7
    const val ERROR_SIGNATURE_INVALID = 8
    const val ERROR_UNAUTHORIZED_LAUNCH = 9

    const val SERVICE_CONNECT_TIMEOUT = 10000L

    const val TRANSACTION_DESTROY = 0x00FFFFF1
    const val TRANSACTION_IS_ALIVE = 0x00FFFFF2
    const val TRANSACTION_GET_UID = 0x00FFFFF3
    const val TRANSACTION_GET_PID = 0x00FFFFF4

    const val USER_SERVICE_CMD_FORMAT =
        "(CLASSPATH='%s' %s%s /system/bin " +
        "--nice-name='%s' roro.stellar.server.userservice.UserServiceStarter " +
        "--token='%s' --package='%s' --class='%s' --uid=%d --mode=%d --standalone-dex=%b --verification-token='%s'%s)&"
}
