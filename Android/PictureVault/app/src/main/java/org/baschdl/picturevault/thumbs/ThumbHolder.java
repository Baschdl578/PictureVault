package org.baschdl.picturevault.thumbs;

import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;

import org.baschdl.picturevault.R;
import org.baschdl.picturevault.loaders.ThumbLoader;

import java.io.File;

/**
 * ViewHolder for a row of 3 square picture thumbnails that loads the pictures asynchronously
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
class ThumbHolder extends RecyclerView.ViewHolder {
    private ImageView img;
    private int pos;

    /**
     * Constructs a ViewHolder and sets layout
     *
     * @param itemView ViewHolder view
     */
    ThumbHolder(View itemView) {
        super(itemView);
        img = itemView.findViewById(R.id.thumb);
    }

    public ImageView getImageView() {
        return img;
    }

    /**
     * Sets all Libraries and the position of the holder and kicks off the pictureLoader
     *
     * @param picId     Picture Id
     * @param position  Position of the ViewHolder
     */
    public void setThumb(long picId, int position) {
        this.pos = position;
        this.img.setImageResource(0);
        new ThumbHolder.CustomImageLoader(pos).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, picId);
    }

    /**
     * Implementation of ThumbLoader that adds the image to the image
     *
     * @author Sebastian Schindler
     * @version 1.0
     */
    private class CustomImageLoader extends ThumbLoader {
        private int position;

        CustomImageLoader(int pos) {
            this.position = pos;
        }

        @Override
        protected void onPostExecute(final File file) {
            super.onPostExecute(file);
            if (ThumbHolder.this.pos == position) {
                if (file.exists())
                    img.setImageURI(Uri.fromFile(file));
            }
        }
    }
}
