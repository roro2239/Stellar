package roro.stellar.manager.adb

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

private const val TAG = "AdbKey"

/**
 * ADB密钥管理类
 * ADB Key Management Class
 * 
 * 功能说明 Features：
 * - 管理ADB认证所需的RSA密钥对 - Manages RSA key pair for ADB authentication
 * - 使用Android KeyStore安全存储密钥 - Uses Android KeyStore for secure key storage
 * - 提供数据签名功能 - Provides data signing functionality
 * - 生成ADB特定格式的公钥 - Generates ADB-specific format public key
 * - 提供TLS/SSL上下文 - Provides TLS/SSL context
 * 
 * 密钥存储方案 Key Storage Scheme：
 * - RSA私钥使用AES-GCM加密后存储 - RSA private key stored encrypted with AES-GCM
 * - AES密钥存储在Android KeyStore中 - AES key stored in Android KeyStore
 * - 支持密钥的持久化和恢复 - Supports key persistence and recovery
 * 
 * 密钥规格 Key Specifications：
 * - RSA密钥长度：2048位 - RSA key length: 2048 bits
 * - RSA指数：F4 (65537)
 * - AES加密：GCM模式，256位密钥
 * - IV大小：12字节
 * - 认证标签：16字节
 * 
 * 使用场景 Use Cases：
 * - ADB连接认证
 * - ADB配对
 * - TLS连接
 * 
 * @param adbKeyStore 密钥存储后端
 * @param name 密钥名称（用于公钥标识）
 */
class AdbKey(private val adbKeyStore: AdbKeyStore, name: String) {

    companion object {

        /** Android KeyStore提供者名称 Android KeyStore provider name */
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        /** 加密密钥别名 Encryption key alias */
        private const val ENCRYPTION_KEY_ALIAS = "_adbkey_encryption_key_"
        /** 加密转换方式 Encryption transformation */
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** IV大小（字节） IV size in bytes */
        private const val IV_SIZE_IN_BYTES = 12
        /** 认证标签大小（字节） Authentication tag size in bytes */
        private const val TAG_SIZE_IN_BYTES = 16

        /**
         * PKCS#1填充数据
         * PKCS#1 padding data
         * 
         * 用于RSA签名的填充，符合ADB协议要求
         * Used for RSA signature padding, conforming to ADB protocol requirements
         */
        private val PADDING = byteArrayOf(
                0x00, 0x01, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x00,
                0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00,
                0x04, 0x14)
    }

    /** AES加密密钥（存储在Android KeyStore中） AES encryption key (stored in Android KeyStore) */
    private val encryptionKey: Key

    /** RSA私钥 RSA private key */
    private val privateKey: RSAPrivateKey
    /** RSA公钥 RSA public key */
    private val publicKey: RSAPublicKey
    /** X.509证书 X.509 certificate */
    private val certificate: X509Certificate

    init {
        // 获取或创建AES加密密钥
        this.encryptionKey = getOrCreateEncryptionKey() ?: error("Failed to generate encryption key with AndroidKeyManager.")

        // 获取或创建RSA私钥
        this.privateKey = getOrCreatePrivateKey()
        // 从私钥生成公钥
        this.publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(privateKey.modulus, RSAKeyGenParameterSpec.F4)) as RSAPublicKey

        // 生成自签名证书（用于TLS）
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
        val x509Certificate = X509v3CertificateBuilder(X500Name("CN=00"),
                BigInteger.ONE,
                Date(0),
                Date(2461449600 * 1000),
                Locale.ROOT,
                X500Name("CN=00"),
                SubjectPublicKeyInfo.getInstance(publicKey.encoded)
        ).build(signer)
        this.certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(x509Certificate.encoded)) as X509Certificate

        Log.d(TAG, privateKey.toString())
    }

    /**
     * ADB格式的公钥
     * ADB format public key
     * 
     * 懒加载的公钥，按照ADB协议要求的格式编码
     */
    val adbPublicKey: ByteArray by lazy {
        publicKey.adbEncoded(name)
    }

    /**
     * 获取或创建AES加密密钥
     * Get or create AES encryption key
     * 
     * 工作流程 Workflow：
     * 1. 尝试从Android KeyStore获取现有密钥
     * 2. 如果不存在，生成新的AES-256密钥
     * 3. 密钥存储在KeyStore中，受系统保护
     * 
     * @return AES密钥，失败返回null
     */
    private fun getOrCreateEncryptionKey(): Key? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        return keyStore.getKey(ENCRYPTION_KEY_ALIAS, null) ?: run {
            // 创建新的AES密钥
            val parameterSpec = KeyGenParameterSpec.Builder(ENCRYPTION_KEY_ALIAS, KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            keyGenerator.init(parameterSpec)
            keyGenerator.generateKey()
        }
    }

    /**
     * 使用AES-GCM加密数据
     * Encrypt data using AES-GCM
     * 
     * 格式 Format：[IV(12字节)][密文][认证标签(16字节)]
     * 
     * @param plaintext 明文数据
     * @param aad 附加认证数据（可选）
     * @return 加密后的数据，失败返回null
     */
    private fun encrypt(plaintext: ByteArray, aad: ByteArray?): ByteArray? {
        if (plaintext.size > Int.MAX_VALUE - IV_SIZE_IN_BYTES - TAG_SIZE_IN_BYTES) {
            return null
        }
        val ciphertext = ByteArray(IV_SIZE_IN_BYTES + plaintext.size + TAG_SIZE_IN_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
        cipher.updateAAD(aad)
        cipher.doFinal(plaintext, 0, plaintext.size, ciphertext, IV_SIZE_IN_BYTES)
        System.arraycopy(cipher.iv, 0, ciphertext, 0, IV_SIZE_IN_BYTES)
        return ciphertext
    }

    /**
     * 使用AES-GCM解密数据
     * Decrypt data using AES-GCM
     * 
     * @param ciphertext 密文数据（包含IV和认证标签）
     * @param aad 附加认证数据（可选）
     * @return 解密后的数据，失败返回null
     */
    private fun decrypt(ciphertext: ByteArray, aad: ByteArray?): ByteArray? {
        if (ciphertext.size < IV_SIZE_IN_BYTES + TAG_SIZE_IN_BYTES) {
            return null
        }
        val params = GCMParameterSpec(8 * TAG_SIZE_IN_BYTES, ciphertext, 0, IV_SIZE_IN_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, params)
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext, IV_SIZE_IN_BYTES, ciphertext.size - IV_SIZE_IN_BYTES)
    }

    /**
     * 获取或创建RSA私钥
     * Get or create RSA private key
     * 
     * 工作流程 Workflow：
     * 1. 尝试从存储中加载加密的私钥
     * 2. 使用AES-GCM解密私钥
     * 3. 如果不存在或解密失败，生成新的RSA密钥对
     * 4. 加密并保存新生成的私钥
     * 
     * 密钥规格 Key Specifications：
     * - 密钥长度：2048位
     * - 公共指数：F4 (65537)
     * - 编码格式：PKCS8
     * 
     * @return RSA私钥
     */
    private fun getOrCreatePrivateKey(): RSAPrivateKey {
        var privateKey: RSAPrivateKey? = null

        // 准备附加认证数据
        val aad = ByteArray(16)
        "adbkey".toByteArray().copyInto(aad)

        // 尝试加载现有密钥
        var ciphertext = adbKeyStore.get()
        if (ciphertext != null) {
            try {
                val plaintext = decrypt(ciphertext, aad)

                val keyFactory = KeyFactory.getInstance("RSA")
                privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(plaintext)) as RSAPrivateKey
            } catch (e: Exception) {
                // 解密失败，需要生成新密钥
            }
        }
        
        // 如果没有现有密钥，生成新密钥
        if (privateKey == null) {
            val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
            keyPairGenerator.initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
            val keyPair = keyPairGenerator.generateKeyPair()
            privateKey = keyPair.private as RSAPrivateKey

            // 加密并保存私钥
            ciphertext = encrypt(privateKey.encoded, aad)
            if (ciphertext != null) {
                adbKeyStore.put(ciphertext)
            }
        }
        return privateKey
    }

    /**
     * 使用RSA私钥签名数据
     * Sign data using RSA private key
     * 
     * 使用PKCS#1填充和RSA私钥签名数据，符合ADB认证协议要求
     * 
     * @param data 要签名的数据
     * @return 签名结果
     */
    fun sign(data: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        cipher.update(PADDING)
        return cipher.doFinal(data)
    }

    /**
     * X.509密钥管理器
     * X.509 Key Manager
     * 
     * 功能说明 Features：
     * - 为TLS连接提供客户端证书和私钥 - Provides client certificate and private key for TLS connection
     * - 支持RSA密钥类型 - Supports RSA key type
     * - 使用自签名证书 - Uses self-signed certificate
     */
    private val keyManager
        get() = object : X509ExtendedKeyManager() {
            private val alias = "key"

            override fun chooseClientAlias(keyTypes: Array<out String>, issuers: Array<out Principal>?, socket: Socket?): String? {
                Log.d(TAG, "选择客户端别名: keyType=${keyTypes.contentToString()}, issuers=${issuers?.contentToString()}")
                for (keyType in keyTypes) {
                    if (keyType == "RSA") return alias
                }
                return null
            }

            override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
                Log.d(TAG, "获取证书链: alias=$alias")
                return if (alias == this.alias) arrayOf(certificate) else null
            }

            override fun getPrivateKey(alias: String?): PrivateKey? {
                Log.d(TAG, "获取私钥: alias=$alias")
                return if (alias == this.alias) privateKey else null
            }

            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? {
                return null
            }

            override fun getServerAliases(keyType: String, issuers: Array<out Principal>?): Array<String>? {
                return null
            }

            override fun chooseServerAlias(keyType: String, issuers: Array<out Principal>?, socket: Socket?): String? {
                return null
            }
        }

    /**
     * X.509信任管理器
     * X.509 Trust Manager
     * 
     * 功能说明 Features：
     * - 信任所有证书（用于ADB配对） - Trusts all certificates (for ADB pairing)
     * - 不进行证书验证 - No certificate validation
     * 
     * 注意 Note：
     * 这是ADB协议的特殊要求，实际的安全性由SPAKE2密码认证保证
     * This is a special requirement of ADB protocol, actual security is ensured by SPAKE2 password authentication
     */
    private val trustManager
        get() =
            @RequiresApi(Build.VERSION_CODES.R)
            object : X509ExtendedTrustManager() {

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
                }

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
                }

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                }

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
                }

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
                }

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return emptyArray()
                }
            }

    /**
     * SSL上下文
     * SSL Context
     * 
     * 功能说明 Features：
     * - 提供TLSv1.3上下文 - Provides TLSv1.3 context
     * - 配置密钥管理器和信任管理器 - Configures key manager and trust manager
     * - 用于ADB配对的TLS连接 - Used for TLS connection in ADB pairing
     * 
     * 要求 Requirements：
     * - Android 11+ (API 30+)
     */
    @delegate:RequiresApi(Build.VERSION_CODES.R)
    val sslContext: SSLContext by lazy {
        val sslContext = SSLContext.getInstance("TLSv1.3")
        sslContext.init(arrayOf(keyManager), arrayOf(trustManager), SecureRandom())
        sslContext
    }
}

/**
 * ADB密钥存储接口
 * ADB Key Store Interface
 * 
 * 功能说明 Features：
 * - 定义密钥存储的抽象接口 - Defines abstract interface for key storage
 * - 支持密钥的保存和读取 - Supports key save and load
 * - 可以有不同的实现（SharedPreferences、文件等） - Can have different implementations
 */
interface AdbKeyStore {

    /**
     * 保存密钥（加密后的字节数组）
     * Save key (encrypted byte array)
     */
    fun put(bytes: ByteArray)

    /**
     * 读取密钥（加密后的字节数组）
     * Load key (encrypted byte array)
     * 
     * @return 密钥数据，如果不存在返回null
     */
    fun get(): ByteArray?
}

/**
 * 基于SharedPreferences的ADB密钥存储实现
 * SharedPreferences-based ADB Key Store Implementation
 * 
 * 功能说明 Features：
 * - 使用SharedPreferences存储密钥 - Uses SharedPreferences to store key
 * - 密钥使用Base64编码存储 - Key stored with Base64 encoding
 * - 适合简单的密钥存储需求 - Suitable for simple key storage needs
 * 
 * @param preference SharedPreferences实例
 */
class PreferenceAdbKeyStore(private val preference: SharedPreferences) : AdbKeyStore {

    /** SharedPreferences键名 SharedPreferences key name */
    private val preferenceKey = "adbkey"

    /**
     * 保存密钥到SharedPreferences
     * Save key to SharedPreferences
     * 
     * 密钥使用Base64编码后存储为字符串
     */
    override fun put(bytes: ByteArray) {
        preference.edit { putString(preferenceKey, String(Base64.encode(bytes, Base64.NO_WRAP))) }
    }

    /**
     * 从SharedPreferences读取密钥
     * Load key from SharedPreferences
     * 
     * 从Base64字符串解码为字节数组
     */
    override fun get(): ByteArray? {
        if (!preference.contains(preferenceKey)) return null
        return Base64.decode(preference.getString(preferenceKey, null), Base64.NO_WRAP)
    }
}

/** RSA模数大小（字节） RSA modulus size in bytes */
const val ANDROID_PUBKEY_MODULUS_SIZE = 2048 / 8
/** RSA模数大小（32位字） RSA modulus size in 32-bit words */
const val ANDROID_PUBKEY_MODULUS_SIZE_WORDS = ANDROID_PUBKEY_MODULUS_SIZE / 4
/** ADB格式RSA公钥大小 ADB format RSA public key size */
const val RSAPublicKey_Size = 524

/**
 * 将BigInteger转换为ADB特定的编码格式
 * Convert BigInteger to ADB-specific encoding format
 * 
 * ADB使用小端序32位字数组表示大整数
 * ADB uses little-endian 32-bit word array to represent big integers
 * 
 * @return 32位整数数组
 */
private fun BigInteger.toAdbEncoded(): IntArray {
    val endcoded = IntArray(ANDROID_PUBKEY_MODULUS_SIZE_WORDS)
    val r32 = BigInteger.ZERO.setBit(32)

    var tmp = this.add(BigInteger.ZERO)
    for (i in 0 until ANDROID_PUBKEY_MODULUS_SIZE_WORDS) {
        val out = tmp.divideAndRemainder(r32)
        tmp = out[0]
        endcoded[i] = out[1].toInt()
    }
    return endcoded
}

/**
 * 将RSA公钥编码为ADB特定格式
 * Encode RSA public key to ADB-specific format
 * 
 * 功能说明 Features：
 * - 符合ADB协议的公钥格式要求 - Complies with ADB protocol public key format requirements
 * - 使用Montgomery模乘优化 - Uses Montgomery multiplication optimization
 * - 包含密钥名称标识 - Includes key name identifier
 * 
 * 格式 Format：
 * - ANDROID_PUBKEY_MODULUS_SIZE_WORDS（4字节）
 * - n0inv（4字节，Montgomery参数）
 * - modulus（256字节，小端序32位字数组）
 * - rr（256字节，Montgomery参数）
 * - exponent（4字节）
 * - 结果进行Base64编码，并附加密钥名称
 * 
 * @param name 密钥名称（用于标识）
 * @return ADB格式的公钥字节数组
 */
private fun RSAPublicKey.adbEncoded(name: String): ByteArray {
    // 计算Montgomery参数
    val r32 = BigInteger.ZERO.setBit(32)
    val n0inv = modulus.remainder(r32).modInverse(r32).negate()
    val r = BigInteger.ZERO.setBit(ANDROID_PUBKEY_MODULUS_SIZE * 8)
    val rr = r.modPow(BigInteger.valueOf(2), modulus)

    // 构建ADB公钥格式
    val buffer = ByteBuffer.allocate(RSAPublicKey_Size).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(ANDROID_PUBKEY_MODULUS_SIZE_WORDS)
    buffer.putInt(n0inv.toInt())
    modulus.toAdbEncoded().forEach { buffer.putInt(it) }
    rr.toAdbEncoded().forEach { buffer.putInt(it) }
    buffer.putInt(publicExponent.toInt())

    // Base64编码并添加密钥名称
    val base64Bytes = Base64.encode(buffer.array(), Base64.NO_WRAP)
    val nameBytes = " $name\u0000".toByteArray()
    val bytes = ByteArray(base64Bytes.size + nameBytes.size)
    base64Bytes.copyInto(bytes)
    nameBytes.copyInto(bytes, base64Bytes.size)
    return bytes
}

