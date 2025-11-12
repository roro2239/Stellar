package roro.stellar.manager.adb

/**
 * 抛出ADB异常的便捷函数
 * Convenient function to throw ADB exception
 * 
 * @param message 错误消息
 * @return Nothing - 此函数总是抛出异常
 */
@Suppress("NOTHING_TO_INLINE")
inline fun adbError(message: Any): Nothing = throw AdbException(message.toString())

/**
 * ADB异常基类
 * ADB Exception Base Class
 * 
 * 功能说明 Features：
 * - ADB相关操作的通用异常类 - General exception class for ADB-related operations
 * - 提供多种构造函数重载 - Provides multiple constructor overloads
 * - 支持异常链 - Supports exception chaining
 * 
 * 使用场景 Use Cases：
 * - ADB连接失败
 * - ADB命令执行错误
 * - ADB协议错误
 */
open class AdbException : Exception {

    constructor(message: String, cause: Throwable?) : super(message, cause)
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
    constructor()
}

/**
 * ADB配对码无效异常
 * ADB Invalid Pairing Code Exception
 * 
 * 使用场景 Use Cases：
 * - 用户输入的配对码格式错误
 * - 配对码与服务端不匹配
 * - 配对验证失败
 */
class AdbInvalidPairingCodeException : AdbException()

/**
 * ADB密钥异常
 * ADB Key Exception
 * 
 * 使用场景 Use Cases：
 * - 密钥生成失败
 * - KeyStore损坏
 * - 密钥加载失败
 * - 加密/解密操作失败
 */
class AdbKeyException(cause: Throwable) : AdbException(cause)

