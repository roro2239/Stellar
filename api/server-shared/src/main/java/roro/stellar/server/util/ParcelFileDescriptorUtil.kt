package roro.stellar.server.util;


import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * ParcelFileDescriptor工具类
 * ParcelFileDescriptor Utility Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>创建跨进程的文件描述符管道 - Creates cross-process file descriptor pipes</li>
 * <li>支持从InputStream创建可读的ParcelFileDescriptor - Supports creating readable ParcelFileDescriptor from InputStream</li>
 * <li>支持向OutputStream创建可写的ParcelFileDescriptor - Supports creating writable ParcelFileDescriptor to OutputStream</li>
 * <li>自动处理数据传输 - Automatically handles data transfer</li>
 * </ul>
 * 
 * <p>使用场景 Use Cases：</p>
 * <ul>
 * <li>跨进程传输进程输入输出流 - Transfer process I/O streams across processes</li>
 * <li>远程进程通信 - Remote process communication</li>
 * </ul>
 */
public class ParcelFileDescriptorUtil {

    /**
     * 从InputStream创建ParcelFileDescriptor（用于读取）
     * Create ParcelFileDescriptor from InputStream (for reading)
     * 
     * @param inputStream 输入流
     * @return 可读的ParcelFileDescriptor
     * @throws IOException 如果创建管道失败
     */
    public static ParcelFileDescriptor pipeFrom(InputStream inputStream) throws IOException {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor readSide = pipe[0];
        ParcelFileDescriptor writeSide = pipe[1];

        // 启动后台线程将inputStream的数据写入管道
        new TransferThread(inputStream, new ParcelFileDescriptor.AutoCloseOutputStream(writeSide))
                .start();

        return readSide;
    }

    /**
     * 创建ParcelFileDescriptor写入OutputStream
     * Create ParcelFileDescriptor to write to OutputStream
     * 
     * @param outputStream 输出流
     * @return 可写的ParcelFileDescriptor
     * @throws IOException 如果创建管道失败
     */
    public static ParcelFileDescriptor pipeTo(OutputStream outputStream) throws IOException {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor readSide = pipe[0];
        ParcelFileDescriptor writeSide = pipe[1];

        // 启动后台线程将管道数据写入outputStream
        new TransferThread(new ParcelFileDescriptor.AutoCloseInputStream(readSide), outputStream)
                .start();

        return writeSide;
    }

    /**
     * 数据传输线程
     * Data Transfer Thread
     * 
     * <p>负责在后台线程中持续传输数据，直到输入流结束</p>
     */
    public static class TransferThread extends Thread {
        final InputStream mIn;
        final OutputStream mOut;

        /**
         * 构造传输线程
         * Construct transfer thread
         * 
         * @param in 输入流
         * @param out 输出流
         */
        public TransferThread(InputStream in, OutputStream out) {
            super("ParcelFileDescriptor Transfer Thread");
            mIn = in;
            mOut = out;
            setDaemon(true); // 设为守护线程，不阻止JVM退出
        }

        /**
         * 线程执行方法
         * Thread execution method
         * 
         * <p>持续从输入流读取数据并写入输出流，直到输入流结束</p>
         * <p>Continuously reads from input stream and writes to output stream until EOF</p>
         */
        @Override
        public void run() {
            byte[] buf = new byte[8192];
            int len;

            try {
                // 持续读取并写入数据
                while ((len = mIn.read(buf)) > 0) {
                    mOut.write(buf, 0, len);
                    mOut.flush();
                }
            } catch (IOException e) {
                Log.e("TransferThread", Log.getStackTraceString(e));
            } finally {
                // 确保流被关闭
                try {
                    mIn.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    mOut.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}


