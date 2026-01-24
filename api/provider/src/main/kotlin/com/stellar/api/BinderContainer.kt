package com.stellar.api

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
open class BinderContainer : Parcelable {
    var binder: IBinder?

    constructor(binder: IBinder?) {
        this.binder = binder
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeStrongBinder(this.binder)
    }

    protected constructor(`in`: Parcel) {
        this.binder = `in`.readStrongBinder()
    }

    companion object {
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
