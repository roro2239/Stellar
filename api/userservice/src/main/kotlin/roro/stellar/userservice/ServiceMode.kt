package roro.stellar.userservice

enum class ServiceMode(val value: Int) {
    ONE_TIME(0),

    DAEMON(1);

    companion object {
        @JvmStatic
        fun fromValue(value: Int): ServiceMode {
            return entries.find { it.value == value } ?: ONE_TIME
        }
    }
}
