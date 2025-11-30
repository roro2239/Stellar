package roro.stellar.manager.compat

enum class Status {
    SUCCESS,
    ERROR,
    LOADING
}

data class Resource<out T>(
    val status: Status,
    val data: T?,
    val error: Throwable?
) {
    companion object {
        fun <T> success(data: T?): Resource<T> {
            return Resource(Status.SUCCESS, data, null)
        }

        fun <T> error(error: Throwable, data: T? = null): Resource<T> {
            return Resource(Status.ERROR, data, error)
        }

        fun <T> loading(data: T? = null): Resource<T> {
            return Resource(Status.LOADING, data, null)
        }
    }
}

