package com.stellar.api

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
open class BinderContainer(var binder: IBinder?) : Parcelable {

    protected constructor(parcel: Parcel) : this(parcel.readStrongBinder())

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeStrongBinder(binder)
    }

    companion object {
        @JvmField
        val CREATOR: Creator<BinderContainer?> = object : Creator<BinderContainer?> {
            override fun createFromParcel(source: Parcel): BinderContainer = BinderContainer(source)
            override fun newArray(size: Int): Array<BinderContainer?> = arrayOfNulls(size)
        }
    }
}
