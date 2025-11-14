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

-assumenosideeffects class android.util.Log {
    public static *** d(...);
}

-assumenosideeffects class roro.stellar.manager.utils.Logger {
    public *** d(...);
}

-assumenosideeffects class roro.stellar.server.util.Logger {
    public *** d(...);
}

-allowaccessmodification
-repackageclasses roro.stellar
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
