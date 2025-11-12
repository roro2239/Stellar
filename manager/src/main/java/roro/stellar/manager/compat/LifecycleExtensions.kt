package roro.stellar.manager.compat

/**
 * Lifecycle辅助类
 * Lifecycle Helper Classes
 * 
 * 替代Rikka lifecycle功能
 * Replaces Rikka lifecycle functions
 */

/**
 * 资源状态枚举
 * Resource Status Enum
 */
enum class Status {
    /** 成功 Success */
    SUCCESS,
    /** 错误 Error */
    ERROR,
    /** 加载中 Loading */
    LOADING
}

/**
 * 资源包装类
 * Resource Wrapper Class
 * 
 * 用于包装网络/数据库请求结果
 * Used to wrap network/database request results
 */
data class Resource<out T>(
    val status: Status,
    val data: T?,
    val error: Throwable?
) {
    companion object {
        /**
         * 创建成功状态的资源
         * Create a success resource
         */
        fun <T> success(data: T?): Resource<T> {
            return Resource(Status.SUCCESS, data, null)
        }

        /**
         * 创建错误状态的资源
         * Create an error resource
         */
        fun <T> error(error: Throwable, data: T? = null): Resource<T> {
            return Resource(Status.ERROR, data, error)
        }

        /**
         * 创建加载中状态的资源
         * Create a loading resource
         */
        fun <T> loading(data: T? = null): Resource<T> {
            return Resource(Status.LOADING, data, null)
        }
    }
}

