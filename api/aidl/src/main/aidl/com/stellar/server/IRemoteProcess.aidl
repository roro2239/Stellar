package com.stellar.server;

/**
 * 远程进程接口
 * Remote Process Interface
 * 
 * 功能说明：
 * - 表示在Stellar服务端执行的进程
 * - 提供标准的进程输入输出流访问
 * - 支持进程生命周期管理
 * 
 * 使用说明：
 * - 通过IStellarService.newProcess()创建
 * - 提供类似java.lang.Process的接口
 * - 从版本11开始，进程在调用者死亡时自动终止
 * 
 * 注意事项：
 * - 需要在不同线程读写流以避免死锁
 * - 进程在服务端以特权身份运行
 */
interface IRemoteProcess {

    /**
     * 获取进程的标准输出流（用于写入数据到进程）
     * Get process standard output stream (for writing to process)
     * @return 输出流的文件描述符
     */
    ParcelFileDescriptor getOutputStream();

    /**
     * 获取进程的标准输入流（用于从进程读取输出）
     * Get process standard input stream (for reading from process)
     * @return 输入流的文件描述符
     */
    ParcelFileDescriptor getInputStream();

    /**
     * 获取进程的错误输出流
     * Get process error stream
     * @return 错误输出流的文件描述符
     */
    ParcelFileDescriptor getErrorStream();

    /**
     * 等待进程结束
     * Wait for process to finish
     * @return 进程退出码
     * @throws InterruptedException 如果等待被中断
     */
    int waitFor();

    /**
     * 获取进程退出值（仅当进程已结束）
     * Get process exit value (only when process has finished)
     * @return 退出码
     * @throws IllegalThreadStateException 如果进程尚未结束
     */
    int exitValue();

    /**
     * 销毁进程（强制终止）
     * Destroy process (forcefully terminate)
     */
    void destroy();

    /**
     * 检查进程是否存活
     * Check if process is alive
     * @return true表示进程仍在运行
     */
    boolean alive();

    /**
     * 等待进程结束（带超时）
     * Wait for process to finish with timeout
     * @param timeout 超时时长
     * @param unit 时间单位（TimeUnit的字符串形式）
     * @return true表示进程在超时前结束
     * @throws InterruptedException 如果等待被中断
     */
    boolean waitForTimeout(long timeout, String unit);
}

