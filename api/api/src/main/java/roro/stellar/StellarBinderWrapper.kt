package roro.stellar

import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import java.io.FileDescriptor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Stellar Binder包装器
 * Stellar Binder Wrapper
 *
 *
 * 功能说明 Features：
 *
 *  * 包装原始Binder，使其通过Stellar服务进行事务 - Wraps original Binder to transact via Stellar service
 *  * 简化系统服务调用 - Simplifies system service calls
 *  * 自动处理Stellar协议版本差异 - Automatically handles Stellar protocol version differences
 *
 *
 *
 * 使用示例 Usage Example：
 * <pre>
 * // 获取PackageManager服务
 * IBinder binder = StellarBinderWrapper.getSystemService("package");
 * IPackageManager pm = IPackageManager.Stub.asInterface(
 * new StellarBinderWrapper(binder)
 * );
 *
 * // 现在可以直接调用IPackageManager的方法
 * List&lt;PackageInfo&gt; packages = pm.getInstalledPackages(0, 0);
</pre> *
 *
 *
 * 工作原理 How it works：
 *
 *  * 拦截所有transact调用 - Intercepts all transact calls
 *  * 通过Stellar.transactRemote转发 - Forwards via Stellar.transactRemote
 *  * 在服务端执行实际操作 - Executes actual operation on service side
 *
 *
 *
 * 注意事项 Notes：
 *
 *  * 需要Stellar服务运行且权限已授予 - Requires Stellar service running and permission granted
 *  * 支持Stellar API版本13及以上 - Supports Stellar API version 13 and above
 *
 */

/** @param original [IBinder] 原始Binder对象 Original Binder object  */
class StellarBinderWrapper(private val original: IBinder) : IBinder {

    /**
     * 执行Binder事务
     * Perform Binder transaction
     *
     *
     * 此方法拦截事务调用，并通过Stellar服务转发到原始Binder
     *
     * This method intercepts transaction calls and forwards to original Binder via Stellar service
     *
     * @param code 事务代码 Transaction code
     * @param data 输入数据 Input data
     * @param reply 回复数据 Reply data
     * @param flags 标志位 Flags
     * @return 总是返回true Always returns true
     * @throws RemoteException 远程调用异常
     */
    @Throws(RemoteException::class)
    override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        // 构造新的数据包

        val newData = Parcel.obtain()
        try {
            // 写入Stellar协议头
            newData.writeInterfaceToken(StellarApiConstants.BINDER_DESCRIPTOR)
            newData.writeStrongBinder(original)
            newData.writeInt(code)
            newData.writeInt(flags)

            // 追加原始数据
            newData.appendFrom(data, 0, data.dataSize())

            Stellar.transactRemote(newData, reply, 0)
        } finally {
            newData.recycle()
        }
        return true
    }

    /**
     * 获取接口描述符
     * Get interface descriptor
     *
     * @return 接口描述符
     * @throws RemoteException 远程调用异常
     */
    @Throws(RemoteException::class)
    override fun getInterfaceDescriptor(): String? {
        return original.interfaceDescriptor
    }

    /**
     * Ping Binder检查是否存活
     * Ping Binder to check if alive
     *
     * @return true表示存活
     */
    override fun pingBinder(): Boolean {
        return original.pingBinder()
    }

    /**
     * 检查Binder是否存活
     * Check if Binder is alive
     *
     * @return true表示存活
     */
    override fun isBinderAlive(): Boolean {
        return original.isBinderAlive
    }

    /**
     * 查询本地接口
     * Query local interface
     *
     * @param descriptor 接口描述符
     * @return 总是返回null（因为这是远程Binder）
     */
    override fun queryLocalInterface(descriptor: String): IInterface? {
        return null
    }

    /**
     * 导出Binder状态
     * Dump Binder state
     *
     * @param fd 文件描述符
     * @param args 参数
     * @throws RemoteException 远程调用异常
     */
    @Throws(RemoteException::class)
    override fun dump(fd: FileDescriptor, args: Array<String?>?) {
        original.dump(fd, args)
    }

    /**
     * 异步导出Binder状态
     * Dump Binder state asynchronously
     *
     * @param fd 文件描述符
     * @param args 参数
     * @throws RemoteException 远程调用异常
     */
    @Throws(RemoteException::class)
    override fun dumpAsync(fd: FileDescriptor, args: Array<String?>?) {
        original.dumpAsync(fd, args)
    }

    /**
     * 链接死亡通知
     * Link death notification
     *
     * @param recipient 死亡接收者
     * @param flags 标志位
     * @throws RemoteException 远程调用异常
     */
    @Throws(RemoteException::class)
    override fun linkToDeath(recipient: DeathRecipient, flags: Int) {
        original.linkToDeath(recipient, flags)
    }

    /**
     * 取消链接死亡通知
     * Unlink death notification
     *
     * @param recipient 死亡接收者
     * @param flags 标志位
     * @return true表示取消成功
     */
    override fun unlinkToDeath(recipient: DeathRecipient, flags: Int): Boolean {
        return original.unlinkToDeath(recipient, flags)
    }

    companion object {

        /** 系统服务Binder缓存 System service Binder cache  */
        private val SYSTEM_SERVICE_CACHE = HashMap<String?, IBinder?>()

        /** 事务代码缓存 Transaction code cache  */
        private val TRANSACT_CODE_CACHE = HashMap<String?, Int?>()

        /** ServiceManager.getService反射方法  */
        private val getService: Method? = try {
            // 反射获取ServiceManager类和getService方法
            val sm = Class.forName("android.os.ServiceManager")
            sm.getMethod("getService", java.lang.String::class.java)
        } catch (e: ClassNotFoundException) {
            Log.w("StellarBinderWrapper", Log.getStackTraceString(e))
            null
        } catch (e: NoSuchMethodException) {
            Log.w("StellarBinderWrapper", Log.getStackTraceString(e))
            null
        }

        fun getSystemService(name: String): IBinder? {
            var binder = SYSTEM_SERVICE_CACHE[name]
            if (binder == null) {
                try {
                    binder = getService?.invoke(null, name) as IBinder?
                } catch (e: IllegalAccessException) {
                    Log.w("StellarBinderWrapper", Log.getStackTraceString(e))
                } catch (e: InvocationTargetException) {
                    Log.w("StellarBinderWrapper", Log.getStackTraceString(e))
                }
                SYSTEM_SERVICE_CACHE[name] = binder
            }
            return binder
        }
    }
}