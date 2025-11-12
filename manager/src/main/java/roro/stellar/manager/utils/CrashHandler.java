package roro.stellar.manager.utils;

import android.content.Context;

/**
 * 崩溃处理器
 * Crash Handler
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>捕获未处理的异常 - Catch uncaught exceptions</li>
 * <li>记录崩溃信息到文件 - Log crash info to file</li>
 * <li>保留默认异常处理器 - Preserve default exception handler</li>
 * </ul>
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    /** 单例实例 Singleton instance */
    private static CrashHandler instance;
    
    /** 默认的异常处理器 Default exception handler */
    private Thread.UncaughtExceptionHandler defaultHandler;
    
    /** 应用上下文 Application context */
    private Context context;

    /**
     * 私有构造函数
     * Private constructor
     */
    private CrashHandler() {
    }

    /**
     * 获取单例实例
     * Get singleton instance
     * 
     * @return 崩溃处理器实例
     */
    public static synchronized CrashHandler getInstance() {
        if (instance == null) {
            instance = new CrashHandler();
        }
        return instance;
    }

    /**
     * 初始化崩溃处理器
     * Initialize crash handler
     * 
     * @param context 应用上下文
     */
    public void init(Context context) {
        this.context = context.getApplicationContext();
        
        // 获取默认的异常处理器
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        
        // 设置当前处理器为默认处理器
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            // 记录崩溃信息到文件
            LogFileManager.getInstance().writeCrashLog(thread, throwable);
            
            // 等待日志写入完成（最多2秒）
            Thread.sleep(2000);
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 调用默认的异常处理器
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        }
    }
}

