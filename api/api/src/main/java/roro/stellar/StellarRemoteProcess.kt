package roro.stellar;

import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.stellar.server.IRemoteProcess;

/**
 * Stellar远程进程包装类
 * Stellar Remote Process Wrapper
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>在Stellar服务端执行进程 - Execute process on Stellar service side</li>
 * <li>提供标准进程输入输出流访问 - Provides standard process I/O stream access</li>
 * <li>支持进程生命周期管理 - Supports process lifecycle management</li>
 * <li>自动管理Binder引用 - Automatically manages Binder references</li>
 * </ul>
 * 
 * <p>使用示例 Usage Example：</p>
 * <pre>
 * StellarRemoteProcess process = Stellar.newProcess(
 *     new String[]{"sh", "-c", "ls -la"}, null, null
 * );
 * 
 * // 读取输出
 * InputStream in = process.getInputStream();
 * // 写入输入
 * OutputStream out = process.getOutputStream();
 * 
 * // 等待完成
 * int exitCode = process.waitFor();
 * </pre>
 * 
 * <p>注意事项 Notes：</p>
 * <ul>
 * <li>从版本11开始，进程在调用者死亡时自动终止 - From version 11, process terminates when caller dies</li>
 * <li>需要在不同线程读写流以避免死锁 - Read/write streams in different threads to avoid deadlock</li>
 * <li>Binder引用会被自动持有直到进程结束 - Binder reference is held until process ends</li>
 * </ul>
 */
public class StellarRemoteProcess extends Process implements Parcelable {

    /** 进程缓存，用于持有Binder引用 Process cache for holding Binder references */
    private static final Set<StellarRemoteProcess> CACHE = Collections.synchronizedSet(new ArraySet<>());

    private static final String TAG = "StellarRemoteProcess";

    /** 远程进程接口 Remote process interface */
    private IRemoteProcess remote;
    /** 输出流（写入进程） Output stream (write to process) */
    private OutputStream os;
    /** 输入流（从进程读取） Input stream (read from process) */
    private InputStream is;

    /**
     * 构造远程进程包装器
     * 
     * @param remote 远程进程接口
     */
    StellarRemoteProcess(IRemoteProcess remote) {
        this.remote = remote;
        try {
            // 监听进程死亡事件
            this.remote.asBinder().linkToDeath((IBinder.DeathRecipient) () -> {
                this.remote = null;
                Log.v(TAG, "remote process is dead");

                // 从缓存中移除
                CACHE.remove(StellarRemoteProcess.this);
            }, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "linkToDeath", e);
        }

        // 必须持有Binder对象的引用，防止被GC
        CACHE.add(this);
    }

    /**
     * 获取进程的标准输出流（用于写入数据到进程）
     * Get process standard output stream (for writing data to process)
     * 
     * @return 输出流 Output stream
     */
    @Override
    public OutputStream getOutputStream() {
        if (os == null) {
            try {
                os = new ParcelFileDescriptor.AutoCloseOutputStream(remote.getOutputStream());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
        return os;
    }

    /**
     * 获取进程的标准输入流（用于从进程读取数据）
     * Get process standard input stream (for reading data from process)
     * 
     * @return 输入流 Input stream
     */
    @Override
    public InputStream getInputStream() {
        if (is == null) {
            try {
                is = new ParcelFileDescriptor.AutoCloseInputStream(remote.getInputStream());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
        return is;
    }

    /**
     * 获取进程的错误输出流
     * Get process error stream
     * 
     * @return 错误输出流 Error stream
     */
    @Override
    public InputStream getErrorStream() {
        try {
            return new ParcelFileDescriptor.AutoCloseInputStream(remote.getErrorStream());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 等待进程结束
     * Wait for process to finish
     * 
     * @return 进程退出码 Process exit code
     * @throws InterruptedException 如果等待被中断
     */
    @Override
    public int waitFor() throws InterruptedException {
        try {
            return remote.waitFor();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取进程退出值（仅当进程已结束）
     * Get process exit value (only when process has finished)
     * 
     * @return 退出码 Exit code
     * @throws IllegalThreadStateException 如果进程尚未结束
     */
    @Override
    public int exitValue() {
        try {
            return remote.exitValue();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 销毁进程
     * Destroy process
     */
    @Override
    public void destroy() {
        try {
            remote.destroy();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 检查进程是否存活
     * Check if process is alive
     * 
     * @return true表示进程仍在运行 true if process is still running
     */
    public boolean alive() {
        try {
            return remote.alive();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 等待进程结束（带超时）
     * Wait for process to finish with timeout
     * 
     * @param timeout 超时时长 Timeout duration
     * @param unit 时间单位 Time unit
     * @return true表示进程在超时前结束 true if process finished before timeout
     * @throws InterruptedException 如果等待被中断
     */
    public boolean waitForTimeout(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            return remote.waitForTimeout(timeout, unit.toString());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取远程进程的Binder对象
     * Get remote process Binder object
     * 
     * @return Binder对象
     */
    public IBinder asBinder() {
        return remote.asBinder();
    }

    private StellarRemoteProcess(Parcel in) {
        remote = IRemoteProcess.Stub.asInterface(in.readStrongBinder());
    }

    public static final Creator<StellarRemoteProcess> CREATOR = new Creator<StellarRemoteProcess>() {
        @Override
        public StellarRemoteProcess createFromParcel(Parcel in) {
            return new StellarRemoteProcess(in);
        }

        @Override
        public StellarRemoteProcess[] newArray(int size) {
            return new StellarRemoteProcess[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongBinder(remote.asBinder());
    }
}

