package com.stellar.api

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import androidx.annotation.RestrictTo

/**
 * Binder容器类
 * Binder Container Class
 *
 *
 * 功能说明 Features：
 *
 *  * 封装IBinder以支持Parcelable序列化 - Wraps IBinder to support Parcelable serialization
 *  * 用于在进程间传递Binder对象 - Used to pass Binder objects between processes
 *  * 在Provider和Broadcast中传递Stellar服务Binder - Passes Stellar service Binder in Provider and Broadcast
 *
 *
 *
 * 注意 Note：
 * 此类仅供内部使用 - This class is for internal use only
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
open class BinderContainer : Parcelable {
    /** Stellar服务Binder对象 Stellar service Binder object  */
    var binder: IBinder?

    /**
     * 构造Binder容器
     * Construct Binder container
     *
     * @param binder Stellar服务Binder对象
     */
    constructor(binder: IBinder?) {
        this.binder = binder
    }

    /**
     * 描述内容
     * Describe contents
     *
     * @return 0（没有特殊对象）
     */
    override fun describeContents(): Int {
        return 0
    }

    /**
     * 序列化到Parcel
     * Serialize to Parcel
     */
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeStrongBinder(this.binder)
    }

    /**
     * 从Parcel反序列化
     * Deserialize from Parcel
     */
    protected constructor(`in`: Parcel) {
        this.binder = `in`.readStrongBinder()
    }

    companion object {
        /** Parcelable创建器 Parcelable creator  */
        @JvmField
        val CREATOR: Creator<BinderContainer?> = object : Creator<BinderContainer?> {
            override fun createFromParcel(source: Parcel): BinderContainer {
                return BinderContainer(source)
            }

            override fun newArray(size: Int): Array<BinderContainer?> {
                return arrayOfNulls(size)
            }
        }
    }
}