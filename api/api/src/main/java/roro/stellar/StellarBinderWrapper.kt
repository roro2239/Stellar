package roro.stellar;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileDescriptor;
import java.util.Objects;

/**
 * Stellar Binder包装器
 * Stellar Binder Wrapper
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>包装原始Binder，使其通过Stellar服务进行事务 - Wraps original Binder to transact via Stellar service</li>
 * <li>简化系统服务调用 - Simplifies system service calls</li>
 * <li>自动处理Stellar协议版本差异 - Automatically handles Stellar protocol version differences</li>
 * </ul>
 * 
 * <p>使用示例 Usage Example：</p>
 * <pre>
 * // 获取PackageManager服务
 * IBinder binder = SystemServiceHelper.getSystemService("package");
 * IPackageManager pm = IPackageManager.Stub.asInterface(
 *     new StellarBinderWrapper(binder)
 * );
 * 
 * // 现在可以直接调用IPackageManager的方法
 * List&lt;PackageInfo&gt; packages = pm.getInstalledPackages(0, 0);
 * </pre>
 * 
 * <p>工作原理 How it works：</p>
 * <ul>
 * <li>拦截所有transact调用 - Intercepts all transact calls</li>
 * <li>通过Stellar.transactRemote转发 - Forwards via Stellar.transactRemote</li>
 * <li>在服务端执行实际操作 - Executes actual operation on service side</li>
 * </ul>
 * 
 * <p>注意事项 Notes：</p>
 * <ul>
 * <li>需要Stellar服务运行且权限已授予 - Requires Stellar service running and permission granted</li>
 * <li>支持Stellar API版本13及以上 - Supports Stellar API version 13 and above</li>
 * </ul>
 */
public class StellarBinderWrapper implements IBinder {

    /** 原始Binder对象 Original Binder object */
    private final IBinder original;

    /**
     * 构造Binder包装器
     * 
     * @param original 要包装的原始Binder对象
     */
    public StellarBinderWrapper(@NonNull IBinder original) {
        this.original = Objects.requireNonNull(original);
    }

    /**
     * 执行Binder事务
     * Perform Binder transaction
     * 
     * <p>此方法拦截事务调用，并通过Stellar服务转发到原始Binder</p>
     * <p>This method intercepts transaction calls and forwards to original Binder via Stellar service</p>
     * 
     * @param code 事务代码 Transaction code
     * @param data 输入数据 Input data
     * @param reply 回复数据 Reply data
     * @param flags 标志位 Flags
     * @return 总是返回true Always returns true
     * @throws RemoteException 远程调用异常
     */
    @Override
    public boolean transact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
        // 检查Stellar服务版本
        boolean atLeast13 = Stellar.getVersion() >= 13;

        // 构造新的数据包
        Parcel newData = Parcel.obtain();
        try {
            // 写入Stellar协议头
            newData.writeInterfaceToken(StellarApiConstants.BINDER_DESCRIPTOR);
            newData.writeStrongBinder(original);
            newData.writeInt(code);
            
            // API版本13+需要在数据包中包含flags
            if (atLeast13) {
                newData.writeInt(flags);
            }
            
            // 追加原始数据
            newData.appendFrom(data, 0, data.dataSize());
            
            // 根据版本调用不同方式
            if (atLeast13) {
                Stellar.transactRemote(newData, reply, 0);
            } else {
                Stellar.transactRemote(newData, reply, flags);
            }
        } finally {
            newData.recycle();
        }
        return true;
    }

    /**
     * 获取接口描述符
     * Get interface descriptor
     * 
     * @return 接口描述符
     * @throws RemoteException 远程调用异常
     */
    @Nullable
    @Override
    public String getInterfaceDescriptor() throws RemoteException {
        return original.getInterfaceDescriptor();
    }

    /**
     * Ping Binder检查是否存活
     * Ping Binder to check if alive
     * 
     * @return true表示存活
     */
    @Override
    public boolean pingBinder() {
        return original.pingBinder();
    }

    /**
     * 检查Binder是否存活
     * Check if Binder is alive
     * 
     * @return true表示存活
     */
    @Override
    public boolean isBinderAlive() {
        return original.isBinderAlive();
    }

    /**
     * 查询本地接口
     * Query local interface
     * 
     * @param descriptor 接口描述符
     * @return 总是返回null（因为这是远程Binder）
     */
    @Nullable
    @Override
    public IInterface queryLocalInterface(@NonNull String descriptor) {
        return null;
    }

    /**
     * 导出Binder状态
     * Dump Binder state
     * 
     * @param fd 文件描述符
     * @param args 参数
     * @throws RemoteException 远程调用异常
     */
    @Override
    public void dump(@NonNull FileDescriptor fd, @Nullable String[] args) throws RemoteException {
        original.dump(fd, args);
    }

    /**
     * 异步导出Binder状态
     * Dump Binder state asynchronously
     * 
     * @param fd 文件描述符
     * @param args 参数
     * @throws RemoteException 远程调用异常
     */
    @Override
    public void dumpAsync(@NonNull FileDescriptor fd, @Nullable String[] args) throws RemoteException {
        original.dumpAsync(fd, args);
    }

    /**
     * 链接死亡通知
     * Link death notification
     * 
     * @param recipient 死亡接收者
     * @param flags 标志位
     * @throws RemoteException 远程调用异常
     */
    @Override
    public void linkToDeath(@NonNull DeathRecipient recipient, int flags) throws RemoteException {
        original.linkToDeath(recipient, flags);
    }

    /**
     * 取消链接死亡通知
     * Unlink death notification
     * 
     * @param recipient 死亡接收者
     * @param flags 标志位
     * @return true表示取消成功
     */
    @Override
    public boolean unlinkToDeath(@NonNull DeathRecipient recipient, int flags) {
        return original.unlinkToDeath(recipient, flags);
    }
}

