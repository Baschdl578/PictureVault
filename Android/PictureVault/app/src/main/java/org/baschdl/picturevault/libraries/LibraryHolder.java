package org.baschdl.picturevault.libraries;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.baschdl.picturevault.MyApplication;
import org.baschdl.picturevault.R;
import org.baschdl.picturevault.loaders.ThumbLoader;
import org.baschdl.picturevault.model.Library;
import org.baschdl.picturevault.thumbs.ImageGrid;
import org.baschdl.picturevault.uielements.Slideshow;

import java.io.File;
import java.util.Locale;
/**
 * ViewHolder for a row of 3 square picture thumbnails that loads the pictures asynchronously
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
class LibraryHolder extends RecyclerView.ViewHolder {
    private Slideshow img;
    private TextView name;
    private TextView count;
    private int pos;
    private Library lib;

    /**
     * Constructs a ViewHolder and sets layout
     *
     * @param itemView ViewHolder view
     */
    LibraryHolder(View itemView) {
        super(itemView);
        this.img = (Slideshow) itemView.findViewById(R.id.slideshow);
        this.name = (TextView) itemView.findViewById(R.id.name);
        this.count = (TextView) itemView.findViewById(R.id.count);

        img.setOnClickListener(new View.OnClickListener() {
                                   @Override
                                   public void onClick(View view) {
                                       Intent intent = new Intent(MyApplication.getInstance().getApplicationContext(), ImageGrid.class);
                                       intent.putExtra(ImageGrid.EXTRA_LIBRARY, lib);
                                       MyApplication.getInstance().getApplicationContext().startActivity(intent);
                                   }
        });
    }

    /**
     * Sets all Libraries and the position of the holder and kicks off the pictureLoader
     *
     * @param lib       Library
     * @param context   Context
     * @param position  Position of the ViewHolder
     */
    public void setLib(Library lib, Context context, int position) {
        this.lib = lib;
        this.pos = position;
        this.img.setImageResource(0);
        this.name.setText(lib.getName());
        this.count.setText(String.format(Locale.getDefault(), "%1$d", lib.getCount()));
        for (Long thumbId: lib.getThumbIds()) {
            if (thumbId > 0)
                new LibraryHolder.CustomImageLoader(pos).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, thumbId);
        }
    }

    void stopAnimation() {
        this.img.stop();
    }

    void startAnimation() {
        this.img.start();
    }

    /**
     * Implementation of ThumbLoader that adds the image to the slideshow
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
            if (LibraryHolder.this.pos == position)
                img.addId(file);
        }
    }
}
