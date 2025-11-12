package roro.stellar;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.os.Parcel;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 系统服务访问辅助类
 * System Service Access Helper
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>提供获取系统服务Binder的便捷方法 - Provides convenient methods to get system service Binder</li>
 * <li>缓存服务Binder以提高性能 - Caches service Binder for better performance</li>
 * <li>通过反射获取事务代码 - Gets transaction code via reflection</li>
 * </ul>
 * 
 * <p>注意 Note：</p>
 * 推荐使用 {@link StellarBinderWrapper} 而不是直接使用事务代码
 * Recommended to use {@link StellarBinderWrapper} instead of direct transaction code
 * 
 * @deprecated 部分方法已废弃，建议使用 {@link StellarBinderWrapper}
 */
@Deprecated
@SuppressLint("PrivateApi")
public class SystemServiceHelper {

    /** 系统服务Binder缓存 System service Binder cache */
    private static final Map<String, IBinder> SYSTEM_SERVICE_CACHE = new HashMap<>();
    /** 事务代码缓存 Transaction code cache */
    private static final Map<String, Integer> TRANSACT_CODE_CACHE = new HashMap<>();

    /** ServiceManager.getService反射方法 */
    private static Method getService;

    static {
        try {
            // 反射获取ServiceManager类和getService方法
            Class<?> sm = Class.forName("android.os.ServiceManager");
            getService = sm.getMethod("getService", String.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.w("SystemServiceHelper", Log.getStackTraceString(e));
        }
    }

    /**
     * 获取指定名称的系统服务
     * Returns a reference to a service with the given name
     *
     * @param name 服务名称，例如"package"对应android.content.pm.IPackageManager
     *             The name of the service to get such as "package" for android.content.pm.IPackageManager
     * @return 服务的Binder引用，如果服务不存在则返回null
     *         A reference to the service, or null if the service doesn't exist
     */
    public static IBinder getSystemService(@NonNull String name) {
        IBinder binder = SYSTEM_SERVICE_CACHE.get(name);
        if (binder == null) {
            try {
                binder = (IBinder) getService.invoke(null, name);
            } catch (IllegalAccessException | InvocationTargetException e) {
                Log.w("SystemServiceHelper", Log.getStackTraceString(e));
            }
            SYSTEM_SERVICE_CACHE.put(name, binder);
        }
        return binder;
    }

    /**
     * 从类名和方法名获取事务代码
     * Returns transaction code from given class and method name
     *
     * @param className  类名，例如"android.content.pm.IPackageManager$Stub"
     *                   Class name such as "android.content.pm.IPackageManager$Stub"
     * @param methodName 方法名，例如"getInstalledPackages"
     *                   Method name such as "getInstalledPackages"
     * @return 事务代码，如果类或方法不存在则返回null
     *         Transaction code, or null if the class or the method doesn't exist
     * @deprecated 使用 {@link StellarBinderWrapper} 代替
     *             Use {@link StellarBinderWrapper} instead
     */
    @Deprecated
    public static Integer getTransactionCode(@NonNull String className, @NonNull String methodName) {
        final String fieldName = "TRANSACTION_" + methodName;
        final String key = className + "." + fieldName;

        Integer value = TRANSACT_CODE_CACHE.get(key);
        if (value != null) return value;

        try {
            final Class<?> cls = Class.forName(className);
            Field declaredField = null;
            try {
                declaredField = cls.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                for (Field f : cls.getDeclaredFields()) {
                    if (f.getType() != int.class)
                        continue;

                    String name = f.getName();
                    if (name.startsWith(fieldName + "_")
                            && TextUtils.isDigitsOnly(name.substring(fieldName.length() + 1))) {
                        declaredField = f;
                        break;
                    }
                }
            }
            if (declaredField == null) {
                return null;
            }

            declaredField.setAccessible(true);
            value = declaredField.getInt(cls);

            TRANSACT_CODE_CACHE.put(key, value);
            return value;
        } catch (ClassNotFoundException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 为{@link Stellar#transactRemote(Parcel, Parcel, int)}创建新的数据包
     * Obtain a new data parcel for {@link Stellar#transactRemote(Parcel, Parcel, int)}
     *
     * @param serviceName   系统服务名称 System service name
     * @param interfaceName 用于反射的类名 Class name for reflection
     * @param methodName    用于反射的方法名 Method name for reflection
     * @return 数据包 Data parcel
     * @throws NullPointerException 无法获取系统服务或事务代码
     *                              Can't get system service or transaction code
     * @deprecated 使用 {@link StellarBinderWrapper} 代替
     *             Use {@link StellarBinderWrapper} instead
     */
    @Deprecated
    public static Parcel obtainParcel(@NonNull String serviceName, @NonNull String interfaceName, @NonNull String methodName) {
        return obtainParcel(serviceName, interfaceName, interfaceName + "$Stub", methodName);
    }

    /**
     * 为{@link Stellar#transactRemote(Parcel, Parcel, int)}创建新的数据包
     * Obtain a new data parcel for {@link Stellar#transactRemote(Parcel, Parcel, int)}
     *
     * @param serviceName   系统服务名称 System service name
     * @param interfaceName 接口名称 Interface name
     * @param className     用于反射的类名 Class name for reflection
     * @param methodName    用于反射的方法名 Method name for reflection
     * @return 数据包 Data parcel
     * @throws NullPointerException 无法获取系统服务或事务代码
     *                              Can't get system service or transaction code
     * @deprecated 使用 {@link StellarBinderWrapper} 代替
     *             Use {@link StellarBinderWrapper} instead
     */
    @Deprecated
    public static Parcel obtainParcel(@NonNull String serviceName, @NonNull String interfaceName, @NonNull String className, @NonNull String methodName) {
        throw new UnsupportedOperationException("不再支持直接使用 Stellar#transactRemote，请使用 StellarBinderWrapper");
    }
}

