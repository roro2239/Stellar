package roro.stellar;

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.stellar.api.BinderContainer;

/**
 * Stellar服务提供者
 * Stellar Service Provider
 *
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>接收来自Stellar服务端的Binder - Receives Binder from Stellar server</li>
 * <li>自动处理服务连接 - Automatically handles service connection</li>
 * <li>支持多进程Binder共享 - Supports multi-process Binder sharing</li>
 * </ul>
 *
 * <p>工作原理 How it works：</p>
 * <p>
 * 当应用进程启动时，Stellar服务端（运行在adb/root下）会通过此Provider发送Binder。
 * This provider receives binder from Stellar server. When app process starts,
 * Stellar server (it runs under adb/root) will send the binder to client apps with this provider.
 * </p>
 *
 * <p>在Manifest中声明Provider In Manifest：</p>
 * <p>
 * Add the provider to your manifest like this:
 * </p>
 * <pre class="prettyprint">&lt;manifest&gt;
 *    ...
 *    &lt;application&gt;
 *        ...
 *        &lt;provider
 *            android:name="roro.stellar.StellarProvider"
 *            android:authorities="${applicationId}.stellar"
 *            android:exported="true"
 *            android:multiprocess="false"
 *            android:permission="android.permission.INTERACT_ACROSS_USERS_FULL"
 *        &lt;/provider&gt;
 *        ...
 *    &lt;/application&gt;
 * &lt;/manifest&gt;</pre>
 *
 * <p>重要配置说明 Important Configuration Notes：</p>
 * <ol>
 * <li><code>android:permission</code> 应该是只授予Shell但普通应用没有的权限
 * （如android.permission.INTERACT_ACROSS_USERS_FULL），这样只有应用本身和Stellar服务端可以访问。
 * Should be a permission that granted to Shell (com.android.shell) but not normal apps,
 * so that it can only be used by the app itself and Stellar server.</li>
 *
 * <li><code>android:exported</code> 必须为<code>true</code>，
 * 以便运行在adb下的Stellar服务端可以访问。
 * Must be true so that the provider can be accessed from Stellar server runs under adb.</li>
 *
 * <li><code>android:multiprocess</code> 必须为<code>false</code>，
 * 因为Stellar服务端只在应用启动时获取UID。
 * Must be false since Stellar server only gets uid when app starts.</li>
 * </ol>
 *
 * <p>多进程支持 Multi-process Support：</p>
 * <p>
 * 如果应用运行在多个进程中，此Provider可以在进程间共享Binder。
 * If your app runs in multiple processes, this provider also provides the functionality of sharing
 * the binder across processes. See {@link #enableMultiProcessSupport(boolean)}.
 * </p>
 */
@SuppressWarnings("deprecation")
public class StellarProvider extends ContentProvider {

    private static final String TAG = "StellarProvider";

    // For receive Binder from Stellar
    public static final String METHOD_SEND_BINDER = "sendBinder";

    // For share Binder between processes
    public static final String METHOD_GET_BINDER = "getBinder";

    public static final String ACTION_BINDER_RECEIVED = "com.stellar.api.action.BINDER_RECEIVED";

    private static final String EXTRA_BINDER = "roro.stellar.manager.intent.extra.BINDER";

    public static final String PERMISSION = "roro.stellar.manager.permission.API_V23";

    public static final String MANAGER_APPLICATION_ID = "roro.stellar.manager";

    private static boolean enableMultiProcess = false;

    private static boolean isProviderProcess = false;

    /**
     * 设置是否为Provider进程
     * Set whether is provider process
     *
     * @param isProviderProcess true表示是Provider进程
     */
    public static void setIsProviderProcess(boolean isProviderProcess) {
        StellarProvider.isProviderProcess = isProviderProcess;
    }

    /**
     * 启用内置多进程支持
     * Enables built-in multi-process support
     *
     * <p>此方法必须尽早调用（例如在Application的静态块中）</p>
     * <p>This method MUST be called as early as possible (e.g., static block in Application).</p>
     *
     * @param isProviderProcess true表示是Provider进程
     */
    public static void enableMultiProcessSupport(boolean isProviderProcess) {
        Log.d(TAG, "Enable built-in multi-process support (from " + (isProviderProcess ? "provider process" : "non-provider process") + ")");

        StellarProvider.isProviderProcess = isProviderProcess;
        StellarProvider.enableMultiProcess = true;
    }



    /**
     * Require binder for non-provider process, should have {@link #enableMultiProcessSupport(boolean)} called first.
     *
     * @param context Context
     */
    public static void requestBinderForNonProviderProcess(@NonNull Context context) {
        if (isProviderProcess) {
            return;
        }

        Log.d(TAG, "request binder in non-provider process");

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                BinderContainer container = intent.getParcelableExtra(EXTRA_BINDER);
                if (container != null && container.binder != null) {
                    Log.i(TAG, "binder received from broadcast");
                    Stellar.onBinderReceived(container.binder, context.getPackageName());
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, new IntentFilter(ACTION_BINDER_RECEIVED), Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, new IntentFilter(ACTION_BINDER_RECEIVED));
        }

        Bundle reply;
        try {
            reply = context.getContentResolver().call(Uri.parse("content://" + context.getPackageName() + ".stellar"),
                    StellarProvider.METHOD_GET_BINDER, null, new Bundle());
        } catch (Throwable tr) {
            reply = null;
        }

        if (reply != null) {
            reply.setClassLoader(BinderContainer.class.getClassLoader());

            BinderContainer container = reply.getParcelable(EXTRA_BINDER);
            if (container != null && container.binder != null) {
                Log.i(TAG, "Binder received from other process");
                Stellar.onBinderReceived(container.binder, context.getPackageName());
            }
        }
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);

        if (info.multiprocess)
            throw new IllegalStateException("android:multiprocess must be false");

        if (!info.exported)
            throw new IllegalStateException("android:exported must be true");

        isProviderProcess = true;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (extras == null) {
            return null;
        }

        extras.setClassLoader(BinderContainer.class.getClassLoader());

        Bundle reply = new Bundle();
        switch (method) {
            case METHOD_SEND_BINDER: {
                handleSendBinder(extras);
                break;
            }
            case METHOD_GET_BINDER: {
                if (!handleGetBinder(reply)) {
                    return null;
                }
                break;
            }
        }
        return reply;
    }

    /**
     * 处理发送Binder请求
     * Handle send binder request
     *
     * <p>接收服务端发来的Binder并通知Stellar</p>
     * <p>如果启用了多进程支持，还会广播Binder到其他进程</p>
     *
     * @param extras 包含Binder的Bundle
     */
    private void handleSendBinder(@NonNull Bundle extras) {
        if (Stellar.pingBinder()) {
            Log.d(TAG, "sendBinder is called when already a living binder");
            return;
        }

        BinderContainer container = extras.getParcelable(EXTRA_BINDER);
        if (container != null && container.binder != null) {
            Log.d(TAG, "binder received");

            Stellar.onBinderReceived(container.binder, getContext().getPackageName());

            if (enableMultiProcess) {
                Log.d(TAG, "broadcast binder");

                Intent intent = new Intent(ACTION_BINDER_RECEIVED)
                        .putExtra(EXTRA_BINDER, container)
                        .setPackage(getContext().getPackageName());
                getContext().sendBroadcast(intent);
            }
        }
    }

    /**
     * 处理获取Binder请求
     * Handle get binder request
     *
     * <p>将已存在的Binder返回给调用者</p>
     *
     * @param reply 返回数据Bundle
     * @return true表示成功获取Binder
     */
    private boolean handleGetBinder(@NonNull Bundle reply) {
        // Other processes in the same app can read the provider without permission
        IBinder binder = Stellar.getBinder();
        if (binder == null || !binder.pingBinder())
            return false;

        reply.putParcelable(EXTRA_BINDER, new BinderContainer(binder));
        return true;
    }

    // no other provider methods
    @Nullable
    @Override
    public final Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public final String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public final Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public final int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public final int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}

