package io.morgan.mio.domain.model

import android.os.Parcelable

class AndroidDomainType : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: android.os.Parcel, flags: Int) = Unit
}
