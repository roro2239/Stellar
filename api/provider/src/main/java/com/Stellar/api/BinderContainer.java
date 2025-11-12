package com.stellar.api;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RestrictTo;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

/**
 * Binder容器类
 * Binder Container Class
 * 
 * <p>功能说明 Features：</p>
 * <ul>
 * <li>封装IBinder以支持Parcelable序列化 - Wraps IBinder to support Parcelable serialization</li>
 * <li>用于在进程间传递Binder对象 - Used to pass Binder objects between processes</li>
 * <li>在Provider和Broadcast中传递Stellar服务Binder - Passes Stellar service Binder in Provider and Broadcast</li>
 * </ul>
 * 
 * <p>注意 Note：</p>
 * 此类仅供内部使用 - This class is for internal use only
 */
@RestrictTo(LIBRARY_GROUP_PREFIX)
public class BinderContainer implements Parcelable {

    /** Stellar服务Binder对象 Stellar service Binder object */
    public IBinder binder;

    /**
     * 构造Binder容器
     * Construct Binder container
     * 
     * @param binder Stellar服务Binder对象
     */
    public BinderContainer(IBinder binder) {
        this.binder = binder;
    }

    /**
     * 描述内容
     * Describe contents
     * 
     * @return 0（没有特殊对象）
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * 序列化到Parcel
     * Serialize to Parcel
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongBinder(this.binder);
    }

    /**
     * 从Parcel反序列化
     * Deserialize from Parcel
     */
    protected BinderContainer(Parcel in) {
        this.binder = in.readStrongBinder();
    }

    /** Parcelable创建器 Parcelable creator */
    public static final Creator<BinderContainer> CREATOR = new Creator<BinderContainer>() {
        @Override
        public BinderContainer createFromParcel(Parcel source) {
            return new BinderContainer(source);
        }

        @Override
        public BinderContainer[] newArray(int size) {
            return new BinderContainer[size];
        }
    };
}

