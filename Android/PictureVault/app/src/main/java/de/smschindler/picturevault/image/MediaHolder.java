package de.smschindler.picturevault.image;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

import de.smschindler.picturevault.model.LibraryPicture;
import de.smschindler.picturevault.uielements.ImageVideoView;

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
