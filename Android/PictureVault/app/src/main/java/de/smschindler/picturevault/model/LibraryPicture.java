package de.smschindler.picturevault.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by baschdl on 29.08.17.
 */

public class LibraryPicture implements Parcelable {
    public static final Parcelable.Creator<LibraryPicture> CREATOR = new Parcelable.Creator<LibraryPicture>() {
        public LibraryPicture createFromParcel(Parcel p) {
            Long id = p.readLong();
            String name = p.readString();
            Long duration = p.readLong();
            Long size = p.readLong();
            LibraryPicture out = new LibraryPicture();
            out.id = id;
            out.name = name;
            out.duration = duration;
            out.size = size;
            return out;
        }

        public LibraryPicture[] newArray(int size) {
            return new LibraryPicture[size];
        }
    };

    public Long id;
    public String name;
    public Long duration;
    public Long size;

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(id);
        parcel.writeString(name);
        parcel.writeLong(duration);
        parcel.writeLong(size);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
