package org.baschdl.picturevault.image;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import org.baschdl.picturevault.R;
import org.baschdl.picturevault.model.LibraryPicture;
import org.baschdl.picturevault.uielements.ImageVideoView;

/**
 * Created by baschdl on 06.09.17.
 */

public class MediaHolder extends RecyclerView.ViewHolder {
    private ImageVideoView mediaView;
    private int position;

    MediaHolder(View itemView) {
        super(itemView);
        mediaView = (ImageVideoView) itemView;
    }

    public void setMedia(LibraryPicture media, int pos) {
        position = pos;
        mediaView.setElement(media);
    }

    public void stop() {
        mediaView.stop();
    }

    public void start() {
        mediaView.start();
    }

    public int getPos() {
        return position;
    }
}
