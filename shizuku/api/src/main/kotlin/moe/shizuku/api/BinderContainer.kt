package moe.shizuku.api

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable

/**
 * Shizuku API 兼容类
 * 用于包装 IBinder 以便通过 ContentProvider 传递
 */
class BinderContainer(val binder: IBinder?) : Parcelable {

    constructor(parcel: Parcel) : this(parcel.readStrongBinder())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeStrongBinder(binder)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<BinderContainer> {
        override fun createFromParcel(parcel: Parcel): BinderContainer {
            return BinderContainer(parcel)
        }

        override fun newArray(size: Int): Array<BinderContainer?> {
            return arrayOfNulls(size)
        }
    }
}
