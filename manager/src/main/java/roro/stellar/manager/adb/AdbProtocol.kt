package roro.stellar.manager.adb

/**
 * ADB协议常量定义
 * ADB Protocol Constants Definition
 * 
 * 功能说明 Features：
 * - 定义ADB协议的所有命令常量 - Defines all ADB protocol command constants
 * - 定义协议版本和参数 - Defines protocol version and parameters
 * - 定义认证类型常量 - Defines authentication type constants
 * 
 * 命令类型 Command Types：
 * - A_SYNC: 同步命令 - Synchronize command
 * - A_CNXN: 连接命令 - Connect command
 * - A_AUTH: 认证命令 - Authentication command
 * - A_OPEN: 打开流命令 - Open stream command
 * - A_OKAY: 确认命令 - Acknowledge command
 * - A_CLSE: 关闭流命令 - Close stream command
 * - A_WRTE: 写入数据命令 - Write data command
 * - A_STLS: 启动TLS命令 - Start TLS command
 * 
 * 认证类型 Authentication Types：
 * - ADB_AUTH_TOKEN: Token认证
 * - ADB_AUTH_SIGNATURE: 签名认证
 * - ADB_AUTH_RSAPUBLICKEY: RSA公钥认证
 * 
 * 协议参数 Protocol Parameters：
 * - A_VERSION: 协议版本 0x01000000
 * - A_MAXDATA: 最大数据长度 4096字节
 * - A_STLS_VERSION: TLS版本 0x01000000
 * 
 * 参考资料 References：
 * - ADB Protocol Specification
 * - Android Debug Bridge documentation
 */
object AdbProtocol {

    /** SYNC命令：同步 SYNC command */
    const val A_SYNC = 0x434e5953
    /** CNXN命令：连接 CNXN command */
    const val A_CNXN = 0x4e584e43
    /** AUTH命令：认证 AUTH command */
    const val A_AUTH = 0x48545541
    /** OPEN命令：打开流 OPEN command */
    const val A_OPEN = 0x4e45504f
    /** OKAY命令：确认 OKAY command */
    const val A_OKAY = 0x59414b4f
    /** CLSE命令：关闭流 CLSE command */
    const val A_CLSE = 0x45534c43
    /** WRTE命令：写入数据 WRTE command */
    const val A_WRTE = 0x45545257
    /** STLS命令：启动TLS STLS command */
    const val A_STLS = 0x534C5453

    /** ADB协议版本 ADB protocol version */
    const val A_VERSION = 0x01000000
    /** 最大数据长度 Maximum data length */
    const val A_MAXDATA = 4096

    /** STLS协议版本 STLS protocol version */
    const val A_STLS_VERSION = 0x01000000

    /** 认证类型：Token ADB auth type: token */
    const val ADB_AUTH_TOKEN = 1
    /** 认证类型：签名 ADB auth type: signature */
    const val ADB_AUTH_SIGNATURE = 2
    /** 认证类型：RSA公钥 ADB auth type: RSA public钥 */
    const val ADB_AUTH_RSAPUBLICKEY = 3
}

