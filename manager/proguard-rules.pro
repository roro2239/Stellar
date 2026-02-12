-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void check*(...);
	public static void throw*(...);
}

-assumenosideeffects class java.util.Objects{
    ** requireNonNull(...);
}

-keepnames class com.stellar.api.BinderContainer

-keepclassmembers class rikka.hidden.compat.adapter.ProcessObserverAdapter {
    <methods>;
}

-keepclassmembers class rikka.hidden.compat.adapter.UidObserverAdapter {
    <methods>;
}

-keep class roro.stellar.server.StellarService {
    public static void main(java.lang.String[]);
}

# Keep UserServiceStarter for app_process
-keep class roro.stellar.server.userservice.UserServiceStarter {
    public static void main(java.lang.String[]);
}

# Keep Shizuku AIDL interfaces
-keep class moe.shizuku.server.** { *; }
-keep interface moe.shizuku.server.** { *; }

# Keep Shizuku API classes (BinderContainer must keep original package name for client compatibility)
-keep class moe.shizuku.api.** { *; }
# Prevent repackaging of Shizuku classes - clients expect exact package names
-keeppackagenames moe.shizuku.**

# Keep Shizuku compatibility layer
-keep class roro.stellar.server.shizuku.** { *; }
-keep class roro.stellar.shizuku.** { *; }

-assumenosideeffects class android.util.Log {
    public static *** d(...);
}

-assumenosideeffects class roro.stellar.manager.util.Logger {
    public *** d(...);
}

-assumenosideeffects class roro.stellar.server.util.Logger {
    public *** d(...);
}

-allowaccessmodification
-repackageclasses roro.stellar
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Fix R8 missing classes for androidx.window
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.area.**
-dontwarn androidx.window.reflection.**
-dontwarn androidx.window.core.util.function.**
