package org.baschdl.picturevault.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

/**
 * Created by baschdl on 22.08.17.
 */

public class Library implements Parcelable {
    public static final Parcelable.Creator<Library> CREATOR = new Parcelable.Creator<Library>() {
        public Library createFromParcel(Parcel p) {
            Long id = p.readLong();
            String name = p.readString();
            int count = p.readInt();
            long[] ids = p.createLongArray();
            Long[] newIds = new Long[ids.length];
            for (int i = 0; i < ids.length; i++) {
                newIds[0] = ids[0];
            }
            return new Library(id, name, count, newIds);
        }

        public Library[] newArray(int size) {
            return new Library[size];
        }
    };


    private Long id;
    private String name;
    private int count;
    private Long[] thumbIds;

    public Library(Long id, String name, int count, Long... thumbs) {
        this.id = id;
        this.name = name;
        this.count = count;
        ArrayList<Long> thmbs = new ArrayList<>();
        for (Long thumb : thumbs) {
            if (thumb != null && thumb > 0) {
                thmbs.add(thumb);
            }
        }
        thumbIds = new Long[thmbs.size()];
        thmbs.toArray(thumbIds);

    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long[] getThumbIds() {
        return thumbIds;
    }

    public void setThumbIds(Long[] thumbIds) {
        this.thumbIds = thumbIds;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(id);
        parcel.writeString(name);
        parcel.writeInt(count);
        long[] thumbs = new long[thumbIds.length];
        for (int j = 0; j < thumbIds.length; j++) {
            thumbs[j] = thumbIds[j];
        }
        parcel.writeLongArray(thumbs);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
