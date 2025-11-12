package roro.stellar.server.api;

import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.stellar.server.IRemoteProcess;
import roro.stellar.server.util.Logger;
import roro.stellar.server.util.ParcelFileDescriptorUtil;

/**
 * 远程进程持有者
 * Remote Process Holder
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>封装Process为AIDL接口 - Wraps Process as AIDL interface</li>
 * <li>提供跨进程的进程控制能力 - Provides cross-process process control</li>
 * <li>管理进程的输入输出流 - Manages process I/O streams</li>
 * <li>监听客户端死亡并自动清理 - Monitors client death and auto cleanup</li>
 * </ul>
 * 
 * <p>工作原理 How It Works：</p>
 * <ul>
 * <li>将Process的InputStream/OutputStream转换为ParcelFileDescriptor - Converts Process I/O streams to ParcelFileDescriptor</li>
 * <li>通过Binder传递文件描述符到客户端 - Passes file descriptors to client via Binder</li>
 * <li>当客户端死亡时自动销毁进程 - Auto destroys process when client dies</li>
 * </ul>
 */
public class RemoteProcessHolder extends IRemoteProcess.Stub {

    private static final Logger LOGGER = new Logger("RemoteProcessHolder");

    /** 本地进程对象 Local process object */
    private final Process process;
    
    /** 进程输入流的文件描述符 Input stream file descriptor */
    private ParcelFileDescriptor in;
    
    /** 进程输出流的文件描述符 Output stream file descriptor */
    private ParcelFileDescriptor out;

    /**
     * 构造远程进程持有者
     * Construct remote process holder
     * 
     * @param process 要包装的进程
     * @param token 客户端Binder令牌（用于监听客户端死亡）
     */
    public RemoteProcessHolder(Process process, IBinder token) {
        this.process = process;

        // 监听客户端死亡，自动清理进程
        if (token != null) {
            try {
                DeathRecipient deathRecipient = () -> {
                    try {
                        if (alive()) {
                            destroy();
                            LOGGER.i("destroy process because the owner is dead");
                        }
                    } catch (Throwable e) {
                        LOGGER.w(e, "failed to destroy process");
                    }
                };
                token.linkToDeath(deathRecipient, 0);
            } catch (Throwable e) {
                LOGGER.w(e, "linkToDeath");
            }
        }
    }

    /**
     * 获取进程的标准输出流（用于写入数据到进程）
     * Get process standard output stream (for writing to process)
     */
    @Override
    public ParcelFileDescriptor getOutputStream() {
        if (out == null) {
            try {
                out = ParcelFileDescriptorUtil.pipeTo(process.getOutputStream());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return out;
    }

    /**
     * 获取进程的标准输入流（用于读取进程输出）
     * Get process standard input stream (for reading process output)
     */
    @Override
    public ParcelFileDescriptor getInputStream() {
        if (in == null) {
            try {
                in = ParcelFileDescriptorUtil.pipeFrom(process.getInputStream());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return in;
    }

    /**
     * 获取进程的错误流
     * Get process error stream
     */
    @Override
    public ParcelFileDescriptor getErrorStream() {
        try {
            return ParcelFileDescriptorUtil.pipeFrom(process.getErrorStream());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 等待进程结束
     * Wait for process to finish
     * 
     * @return 进程退出码
     */
    @Override
    public int waitFor() {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 获取进程退出码
     * Get process exit value
     * 
     * @return 退出码
     * @throws IllegalThreadStateException 如果进程尚未结束
     */
    @Override
    public int exitValue() {
        return process.exitValue();
    }

    /**
     * 销毁进程
     * Destroy process
     */
    @Override
    public void destroy() {
        process.destroy();
    }

    /**
     * 检查进程是否存活
     * Check if process is alive
     * 
     * @return true表示进程仍在运行
     */
    @Override
    public boolean alive() throws RemoteException {
        try {
            this.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    /**
     * 等待进程结束（带超时）
     * Wait for process to finish (with timeout)
     * 
     * @param timeout 超时时间
     * @param unitName 时间单位名称
     * @return true表示进程在超时前结束
     */
    @Override
    public boolean waitForTimeout(long timeout, String unitName) throws RemoteException {
        TimeUnit unit = TimeUnit.valueOf(unitName);
        long startTime = System.nanoTime();
        long rem = unit.toNanos(timeout);

        do {
            try {
                exitValue();
                return true;
            } catch (IllegalThreadStateException ex) {
                if (rem > 0) {
                    try {
                        Thread.sleep(
                                Math.min(TimeUnit.NANOSECONDS.toMillis(rem) + 1, 100));
                    } catch (InterruptedException e) {
                        throw new IllegalStateException();
                    }
                }
            }
            rem = unit.toNanos(timeout) - (System.nanoTime() - startTime);
        } while (rem > 0);
        return false;
    }
}

