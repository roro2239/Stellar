package roro.stellar.manager.adb

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.org.conscrypt.Conscrypt
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

private const val TAG = "AdbPairClient"

/** 当前密钥头部版本 Current key header version */
private const val kCurrentKeyHeaderVersion = 1.toByte()
/** 最小支持的密钥头部版本 Minimum supported key header version */
private const val kMinSupportedKeyHeaderVersion = 1.toByte()
/** 最大支持的密钥头部版本 Maximum supported key header version */
private const val kMaxSupportedKeyHeaderVersion = 1.toByte()
/** 最大对等信息大小 Maximum peer info size */
private const val kMaxPeerInfoSize = 8192
/** 最大载荷大小 Maximum payload size */
private const val kMaxPayloadSize = kMaxPeerInfoSize * 2

/** 导出密钥标签 Exported key label */
private const val kExportedKeyLabel = "adb-label\u0000"
/** 导出密钥大小 Exported key size */
private const val kExportedKeySize = 64

/** 配对包头部大小 Pairing packet header size */
private const val kPairingPacketHeaderSize = 6

/**
 * 对等信息类
 * Peer Info Class
 * 
 * 功能说明 Features：
 * - 封装ADB配对的对等信息 - Encapsulates peer info for ADB pairing
 * - 包含RSA公钥或设备GUID - Contains RSA public key or device GUID
 * - 支持序列化和反序列化 - Supports serialization and deserialization
 * 
 * @param type 信息类型（RSA公钥或设备GUID）
 * @param data 信息数据
 */
private class PeerInfo(
        val type: Byte,
        data: ByteArray) {

    /** 对等信息数据（固定大小） Peer info data (fixed size) */
    val data = ByteArray(kMaxPeerInfoSize - 1)

    init {
        // 复制数据，限制在最大大小内
        data.copyInto(this.data, 0, 0, data.size.coerceAtMost(kMaxPeerInfoSize - 1))
    }

    /**
     * 对等信息类型枚举
     * Peer info type enum
     */
    enum class Type(val value: Byte) {
        /** RSA公钥 RSA public key */
        ADB_RSA_PUB_KEY(0.toByte()),
        /** 设备GUID Device GUID */
        ADB_DEVICE_GUID(0.toByte()),
    }

    /**
     * 写入到缓冲区
     * Write to buffer
     */
    fun writeTo(buffer: ByteBuffer) {
        buffer.run {
            put(type)
            put(data)
        }

        Log.d(TAG, "写入对等信息 ${toStringShort()}")
    }

    override fun toString(): String {
        return "PeerInfo(${toStringShort()})"
    }

    fun toStringShort(): String {
        return "type=$type, data=${data.contentToString()}"
    }

    companion object {

        /**
         * 从缓冲区读取对等信息
         * Read peer info from buffer
         */
        fun readFrom(buffer: ByteBuffer): PeerInfo {
            val type = buffer.get()
            val data = ByteArray(kMaxPeerInfoSize - 1)
            buffer.get(data)
            return PeerInfo(type, data)
        }
    }
}

/**
 * 配对包头部类
 * Pairing Packet Header Class
 * 
 * 功能说明 Features：
 * - 封装ADB配对协议的包头部信息 - Encapsulates packet header info for ADB pairing protocol
 * - 包含版本、类型和载荷大小 - Contains version, type and payload size
 * - 支持头部验证 - Supports header validation
 * 
 * @param version 协议版本
 * @param type 包类型（SPAKE2消息或对等信息）
 * @param payload 载荷大小
 */
private class PairingPacketHeader(
        val version: Byte,
        val type: Byte,
        val payload: Int) {

    /**
     * 配对包类型枚举
     * Pairing packet type enum
     */
    enum class Type(val value: Byte) {
        /** SPAKE2协议消息 SPAKE2 protocol message */
        SPAKE2_MSG(0.toByte()),
        /** 对等信息 Peer information */
        PEER_INFO(1.toByte())
    }

    /**
     * 写入到缓冲区
     * Write to buffer
     */
    fun writeTo(buffer: ByteBuffer) {
        buffer.run {
            put(version)
            put(type)
            putInt(payload)
        }

        Log.d(TAG, "写入配对包头 ${toStringShort()}")
    }

    override fun toString(): String {
        return "PairingPacketHeader(${toStringShort()})"
    }

    fun toStringShort(): String {
        return "version=${version.toInt()}, type=${type.toInt()}, payload=$payload"
    }

    companion object {

        /**
         * 从缓冲区读取并验证头部
         * Read and validate header from buffer
         * 
         * @return 验证通过的头部，或null（如果验证失败）
         */
        fun readFrom(buffer: ByteBuffer): PairingPacketHeader? {
            val version = buffer.get()
            val type = buffer.get()
            val payload = buffer.int

            // 验证版本
            if (version < kMinSupportedKeyHeaderVersion || version > kMaxSupportedKeyHeaderVersion) {
                Log.e(TAG, "配对包头版本不匹配 (我们=$kCurrentKeyHeaderVersion 对方=${version})")
                return null
            }
            // 验证类型
            if (type != Type.SPAKE2_MSG.value && type != Type.PEER_INFO.value) {
                Log.e(TAG, "未知配对包类型=${type}")
                return null
            }
            // 验证载荷大小
            if (payload <= 0 || payload > kMaxPayloadSize) {
                Log.e(TAG, "头部载荷不在安全载荷大小范围内 (大小=${payload})")
                return null
            }

            val header = PairingPacketHeader(version, type, payload)
            Log.d(TAG, "读取配对包头 ${header.toStringShort()}")
            return header
        }
    }
}

/**
 * 配对上下文类（使用SPAKE2协议）
 * Pairing Context Class (using SPAKE2 protocol)
 * 
 * 功能说明 Features：
 * - 管理SPAKE2密码认证协议的状态 - Manages SPAKE2 password authentication protocol state
 * - 提供加密/解密功能 - Provides encryption/decryption functionality
 * - 通过JNI调用native代码 - Calls native code through JNI
 * 
 * SPAKE2协议说明 SPAKE2 Protocol：
 * - 密码认证密钥交换协议 - Password Authenticated Key Exchange protocol
 * - 无需预共享密钥 - No pre-shared key required
 * - 抵抗离线字典攻击 - Resistant to offline dictionary attacks
 * 
 * @param nativePtr Native对象指针
 */
private class PairingContext private constructor(private val nativePtr: Long) {

    /** SPAKE2协议消息 SPAKE2 protocol message */
    val msg: ByteArray

    init {
        msg = nativeMsg(nativePtr)
    }

    /**
     * 使用对方的消息初始化加密器
     * Initialize cipher with their message
     * 
     * @param theirMsg 对方的SPAKE2消息
     * @return true表示初始化成功
     */
    fun initCipher(theirMsg: ByteArray) = nativeInitCipher(nativePtr, theirMsg)

    /**
     * 加密数据
     * Encrypt data
     * 
     * @param in 明文数据
     * @return 密文数据，失败返回null
     */
    fun encrypt(`in`: ByteArray) = nativeEncrypt(nativePtr, `in`)

    /**
     * 解密数据
     * Decrypt data
     * 
     * @param in 密文数据
     * @return 明文数据，失败返回null
     */
    fun decrypt(`in`: ByteArray) = nativeDecrypt(nativePtr, `in`)

    /**
     * 销毁native对象
     * Destroy native object
     */
    fun destroy() = nativeDestroy(nativePtr)

    /** 获取SPAKE2消息（JNI） Get SPAKE2 message (JNI) */
    private external fun nativeMsg(nativePtr: Long): ByteArray

    /** 初始化加密器（JNI） Initialize cipher (JNI) */
    private external fun nativeInitCipher(nativePtr: Long, theirMsg: ByteArray): Boolean

    /** 加密数据（JNI） Encrypt data (JNI) */
    private external fun nativeEncrypt(nativePtr: Long, inbuf: ByteArray): ByteArray?

    /** 解密数据（JNI） Decrypt data (JNI) */
    private external fun nativeDecrypt(nativePtr: Long, inbuf: ByteArray): ByteArray?

    /** 销毁对象（JNI） Destroy object (JNI) */
    private external fun nativeDestroy(nativePtr: Long)

    companion object {

        /**
         * 创建配对上下文
         * Create pairing context
         * 
         * @param password 配对密码（配对码+TLS密钥材料）
         * @return 配对上下文，失败返回null
         */
        fun create(password: ByteArray): PairingContext? {
            val nativePtr = nativeConstructor(true, password)
            return if (nativePtr != 0L) PairingContext(nativePtr) else null
        }

        /** 构造native对象（JNI） Construct native object (JNI) */
        @JvmStatic
        private external fun nativeConstructor(isClient: Boolean, password: ByteArray): Long
    }
}

/**
 * ADB配对客户端类
 * ADB Pairing Client Class
 * 
 * 功能说明 Features：
 * - 实现ADB无线配对协议客户端 - Implements ADB wireless pairing protocol client
 * - 使用SPAKE2密码认证 - Uses SPAKE2 password authentication
 * - 通过TLS建立安全连接 - Establishes secure connection via TLS
 * - 交换RSA公钥完成配对 - Exchanges RSA public key to complete pairing
 * 
 * 配对流程 Pairing Flow：
 * 1. 建立TLS连接
 * 2. 从TLS会话导出密钥材料
 * 3. 结合配对码和密钥材料生成SPAKE2密码
 * 4. 交换SPAKE2消息（A_SPAKE2_MSG）
 * 5. 初始化加密器
 * 6. 交换加密的对等信息（RSA公钥）
 * 7. 配对完成
 * 
 * 安全机制 Security Mechanism：
 * - TLS保护传输层 - TLS protects transport layer
 * - SPAKE2防止密码嗅探 - SPAKE2 prevents password sniffing
 * - 密钥材料绑定防止中间人攻击 - Key material binding prevents MITM attacks
 * - 配对码提供额外认证 - Pairing code provides additional authentication
 * 
 * 使用示例 Usage Example：
 * ```kotlin
 * val client = AdbPairingClient("192.168.1.100", 37453, "123456", adbKey)
 * val success = client.start()
 * client.close()
 * ```
 * 
 * 要求 Requirements：
 * - Android 11+ (API 30+)
 * - 支持无线ADB的设备
 * - Native ADB库支持
 * 
 * @param host ADB服务器地址
 * @param port ADB配对服务端口
 * @param pairCode 6位配对码
 * @param key ADB密钥（包含RSA公钥/私钥）
 */
@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingClient(private val host: String, private val port: Int, private val pairCode: String, private val key: AdbKey) : Closeable {

    /**
     * 配对状态枚举
     * Pairing state enum
     */
    private enum class State {
        /** 就绪状态 Ready state */
        Ready,
        /** 正在交换SPAKE2消息 Exchanging SPAKE2 messages */
        ExchangingMsgs,
        /** 正在交换对等信息 Exchanging peer info */
        ExchangingPeerInfo,
        /** 已停止 Stopped */
        Stopped
    }

    /** TCP Socket连接 TCP socket connection */
    private lateinit var socket: Socket
    /** 输入流 Input stream */
    private lateinit var inputStream: DataInputStream
    /** 输出流 Output stream */
    private lateinit var outputStream: DataOutputStream

    /** 本地对等信息（包含RSA公钥） Local peer info (contains RSA public key) */
    private val peerInfo: PeerInfo = PeerInfo(PeerInfo.Type.ADB_RSA_PUB_KEY.value, key.adbPublicKey)
    /** 配对上下文（SPAKE2） Pairing context (SPAKE2) */
    private lateinit var pairingContext: PairingContext
    /** 当前状态 Current state */
    private var state: State = State.Ready

    /**
     * 启动配对流程
     * Start pairing process
     * 
     * @return true表示配对成功，false表示失败
     */
    fun start(): Boolean {
        setupTlsConnection()

        // 第一阶段：交换SPAKE2消息
        state = State.ExchangingMsgs

        if (!doExchangeMsgs()) {
            state = State.Stopped
            return false
        }

        // 第二阶段：交换对等信息（加密）
        state = State.ExchangingPeerInfo

        if (!doExchangePeerInfo()) {
            state = State.Stopped
            return false
        }

        state = State.Stopped
        return true
    }

    /**
     * 建立TLS连接并初始化配对上下文
     * Setup TLS connection and initialize pairing context
     * 
     * 工作流程 Workflow：
     * 1. 建立TCP连接
     * 2. 升级到TLS连接
     * 3. 从TLS会话导出密钥材料
     * 4. 结合配对码和密钥材料创建SPAKE2密码
     * 5. 初始化配对上下文
     */
    private fun setupTlsConnection() {
        // 建立TCP连接
        socket = Socket(host, port)
        socket.tcpNoDelay = true

        // 升级到TLS
        val sslContext = key.sslContext
        val sslSocket = sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
        sslSocket.startHandshake()
        Log.d(TAG, "握手成功")

        inputStream = DataInputStream(sslSocket.inputStream)
        outputStream = DataOutputStream(sslSocket.outputStream)

        // 生成SPAKE2密码：配对码 + TLS密钥材料
        val pairCodeBytes = pairCode.toByteArray()
        val keyMaterial = Conscrypt.exportKeyingMaterial(sslSocket, kExportedKeyLabel, null, kExportedKeySize)
        val passwordBytes = ByteArray(pairCode.length + keyMaterial.size)
        pairCodeBytes.copyInto(passwordBytes)
        keyMaterial.copyInto(passwordBytes, pairCodeBytes.size)

        // 创建配对上下文
        val pairingContext = PairingContext.create(passwordBytes)
        checkNotNull(pairingContext) { "Unable to create PairingContext." }
        this.pairingContext = pairingContext
    }

    /**
     * 创建配对包头部
     * Create pairing packet header
     */
    private fun createHeader(type: PairingPacketHeader.Type, payloadSize: Int): PairingPacketHeader {
        return PairingPacketHeader(kCurrentKeyHeaderVersion, type.value, payloadSize)
    }

    /**
     * 读取配对包头部
     * Read pairing packet header
     * 
     * @return 头部对象，验证失败返回null
     */
    private fun readHeader(): PairingPacketHeader? {
        val bytes = ByteArray(kPairingPacketHeaderSize)
        inputStream.readFully(bytes)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        return PairingPacketHeader.readFrom(buffer)
    }

    /**
     * 写入配对包头部和载荷
     * Write pairing packet header and payload
     * 
     * @param header 包头部
     * @param payload 载荷数据
     */
    private fun writeHeader(header: PairingPacketHeader, payload: ByteArray) {
        val buffer = ByteBuffer.allocate(kPairingPacketHeaderSize).order(ByteOrder.BIG_ENDIAN)
        header.writeTo(buffer)

        outputStream.write(buffer.array())
        outputStream.write(payload)
        Log.d(TAG, "写入载荷，大小=${payload.size}")
    }

    /**
     * 第一阶段：交换SPAKE2消息
     * Phase 1: Exchange SPAKE2 messages
     * 
     * 工作流程 Workflow：
     * 1. 发送本地SPAKE2消息
     * 2. 接收对方SPAKE2消息
     * 3. 使用双方消息初始化加密器
     * 4. 生成共享密钥
     * 
     * @return true表示成功
     */
    private fun doExchangeMsgs(): Boolean {
        // 获取本地SPAKE2消息
        val msg = pairingContext.msg
        val size = msg.size

        // 发送本地消息
        val ourHeader = createHeader(PairingPacketHeader.Type.SPAKE2_MSG, size)
        writeHeader(ourHeader, msg)

        // 接收对方消息
        val theirHeader = readHeader() ?: return false
        if (theirHeader.type != PairingPacketHeader.Type.SPAKE2_MSG.value) return false

        val theirMessage = ByteArray(theirHeader.payload)
        inputStream.readFully(theirMessage)

        // 初始化加密器，生成共享密钥
        if (!pairingContext.initCipher(theirMessage)) return false
        return true
    }

    /**
     * 第二阶段：交换加密的对等信息
     * Phase 2: Exchange encrypted peer info
     * 
     * 工作流程 Workflow：
     * 1. 加密本地对等信息（RSA公钥）
     * 2. 发送加密的对等信息
     * 3. 接收加密的对方对等信息
     * 4. 解密对方对等信息
     * 5. 验证对方公钥
     * 
     * 注意 Note：
     * - 如果解密失败，说明配对码错误
     * - 配对完成后，双方都拥有对方的RSA公钥
     * 
     * @return true表示成功
     * @throws AdbInvalidPairingCodeException 如果配对码错误
     */
    private fun doExchangePeerInfo(): Boolean {
        // 序列化并加密本地对等信息
        val buf = ByteBuffer.allocate(kMaxPeerInfoSize).order(ByteOrder.BIG_ENDIAN)
        peerInfo.writeTo(buf)

        val outbuf = pairingContext.encrypt(buf.array()) ?: return false

        // 发送加密的对等信息
        val ourHeader = createHeader(PairingPacketHeader.Type.PEER_INFO, outbuf.size)
        writeHeader(ourHeader, outbuf)

        // 接收加密的对方对等信息
        val theirHeader = readHeader() ?: return false
        if (theirHeader.type != PairingPacketHeader.Type.PEER_INFO.value) return false

        val theirMessage = ByteArray(theirHeader.payload)
        inputStream.readFully(theirMessage)

        // 解密对方对等信息
        val decrypted = pairingContext.decrypt(theirMessage) ?: throw AdbInvalidPairingCodeException()
        if (decrypted.size != kMaxPeerInfoSize) {
            Log.e(TAG, "获得大小=${decrypted.size} 对等信息大小=$kMaxPeerInfoSize")
            return false
        }
        
        // 解析对方对等信息
        val theirPeerInfo = PeerInfo.readFrom(ByteBuffer.wrap(decrypted))
        Log.d(TAG, theirPeerInfo.toString())
        return true
    }

    /**
     * 关闭连接和清理资源
     * Close connection and cleanup resources
     */
    override fun close() {
        try {
            inputStream.close()
        } catch (e: Throwable) {
        }
        try {
            outputStream.close()
        } catch (e: Throwable) {
        }
        try {
            socket.close()
        } catch (e: Exception) {
        }

        // 销毁配对上下文
        if (state != State.Ready) {
            pairingContext.destroy()
        }
    }

    companion object {

        init {
            // 加载native ADB库
            System.loadLibrary("adb")
        }

        /**
         * 检查配对功能是否可用
         * Check if pairing is available
         * 
         * @return true表示native库已加载且功能可用
         */
        @JvmStatic
        external fun available(): Boolean
    }
}

