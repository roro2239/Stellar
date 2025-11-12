package roro.stellar.manager.adb

import roro.stellar.manager.adb.AdbProtocol.A_AUTH
import roro.stellar.manager.adb.AdbProtocol.A_CLSE
import roro.stellar.manager.adb.AdbProtocol.A_CNXN
import roro.stellar.manager.adb.AdbProtocol.A_OKAY
import roro.stellar.manager.adb.AdbProtocol.A_OPEN
import roro.stellar.manager.adb.AdbProtocol.A_STLS
import roro.stellar.manager.adb.AdbProtocol.A_SYNC
import roro.stellar.manager.adb.AdbProtocol.A_WRTE
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ADB协议消息类
 * ADB Protocol Message Class
 * 
 * 功能说明 Features：
 * - 封装ADB协议消息格式 - Encapsulates ADB protocol message format
 * - 支持消息的序列化和反序列化 - Supports message serialization and deserialization
 * - 提供消息验证功能 - Provides message validation
 * - 自动计算CRC32校验和magic值 - Auto calculates CRC32 checksum and magic value
 * 
 * 消息格式 Message Format：
 * - 24字节固定头部 + 可变长度数据 - 24-byte fixed header + variable-length data
 * - Little-endian字节序 - Little-endian byte order
 * 
 * 头部字段 Header Fields：
 * - command: 命令类型（4字节）
 * - arg0: 参数0（4字节）
 * - arg1: 参数1（4字节）
 * - data_length: 数据长度（4字节）
 * - data_crc32: 数据CRC32校验和（4字节）
 * - magic: 校验值 = command XOR 0xFFFFFFFF（4字节）
 * - data: 可变长度数据
 * 
 * 验证机制 Validation Mechanism：
 * - magic值必须等于 command XOR 0xFFFFFFFF
 * - data_crc32必须等于实际数据的CRC32值
 * 
 * 使用场景 Use Cases：
 * - ADB连接握手
 * - ADB认证
 * - 数据传输
 * - TLS升级
 * 
 * @property command 命令类型
 * @property arg0 参数0（用途取决于命令类型）
 * @property arg1 参数1（用途取决于命令类型）
 * @property data_length 数据长度
 * @property data_crc32 数据CRC32校验和
 * @property magic 校验值
 * @property data 消息数据
 */
class AdbMessage(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val data_length: Int,
        val data_crc32: Int,
        val magic: Int,
        val data: ByteArray?
) {

    /**
     * 字符串数据构造函数
     * String data constructor
     * 
     * 自动添加null终止符
     */
    constructor(command: Int, arg0: Int, arg1: Int, data: String) : this(
            command,
            arg0,
            arg1,
            "$data\u0000".toByteArray())

    /**
     * 字节数组数据构造函数
     * Byte array data constructor
     * 
     * 自动计算data_length、data_crc32和magic值
     */
    constructor(command: Int, arg0: Int, arg1: Int, data: ByteArray?) : this(
            command,
            arg0,
            arg1,
            data?.size ?: 0,
            crc32(data),
            (command.toLong() xor 0xFFFFFFFF).toInt(),
            data)

    /**
     * 验证消息有效性
     * Validate message
     * 
     * @return true表示消息有效
     */
    fun validate(): Boolean {
        if (command != magic xor -0x1) return false
        if (data_length != 0 && crc32(data) != data_crc32) return false
        return true
    }

    /**
     * 验证消息或抛出异常
     * Validate message or throw exception
     * 
     * @throws IllegalArgumentException 如果消息无效
     */
    fun validateOrThrow() {
        if (!validate()) throw IllegalArgumentException("错误的消息 ${this.toStringShort()}")
    }

    /**
     * 序列化为字节数组
     * Serialize to byte array
     * 
     * @return 完整的消息字节数组（头部+数据）
     */
    fun toByteArray(): ByteArray {
        val length = HEADER_LENGTH + (data?.size ?: 0)
        return ByteBuffer.allocate(length).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putInt(command)
            putInt(arg0)
            putInt(arg1)
            putInt(data_length)
            putInt(data_crc32)
            putInt(magic)
            if (data != null) {
                put(data)
            }
        }.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AdbMessage

        if (command != other.command) return false
        if (arg0 != other.arg0) return false
        if (arg1 != other.arg1) return false
        if (data_length != other.data_length) return false
        if (data_crc32 != other.data_crc32) return false
        if (magic != other.magic) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = command
        result = 31 * result + arg0
        result = 31 * result + arg1
        result = 31 * result + data_length
        result = 31 * result + data_crc32
        result = 31 * result + magic
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "AdbMessage(${toStringShort()})"
    }

    /**
     * 生成简短的字符串表示
     * Generate short string representation
     * 
     * 将命令类型转换为可读的名称
     */
    fun toStringShort(): String {
        val commandString = when (command) {
            A_SYNC -> "A_SYNC"
            A_CNXN -> "A_CNXN"
            A_AUTH -> "A_AUTH"
            A_OPEN -> "A_OPEN"
            A_OKAY -> "A_OKAY"
            A_CLSE -> "A_CLSE"
            A_WRTE -> "A_WRTE"
            A_STLS -> "A_STLS"
            else -> command.toString()
        }
        return "command=$commandString, arg0=$arg0, arg1=$arg1, data_length=$data_length, data_crc32=$data_crc32, magic=$magic, data=${data?.contentToString()}"
    }

    companion object {

        /** 消息头部长度（字节） Message header length (bytes) */
        const val HEADER_LENGTH = 24

        /**
         * 计算简化的CRC32校验和
         * Calculate simplified CRC32 checksum
         * 
         * 注意：这是ADB协议特有的简化版CRC32，仅对数据字节求和
         * Note: This is a simplified CRC32 specific to ADB protocol, only sums data bytes
         * 
         * @param data 要计算校验和的数据
         * @return CRC32校验和
         */
        private fun crc32(data: ByteArray?): Int {
            if (data == null) return 0
            var res = 0
            for (b in data) {
                if (b >= 0)
                    res += b
                else
                    res += b + 256
            }
            return res
        }
    }
}

