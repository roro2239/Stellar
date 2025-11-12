package roro.stellar.manager.adb

import android.util.Log
import roro.stellar.manager.adb.AdbProtocol.ADB_AUTH_RSAPUBLICKEY
import roro.stellar.manager.adb.AdbProtocol.ADB_AUTH_SIGNATURE
import roro.stellar.manager.adb.AdbProtocol.ADB_AUTH_TOKEN
import roro.stellar.manager.adb.AdbProtocol.A_AUTH
import roro.stellar.manager.adb.AdbProtocol.A_CLSE
import roro.stellar.manager.adb.AdbProtocol.A_CNXN
import roro.stellar.manager.adb.AdbProtocol.A_MAXDATA
import roro.stellar.manager.adb.AdbProtocol.A_OKAY
import roro.stellar.manager.adb.AdbProtocol.A_OPEN
import roro.stellar.manager.adb.AdbProtocol.A_STLS
import roro.stellar.manager.adb.AdbProtocol.A_STLS_VERSION
import roro.stellar.manager.adb.AdbProtocol.A_VERSION
import roro.stellar.manager.adb.AdbProtocol.A_WRTE
import roro.stellar.manager.compat.BuildUtils
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

private const val TAG = "AdbClient"

/**
 * ADB客户端类
 * ADB Client Class
 * 
 * 功能说明 Features：
 * - 实现ADB协议客户端 - Implements ADB protocol client
 * - 支持TCP/IP连接到ADB服务器 - Supports TCP/IP connection to ADB server
 * - 支持TLS加密连接（Android 9+） - Supports TLS encrypted connection (Android 9+)
 * - 支持RSA密钥认证 - Supports RSA key authentication
 * - 提供Shell命令执行 - Provides shell command execution
 * - 提供Root切换 - Provides root switching
 * - 提供TCP/IP模式切换 - Provides TCP/IP mode switching
 * 
 * 连接流程 Connection Flow：
 * 1. 建立TCP连接到ADB服务器
 * 2. 发送CNXN（连接）消息
 * 3. 如果服务器支持，升级到TLS连接
 * 4. 如果需要，进行RSA认证
 * 5. 连接建立完成
 * 
 * TLS升级流程 TLS Upgrade Flow：
 * 1. 服务器发送STLS消息
 * 2. 客户端响应STLS确认
 * 3. 进行SSL握手
 * 4. 后续通信使用加密连接
 * 
 * 认证流程 Authentication Flow：
 * 1. 服务器发送AUTH消息（包含Token）
 * 2. 客户端使用私钥签名Token，发送签名
 * 3. 如果签名验证失败，发送公钥
 * 4. 服务器确认，发送CNXN消息
 * 
 * 使用示例 Usage Example：
 * ```kotlin
 * val client = AdbClient("127.0.0.1", 5555, adbKey)
 * client.connect()
 * client.shellCommand("ls -la") { data ->
 *     println(String(data))
 * }
 * client.close()
 * ```
 * 
 * @param host ADB服务器地址
 * @param port ADB服务器端口
 * @param key ADB密钥（用于认证和TLS）
 */
class AdbClient(private val host: String, private val port: Int, private val key: AdbKey) : Closeable {

    /** 普通Socket连接 Plain socket connection */
    private lateinit var socket: Socket
    /** 普通输入流 Plain input stream */
    private lateinit var plainInputStream: DataInputStream
    /** 普通输出流 Plain output stream */
    private lateinit var plainOutputStream: DataOutputStream

    /** 是否使用TLS Whether using TLS */
    private var useTls = false

    /** TLS Socket连接 TLS socket connection */
    private lateinit var tlsSocket: SSLSocket
    /** TLS输入流 TLS input stream */
    private lateinit var tlsInputStream: DataInputStream
    /** TLS输出流 TLS output stream */
    private lateinit var tlsOutputStream: DataOutputStream

    /** 当前使用的输入流（根据TLS状态选择） Current input stream (selected based on TLS status) */
    private val inputStream get() = if (useTls) tlsInputStream else plainInputStream
    /** 当前使用的输出流（根据TLS状态选择） Current output stream (selected based on TLS status) */
    private val outputStream get() = if (useTls) tlsOutputStream else plainOutputStream

    /**
     * 连接到ADB服务器
     * Connect to ADB server
     * 
     * 工作流程 Workflow：
     * 1. 建立TCP连接
     * 2. 发送连接请求（A_CNXN）
     * 3. 处理服务器响应：
     *    - A_STLS: 升级到TLS连接（Android 9+）
     *    - A_AUTH: 进行RSA认证
     *    - A_CNXN: 连接成功
     * 
     * @throws Exception 如果连接失败或认证失败
     */
    fun connect() {
        // 建立TCP连接
        socket = Socket(host, port)
        socket.tcpNoDelay = true
        plainInputStream = DataInputStream(socket.getInputStream())
        plainOutputStream = DataOutputStream(socket.getOutputStream())

        // 发送连接请求
        write(A_CNXN, A_VERSION, A_MAXDATA, "host::")

        var message = read()
        if (message.command == A_STLS) {
            // TLS升级流程（Android 9+）
            if (!BuildUtils.atLeast29) {
                error("Connect to adb with TLS is not supported before Android 9")
            }
            write(A_STLS, A_STLS_VERSION, 0)

            val sslContext = key.sslContext
            tlsSocket = sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
            tlsSocket.startHandshake()
            Log.d(TAG, "握手成功")

            tlsInputStream = DataInputStream(tlsSocket.inputStream)
            tlsOutputStream = DataOutputStream(tlsSocket.outputStream)
            useTls = true

            message = read()
        } else if (message.command == A_AUTH) {
            // RSA认证流程
            if (message.command != A_AUTH && message.arg0 != ADB_AUTH_TOKEN) error("not A_AUTH ADB_AUTH_TOKEN")
            // 发送签名
            write(A_AUTH, ADB_AUTH_SIGNATURE, 0, key.sign(message.data))

            message = read()
            if (message.command != A_CNXN) {
                // 如果签名验证失败，发送公钥
                write(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, key.adbPublicKey)
                message = read()
            }
        }

        // 验证连接成功
        if (message.command != A_CNXN) error("not A_CNXN")
    }

    /**
     * 执行Shell命令
     * Execute shell command
     * 
     * 工作流程 Workflow：
     * 1. 打开Shell服务（A_OPEN）
     * 2. 等待服务器确认（A_OKAY）
     * 3. 循环读取命令输出（A_WRTE）
     * 4. 等待流关闭（A_CLSE）
     * 
     * @param command Shell命令
     * @param listener 数据回调，接收命令输出
     * @throws Exception 如果命令执行失败
     */
    fun shellCommand(command: String, listener: ((ByteArray) -> Unit)?) {
        val localId = 1
        write(A_OPEN, localId, 0, "shell:$command")

        var message = read()
        when (message.command) {
            A_OKAY -> {
                // 命令被接受，开始接收输出
                while (true) {
                    message = read()
                    val remoteId = message.arg0
                    if (message.command == A_WRTE) {
                        // 接收命令输出数据
                        if (message.data_length > 0) {
                            listener?.invoke(message.data!!)
                        }
                        write(A_OKAY, localId, remoteId)
                    } else if (message.command == A_CLSE) {
                        // 流关闭，命令执行完成
                        write(A_CLSE, localId, remoteId)
                        break
                    } else {
                        error("not A_WRTE or A_CLSE")
                    }
                }
            }
            A_CLSE -> {
                // 命令立即关闭（可能是错误）
                val remoteId = message.arg0
                write(A_CLSE, localId, remoteId)
            }
            else -> {
                error("not A_OKAY or A_CLSE")
            }
        }
    }

    /**
     * 切换到Root权限
     * Switch to root privilege
     * 
     * 发送"root:"服务请求，让ADB守护进程以root身份重启
     * 
     * @param listener 数据回调，接收响应消息
     * @throws Exception 如果切换失败
     */
    fun root(listener: ((ByteArray) -> Unit)?) {
        val localId = 1
        write(A_OPEN, localId, 0, "root:")

        var message = read()
        when (message.command) {
            A_OKAY -> {
                // Root请求被接受
                while (true) {
                    message = read()
                    val remoteId = message.arg0
                    if (message.command == A_WRTE) {
                        // 接收响应消息
                        if (message.data_length > 0) {
                            listener?.invoke(message.data!!)
                        }
                        write(A_OKAY, localId, remoteId)
                    } else if (message.command == A_CLSE) {
                        write(A_CLSE, localId, remoteId)
                        break
                    } else {
                        error("not A_WRTE or A_CLSE")
                    }
                }
            }

            else -> {
                Log.e(TAG, "不是A_OKAY响应")
                error("not A_OKAY")
            }
        }
    }

    /**
     * 切换到TCP/IP模式
     * Switch to TCP/IP mode
     * 
     * 让ADB守护进程监听指定端口，用于无线ADB连接
     * 
     * @param port 监听端口号
     * @param listener 数据回调，接收响应消息
     * @throws Exception 如果切换失败
     */
    fun tcpip(port: Int, listener: ((ByteArray) -> Unit)?) {
        val localId = 1
        write(A_OPEN, localId, 0, "tcpip:$port")

        var message = read()
        when (message.command) {
            A_OKAY -> {
                // TCP/IP切换请求被接受
                while (true) {
                    message = read()
                    val remoteId = message.arg0
                    if (message.command == A_WRTE) {
                        // 接收响应消息
                        if (message.data_length > 0) {
                            listener?.invoke(message.data!!)
                        }
                        write(A_OKAY, localId, remoteId)
                    } else if (message.command == A_CLSE) {
                        write(A_CLSE, localId, remoteId)
                        break
                    } else {
                        error("not A_WRTE or A_CLSE")
                    }
                }
            }

            else -> {
                Log.e(TAG, "not A_OKAY")
                error("not A_OKAY")
            }
        }
    }

    /**
     * 写入消息（字节数组版本）
     * Write message (byte array version)
     */
    private fun write(command: Int, arg0: Int, arg1: Int, data: ByteArray? = null) = write(
        AdbMessage(command, arg0, arg1, data)
    )

    /**
     * 写入消息（字符串版本）
     * Write message (string version)
     */
    private fun write(command: Int, arg0: Int, arg1: Int, data: String) = write(
        AdbMessage(
            command,
            arg0,
            arg1,
            data
        )
    )

    /**
     * 写入ADB消息到输出流
     * Write ADB message to output stream
     * 
     * @param message 要发送的消息
     */
    private fun write(message: AdbMessage) {
        outputStream.write(message.toByteArray())
        outputStream.flush()
        Log.d(TAG, "写入 ${message.toStringShort()}")
    }

    /**
     * 从输入流读取ADB消息
     * Read ADB message from input stream
     * 
     * 工作流程 Workflow：
     * 1. 读取24字节消息头
     * 2. 解析头部字段
     * 3. 根据data_length读取消息数据
     * 4. 验证消息有效性
     * 
     * @return 接收到的ADB消息
     * @throws Exception 如果消息无效
     */
    private fun read(): AdbMessage {
        val buffer = ByteBuffer.allocate(AdbMessage.Companion.HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)

        // 读取消息头（24字节）
        inputStream.readFully(buffer.array(), 0, 24)

        // 解析头部字段
        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val dataLength = buffer.int
        val checksum = buffer.int
        val magic = buffer.int
        
        // 读取消息数据
        val data: ByteArray?
        if (dataLength >= 0) {
            data = ByteArray(dataLength)
            inputStream.readFully(data, 0, dataLength)
        } else {
            data = null
        }
        
        // 创建并验证消息
        val message = AdbMessage(command, arg0, arg1, dataLength, checksum, magic, data)
        message.validateOrThrow()
        Log.d(TAG, "读取 ${message.toStringShort()}")
        return message
    }

    /**
     * 关闭连接
     * Close connection
     * 
     * 清理所有资源，包括普通Socket和TLS Socket
     */
    override fun close() {
        // 关闭普通流
        try {
            plainInputStream.close()
        } catch (e: Throwable) {
        }
        try {
            plainOutputStream.close()
        } catch (e: Throwable) {
        }
        try {
            socket.close()
        } catch (e: Exception) {
        }

        // 关闭TLS流（如果使用）
        if (useTls) {
            try {
                tlsInputStream.close()
            } catch (e: Throwable) {
            }
            try {
                tlsOutputStream.close()
            } catch (e: Throwable) {
            }
            try {
                tlsSocket.close()
            } catch (e: Exception) {
            }
        }
    }
}

