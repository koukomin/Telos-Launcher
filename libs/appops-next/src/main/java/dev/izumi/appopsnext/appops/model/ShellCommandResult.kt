package dev.izumi.appopsnext.appops.model

import android.os.Parcel
import android.os.Parcelable

data class ShellCommandResult(val exitCode: Int, val stdout: String, val stderr: String, val timedOut: Boolean) : Parcelable {
    override fun describeContents() = 0
    override fun writeToParcel(out: Parcel, flags: Int) {
        out.writeInt(exitCode); out.writeString(stdout); out.writeString(stderr); out.writeInt(if (timedOut) 1 else 0)
    }
    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<ShellCommandResult> {
            override fun createFromParcel(source: Parcel) = ShellCommandResult(source.readInt(), source.readString().orEmpty(), source.readString().orEmpty(), source.readInt() != 0)
            override fun newArray(size: Int) = arrayOfNulls<ShellCommandResult>(size)
        }
    }
}
