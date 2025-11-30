package roro.stellar.manager;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;

import roro.stellar.manager.utils.EmptySharedPreferencesImpl;

public class StellarSettings {

    public static final String NAME = "settings";
    
    public static final String KEEP_START_ON_BOOT = "start_on_boot";
    
    public static final String KEEP_START_ON_BOOT_WIRELESS = "start_on_boot_wireless";
    
    public static final String TCPIP_PORT = "tcpip_port";
    
    public static final String TCPIP_PORT_ENABLED = "tcpip_port_enabled";
    
    public static final String THEME_MODE = "theme_mode";

    private static SharedPreferences sPreferences;

    public static SharedPreferences getPreferences() {
        return sPreferences;
    }

    @NonNull
    private static Context getSettingsStorageContext(@NonNull Context context) {
        Context storageContext = context.createDeviceProtectedStorageContext();
        storageContext = new ContextWrapper(storageContext) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                try {
                    return super.getSharedPreferences(name, mode);
                } catch (IllegalStateException e) {
                    return new EmptySharedPreferencesImpl();
                }
            }
        };
        return storageContext;
    }

    public static void initialize(Context context) {
        if (sPreferences == null) {
            sPreferences = getSettingsStorageContext(context).getSharedPreferences(NAME, Context.MODE_PRIVATE);
            
            if (!sPreferences.contains(TCPIP_PORT_ENABLED)) {
                sPreferences.edit().putBoolean(TCPIP_PORT_ENABLED, true).apply();
            }
            if (!sPreferences.contains(TCPIP_PORT)) {
                int randomPort = 1000 + (int) (Math.random() * 9000);
                sPreferences.edit().putString(TCPIP_PORT, String.valueOf(randomPort)).apply();
            }
        }
    }

    @IntDef({LaunchMethod.UNKNOWN, LaunchMethod.ROOT, LaunchMethod.ADB,})
    @Retention(SOURCE)
    public @interface LaunchMethod {
        int UNKNOWN = -1;
        int ROOT = 0;
        int ADB = 1;
    }

    @LaunchMethod
    public static int getLastLaunchMode() {
        return getPreferences().getInt("mode", LaunchMethod.UNKNOWN);
    }

    public static void setLastLaunchMode(@LaunchMethod int method) {
        getPreferences().edit().putInt("mode", method).apply();
    }
}

