package roro.stellar.server;

import static roro.stellar.server.ServerConstants.PERMISSION;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.AtomicFile;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import kotlin.collections.ArraysKt;
import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.PermissionManagerApis;
import rikka.hidden.compat.UserManagerApis;
import roro.stellar.server.ktx.HandlerKt;

/**
 * Stellar配置管理器
 * Stellar Configuration Manager
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>管理应用权限配置的读写 - Manages app permission configuration read/write</li>
 * <li>支持延迟写入优化性能 - Supports delayed write for performance</li>
 * <li>自动同步系统权限状态 - Auto syncs system permission status</li>
 * <li>监听APK变化并更新配置 - Monitors APK changes and updates config</li>
 * </ul>
 * 
 * <p>配置存储 Configuration Storage：</p>
 * <ul>
 * <li>使用JSON格式存储 - Uses JSON format for storage</li>
 * <li>使用AtomicFile保证原子性 - Uses AtomicFile for atomicity</li>
 * <li>支持配置版本升级 - Supports configuration version upgrade</li>
 * </ul>
 */
public class StellarConfigManager extends ConfigManager {

    /** JSON反序列化器 JSON deserializer */
    private static final Gson GSON_IN = new GsonBuilder()
            .create();
    
    /** JSON序列化器（带版本过滤） JSON serializer (with version filter) */
    private static final Gson GSON_OUT = new GsonBuilder()
            .setVersion(StellarConfig.LATEST_VERSION)
            .create();

    /** 延迟写入时间（毫秒） Delayed write time in milliseconds */
    private static final long WRITE_DELAY = 10 * 1000;

    private static final File FILE = new File("/data/user_de/0/com.android.shell/Stellar.json");
    private static final AtomicFile ATOMIC_FILE = new AtomicFile(FILE);

    public static StellarConfig load() {
        FileInputStream stream;
        try {
            stream = ATOMIC_FILE.openRead();
        } catch (FileNotFoundException e) {
            LOGGER.i("no existing config file " + ATOMIC_FILE.getBaseFile() + "; starting empty");
            return new StellarConfig();
        }

        StellarConfig config = null;
        try {
            config = GSON_IN.fromJson(new InputStreamReader(stream), StellarConfig.class);
        } catch (Throwable tr) {
            LOGGER.w(tr, "load config");
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                LOGGER.w("failed to close: " + e);
            }
        }
        if (config != null) return config;
        return new StellarConfig();
    }

    public static void write(StellarConfig config) {
        synchronized (ATOMIC_FILE) {
            FileOutputStream stream;
            try {
                stream = ATOMIC_FILE.startWrite();
            } catch (IOException e) {
                LOGGER.w("failed to write state: " + e);
                return;
            }

            try {
                String json = GSON_OUT.toJson(config);
                stream.write(json.getBytes());

                ATOMIC_FILE.finishWrite(stream);
                LOGGER.v("config saved");
            } catch (Throwable tr) {
                LOGGER.w(tr, "can't save %s, restoring backup.", ATOMIC_FILE.getBaseFile());
                ATOMIC_FILE.failWrite(stream);
            }
        }
    }

    private final Runnable mWriteRunner = new Runnable() {

        @Override
        public void run() {
            write(config);
        }
    };

    private final StellarConfig config;

    public StellarConfigManager() {
        this.config = load();

        boolean changed = false;

        if (config.packages == null) {
            config.packages = new ArrayList<>();
            changed = true;
        }

        for (StellarConfig.PackageEntry entry : new ArrayList<>(config.packages)) {
            if (entry.packages == null) {
                entry.packages = new ArrayList<>();
            }

            List<String> packages = PackageManagerApis.getPackagesForUidNoThrow(entry.uid);
            if (packages.isEmpty()) {
                LOGGER.i("remove config for uid %d since it has gone", entry.uid);
                config.packages.remove(entry);
                changed = true;
                continue;
            }

            boolean packagesChanged = true;

            for (String packageName : entry.packages) {
                if (packages.contains(packageName)) {
                    packagesChanged = false;
                    break;
                }
            }

            final int rawSize = entry.packages.size();
            Set<String> s = new LinkedHashSet<>(entry.packages);
            entry.packages.clear();
            entry.packages.addAll(s);
            final int shrunkSize = entry.packages.size();
            if (shrunkSize < rawSize) {
                LOGGER.w("entry.packages has duplicate! Shrunk. (%d -> %d)", rawSize, shrunkSize);
            }

            if (packagesChanged) {
                LOGGER.i("remove config for uid %d since the packages for it changed", entry.uid);
                config.packages.remove(entry);
                changed = true;
            }
        }

        for (int userId : UserManagerApis.getUserIdsNoThrow()) {
            for (PackageInfo pi : PackageManagerApis.getInstalledPackagesNoThrow(PackageManager.GET_PERMISSIONS, userId)) {
                if (pi == null
                        || pi.applicationInfo == null
                        || pi.requestedPermissions == null
                        || !ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
                    continue;
                }

                int uid = pi.applicationInfo.uid;
                boolean allowed;
                try {
                    allowed = PermissionManagerApis.checkPermission(PERMISSION, uid) == PackageManager.PERMISSION_GRANTED;
                } catch (Throwable e) {
                    LOGGER.w("checkPermission");
                    continue;
                }

                List<String> packages = new ArrayList<>();
                packages.add(pi.packageName);

                updateLocked(uid, packages, ConfigManager.MASK_PERMISSION, allowed ? ConfigManager.FLAG_ALLOWED : 0);
                changed = true;
            }
        }

        if (changed) {
            scheduleWriteLocked();
        }
    }

    private void scheduleWriteLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (HandlerKt.getWorkerHandler().hasCallbacks(mWriteRunner)) {
                return;
            }
        } else {
            HandlerKt.getWorkerHandler().removeCallbacks(mWriteRunner);
        }
        HandlerKt.getWorkerHandler().postDelayed(mWriteRunner, WRITE_DELAY);
    }

    private StellarConfig.PackageEntry findLocked(int uid) {
        for (StellarConfig.PackageEntry entry : config.packages) {
            if (uid == entry.uid) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    public StellarConfig.PackageEntry find(int uid) {
        synchronized (this) {
            return findLocked(uid);
        }
    }

    private void updateLocked(int uid, List<String> packages, int mask, int values) {
        StellarConfig.PackageEntry entry = findLocked(uid);
        if (entry == null) {
            entry = new StellarConfig.PackageEntry(uid, mask & values);
            config.packages.add(entry);
        } else {
            int newValue = (entry.flags & ~mask) | (mask & values);
            if (newValue == entry.flags) {
                return;
            }
            entry.flags = newValue;
        }
        if (packages != null) {
            for (String packageName : packages) {
                if (entry.packages.contains(packageName)) {
                    continue;
                }
                entry.packages.add(packageName);
            }
        }
        scheduleWriteLocked();
    }

    public void update(int uid, List<String> packages, int mask, int values) {
        synchronized (this) {
            updateLocked(uid, packages, mask, values);
        }
    }

    private void removeLocked(int uid) {
        StellarConfig.PackageEntry entry = findLocked(uid);
        if (entry == null) {
            return;
        }
        config.packages.remove(entry);
        scheduleWriteLocked();
    }

    public void remove(int uid) {
        synchronized (this) {
            removeLocked(uid);
        }
    }
}

