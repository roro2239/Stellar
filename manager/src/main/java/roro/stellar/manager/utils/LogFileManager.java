package roro.stellar.manager.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 日志文件管理器
 * Log File Manager
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>将日志保存到应用私有目录 - Save logs to app private directory</li>
 * <li>支持日志文件轮转 - Supports log rotation</li>
 * <li>异步写入，不阻塞主线程 - Async write, non-blocking</li>
 * <li>自动清理旧日志 - Auto cleanup old logs</li>
 * </ul>
 */
public class LogFileManager {

    /** 单例实例 Singleton instance */
    private static LogFileManager instance;

    /** 日志目录 Log directory */
    private File logDir;
    
    /** 当前日志文件 Current log file */
    private File currentLogFile;
    
    /** 崩溃日志目录 Crash log directory */
    private File crashDir;
    
    /** 异步写入线程池 Async write executor */
    private ExecutorService executor;
    
    /** 日期格式化器 Date formatter */
    private SimpleDateFormat dateFormat;
    
    /** 时间戳格式化器 Timestamp formatter */
    private SimpleDateFormat timestampFormat;
    
    /** 最大日志文件大小（10MB） Max log file size */
    private static final long MAX_LOG_FILE_SIZE = 10 * 1024 * 1024;
    
    /** 保留的最大日志文件数量 Max log files to keep */
    private static final int MAX_LOG_FILES = 5;

    /**
     * 私有构造函数
     * Private constructor
     */
    private LogFileManager() {
        executor = Executors.newSingleThreadExecutor();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    }

    /**
     * 获取单例实例
     * Get singleton instance
     * 
     * @return 日志文件管理器实例
     */
    public static synchronized LogFileManager getInstance() {
        if (instance == null) {
            instance = new LogFileManager();
        }
        return instance;
    }

    /**
     * 初始化日志文件管理器
     * Initialize log file manager
     * 
     * @param context 应用上下文
     */
    public void init(Context context) {
        // 创建日志目录
        logDir = new File(context.getFilesDir(), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        
        // 创建崩溃日志目录
        crashDir = new File(context.getFilesDir(), "crashes");
        if (!crashDir.exists()) {
            crashDir.mkdirs();
        }
        
        // 设置当前日志文件
        String today = dateFormat.format(new Date());
        currentLogFile = new File(logDir, "stellar_" + today + ".log");
        
        // 清理旧日志
        cleanOldLogs();
    }

    /**
     * 写入日志到文件
     * Write log to file
     * 
     * @param level 日志级别
     * @param tag 日志标签
     * @param message 日志消息
     * @param throwable 异常（可选）
     */
    public void writeLog(String level, String tag, String message, Throwable throwable) {
        if (currentLogFile == null) {
            return;
        }
        
        executor.execute(() -> {
            try {
                // 检查是否需要轮转日志文件
                checkLogRotation();
                
                // 格式化日志消息
                String timestamp = timestampFormat.format(new Date());
                StringBuilder logMessage = new StringBuilder();
                logMessage.append(timestamp).append(" ")
                         .append(level).append("/")
                         .append(tag).append(": ")
                         .append(message);
                
                // 如果有异常，添加异常堆栈
                if (throwable != null) {
                    logMessage.append("\n");
                    logMessage.append(Log.getStackTraceString(throwable));
                }
                
                logMessage.append("\n");
                
                // 写入文件
                try (FileWriter fw = new FileWriter(currentLogFile, true);
                     BufferedWriter bw = new BufferedWriter(fw)) {
                    bw.write(logMessage.toString());
                    bw.flush();
                }
                
            } catch (IOException e) {
                // 静默失败，避免日志系统本身产生错误
                e.printStackTrace();
            }
        });
    }

    /**
     * 写入崩溃日志
     * Write crash log
     * 
     * @param thread 崩溃线程
     * @param throwable 异常
     */
    public void writeCrashLog(Thread thread, Throwable throwable) {
        if (crashDir == null) {
            return;
        }
        
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
            File crashFile = new File(crashDir, "crash_" + timestamp + ".log");
            
            try (FileWriter fw = new FileWriter(crashFile);
                 PrintWriter pw = new PrintWriter(fw)) {
                
                // 写入崩溃信息头
                pw.println("=== Stellar Manager Crash Report ===");
                pw.println("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
                pw.println("Thread: " + thread.getName() + " (ID: " + thread.getId() + ")");
                pw.println();
                
                // 写入异常堆栈
                pw.println("Exception:");
                throwable.printStackTrace(pw);
                pw.println();
                
                // 写入所有线程堆栈
                pw.println("=== All Threads ===");
                for (Thread t : Thread.getAllStackTraces().keySet()) {
                    pw.println("Thread: " + t.getName() + " (ID: " + t.getId() + ")");
                    for (StackTraceElement element : t.getStackTrace()) {
                        pw.println("    at " + element);
                    }
                    pw.println();
                }
                
                pw.flush();
            }
            
            // 同时写入到普通日志
            writeLog("CRASH", "CrashHandler", 
                    "Application crashed on thread: " + thread.getName(), throwable);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 检查并执行日志轮转
     * Check and perform log rotation
     */
    private void checkLogRotation() {
        if (currentLogFile.exists() && currentLogFile.length() > MAX_LOG_FILE_SIZE) {
            // 日志文件过大，创建新文件
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
            currentLogFile = new File(logDir, "stellar_" + timestamp + ".log");
            cleanOldLogs();
        }
    }

    /**
     * 清理旧日志文件
     * Clean old log files
     */
    private void cleanOldLogs() {
        if (logDir == null || !logDir.exists()) {
            return;
        }
        
        File[] logFiles = logDir.listFiles((dir, name) -> name.endsWith(".log"));
        if (logFiles != null && logFiles.length > MAX_LOG_FILES) {
            // 按修改时间排序
            java.util.Arrays.sort(logFiles, (f1, f2) -> 
                Long.compare(f1.lastModified(), f2.lastModified()));
            
            // 删除最旧的文件
            for (int i = 0; i < logFiles.length - MAX_LOG_FILES; i++) {
                logFiles[i].delete();
            }
        }
    }

    /**
     * 获取日志目录
     * Get log directory
     * 
     * @return 日志目录
     */
    public File getLogDir() {
        return logDir;
    }

    /**
     * 获取崩溃日志目录
     * Get crash log directory
     * 
     * @return 崩溃日志目录
     */
    public File getCrashDir() {
        return crashDir;
    }

    /**
     * 关闭日志管理器
     * Shutdown log manager
     */
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}

