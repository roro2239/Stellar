package com.stellar.starter;

import android.content.IContentProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.stellar.api.BinderContainer;
import com.stellar.starter.util.IContentProviderCompat;
import rikka.hidden.compat.ActivityManagerApis;

/**
 * Stellar服务启动器
 * Stellar Service Starter
 *
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>在特权进程中启动，接收Stellar服务的Binder - Starts in privileged process, receives Stellar service Binder</li>
 * <li>通过ContentProvider将Binder发送给Manager应用 - Sends Binder to Manager app via ContentProvider</li>
 * <li>支持重试机制确保Binder成功传递 - Supports retry mechanism to ensure Binder delivery</li>
 * <li>监听Manager进程死亡并自动退出 - Monitors Manager process death and auto exits</li>
 * </ul>
 *
 * <p>工作流程 Workflow：</p>
 * <ul>
 * <li>1. 在root/adb进程中启动 - Starts in root/adb process</li>
 * <li>2. 接收Stellar服务Binder - Receives Stellar service Binder</li>
 * <li>3. 通过ContentProvider发送到Manager - Sends to Manager via ContentProvider</li>
 * <li>4. 等待Manager确认并返回Manager的Binder - Waits for Manager confirmation and returns Manager's Binder</li>
 * <li>5. 监听Manager死亡并保持运行 - Monitors Manager death and keeps running</li>
 * </ul>
 *
 * <p>注意 Note：</p>
 * 此类在特权进程中运行，需要root或adb权限
 * This class runs in privileged process, requires root or adb permission
 */
@SuppressWarnings("deprecation")
public class ServiceStarter {

    private static final String TAG = "StellarServiceStarter";

    /** Binder传递的Bundle键名 Bundle key for Binder transfer */
    private static final String EXTRA_BINDER = "roro.stellar.manager.intent.extra.BINDER";

    /** 调试参数（根据Android版本不同） Debug arguments (varies by Android version) */
    public static final String DEBUG_ARGS;

    static {
        // 根据Android版本配置不同的调试参数
        int sdk = Build.VERSION.SDK_INT;
        if (sdk >= 30) {
            // Android 11+
            DEBUG_ARGS = "-Xcompiler-option" + " --debuggable" +
                    " -XjdwpProvider:adbconnection" +
                    " -XjdwpOptions:suspend=n,server=y";
        } else if (sdk >= 28) {
            // Android 9-10
            DEBUG_ARGS = "-Xcompiler-option" + " --debuggable" +
                    " -XjdwpProvider:internal" +
                    " -XjdwpOptions:transport=dt_android_adb,suspend=n,server=y";
        } else {
            // Android 8及以下
            DEBUG_ARGS = "-Xcompiler-option" + " --debuggable" +
                    " -agentlib:jdwp=transport=dt_android_adb,suspend=n,server=y";
        }
    }

    /**
     * 保持Stellar服务Binder的引用，防止被GC回收
     * Hold reference to Stellar service Binder to prevent GC
     *
     * DeathRecipient会在所有引用被释放时自动解除绑定，所以需要保持引用
     * DeathRecipient will automatically be unlinked when all references to the
     * binder is dropped, so we hold the reference here.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private static IBinder StellarBinder;

    /** 最大重试次数 Max retry times */
    private static final int MAX_RETRIES = 50;

    /** 重试延迟（毫秒） Retry delay in milliseconds */
    private static final int RETRY_DELAY_MS = 200;

    /** 主线程Handler Main thread Handler */
    private static Handler handler;

    /**
     * 启动器入口点
     * Starter entry point
     *
     * @param args 命令行参数（暂未使用）
     */
    public static void main(String[] args) {
        // 初始化主线程Looper
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
        }
        handler = new Handler(Looper.getMainLooper());
        retryCount = 0;
        IBinder service;
        String token;

        Log.e(TAG, "Service starter functionality not implemented");
        System.exit(1);
    }

    /** 当前重试次数 Current retry count */
    private static int retryCount;

    /** Stellar Manager包名 Stellar Manager package name */
    static String packageName = "roro.stellar.manager";

    /** ContentProvider实例 ContentProvider instance */
    static IContentProvider provider = null;

    /**
     * 发送Binder到Manager应用
     * Send Binder to Manager app
     *
     * @param binder Stellar服务的Binder
     * @param token 认证令牌
     */
    private static void sendBinder(IBinder binder, String token) {
        String name = packageName + ".stellar";
        int userId = 0;

        // 重试任务：获取ContentProvider并发送Binder
        Runnable retryRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    // 获取Manager的ContentProvider
                    provider = ActivityManagerApis.getContentProviderExternal(name, userId, null, name);
                    if (provider == null) {
                        retryCount++;
                        Log.w(TAG, String.format("provider is null %s %d,try times %d", name, userId, retryCount));
                        if (retryCount < MAX_RETRIES) {
                            // 未达到最大重试次数，继续重试
                            handler.postDelayed(this, RETRY_DELAY_MS);
                        } else {
                            // 达到最大重试次数，退出
                            Log.e(TAG, String.format("provider is null %s %d", name, userId));
                            handler.removeCallbacks(this);
                            System.exit(1);
                        }
                    } else {
                        // 成功获取Provider，处理Binder发送
                        processProvider(provider,binder,token,packageName,userId,this);
                    }

                } catch (Throwable tr) {
                    Log.e(TAG, String.format("failed send binder to %s in user %d", packageName, userId), tr);
                    handler.removeCallbacks(this);
                    System.exit(1);
                } finally {
                    // 清理Provider引用
                    if (provider != null) {
                        try {
                            ActivityManagerApis.removeContentProviderExternal(name, null);
                        } catch (Throwable tr) {
                            Log.w(TAG, "removeContentProviderExternal", tr);
                        }
                    }
                }
            }
        };
        handler.post(retryRunnable);
    }

    /** 是否重试Provider ping检查 Whether to retry provider ping check */
    private static boolean retryProviderPingBinder = true;

    /**
     * 处理ContentProvider，发送Binder
     * Process ContentProvider and send Binder
     *
     * @param provider ContentProvider实例
     * @param binder 要发送的Binder
     * @param token 认证令牌
     * @param packageName Manager包名
     * @param userId 用户ID
     * @param retryRunnable 重试任务
     */
    private static void processProvider(IContentProvider provider, IBinder binder, String token, String packageName, int userId, Runnable retryRunnable) {
        String name = packageName + ".stellar";

        // 检查Provider是否存活
        if (!provider.asBinder().pingBinder()) {
            Log.e(TAG, String.format("provider is dead %s %d", name, userId));

            if (retryProviderPingBinder) {
                // 由于未知原因，有时会出现Provider死亡的情况
                // 强制停止Stellar应用并重试可以解决
                // For unknown reason, sometimes this could happens
                // Kill Stellar app and try again could work
                ActivityManagerApis.forceStopPackageNoThrow(packageName, userId);
                Log.e(TAG, String.format("kill %s in user %d and try again", packageName, userId));
                handler.postDelayed(retryRunnable, 1000);
                retryProviderPingBinder = false;
                return;
            }
            handler.removeCallbacks(retryRunnable);
            System.exit(1);
        }

        if (!retryProviderPingBinder) {
            Log.e(TAG, "retry works");
        }

        // 准备发送的数据
        Bundle extra = new Bundle();
        extra.putParcelable(EXTRA_BINDER, new BinderContainer(binder));
        extra.putString("token", token);

        // 调用Provider的sendBinder方法
        Bundle reply = null;
        try {
            reply = IContentProviderCompat.call(provider, null, null, name, "sendBinder", null, extra);
        } catch (Throwable tr) {
            Log.e(TAG, String.format("failed send binder to %s in user %d", packageName, userId), tr);
            handler.removeCallbacks(retryRunnable);
            System.exit(1);
        }

        // 处理返回的Binder
        if (reply != null) {
            reply.setClassLoader(BinderContainer.class.getClassLoader());

            Log.i(TAG, String.format("send binder to %s in user %d", packageName, userId));
            BinderContainer container = reply.getParcelable(EXTRA_BINDER);

            // 验证并保存Manager返回的Binder
            if (container != null && container.binder != null && container.binder.pingBinder()) {
                StellarBinder = container.binder;
                try {
                    // 监听Manager进程死亡
                    StellarBinder.linkToDeath(() -> {
                        Log.i(TAG, "exiting...");
                        handler.removeCallbacks(retryRunnable);
                        System.exit(0);
                    }, 0);
                } catch (Throwable tr) {
                    Log.e(TAG, String.format("failed send binder to %s in user %d", packageName, userId), tr);
                    handler.removeCallbacks(retryRunnable);
                    System.exit(1);
                }
                return;
            } else {
                Log.w(TAG, "server binder not received");
            }
        }
        // 发送失败，退出
        handler.removeCallbacks(retryRunnable);
        System.exit(1);

    }
}

