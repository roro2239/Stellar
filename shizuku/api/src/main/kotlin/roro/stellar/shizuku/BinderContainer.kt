package roro.stellar.shizuku

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable

/**
 * Shizuku 兼容的 Binder 容器
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
