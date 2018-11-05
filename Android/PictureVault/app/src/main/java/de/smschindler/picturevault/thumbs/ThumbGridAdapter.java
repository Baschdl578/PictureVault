package de.smschindler.picturevault.thumbs;

import android.content.Context;
import android.content.Intent;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import de.smschindler.picturevault.MyApplication;
import de.smschindler.picturevault.R;
import de.smschindler.picturevault.image.FullImage;
import de.smschindler.picturevault.model.LibraryPicture;

/**
 * Adapter for the RecyclerView that holds the thumbnails in the ImageGrid
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
class ThumbGridAdapter extends RecyclerView.Adapter<ThumbHolder> {
    private LibraryPicture[] picIds;
    private Context context;

    /**
     * Constructs a new Adapter with a given dataset and context
     *
     * @param pics    Dataset of pictureIds as Long[]
     * @param context Context
     */
    ThumbGridAdapter(LibraryPicture[] pics, Context context) {
        super();
        this.picIds = pics;
        this.context = context;
        notifyDataSetChanged();
    }


    ThumbGridAdapter(Context context) {
        super();
        this.picIds = new LibraryPicture[0];
        this.context = context;
        notifyDataSetChanged();
    }


    /**
     * Sets the dataset
     *
     * @param libIds New dataset
     */
    void setPicIds(LibraryPicture[] libIds) {
        this.picIds = libIds;
        notifyDataSetChanged();
    }

    /**
     * Creates a new ViewHolder
     *
     * @param viewGroup Parent View
     * @param i         ViewType
     * @return New ThumbnailViewHolder
     */
    @Override
    public ThumbHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        View view = inflater.inflate(R.layout.recycler_thumb, viewGroup, false);
        ThumbHolder newHolder = new ThumbHolder(view);
        return newHolder;
    }

    /**
     * Sets the id in the ViewHolder and lets it kick off loading the thumbnail
     *
     * @param holder   The ViewHolder
     * @param position The position of the ViewHolder
     */
    @Override
    public void onBindViewHolder(final ThumbHolder holder, int position) {
        holder.setThumb(picIds[position].id, position);
        holder.getImageView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MyApplication.getInstance().getApplicationContext(), FullImage.class);
                intent.putExtra(FullImage.EXTRA_ALLPICS, picIds);
                intent.putExtra(FullImage.EXTRA_POSITION, holder.getAdapterPosition());
                MyApplication.getInstance().getApplicationContext().startActivity(intent);
            }
        });
    }

    /**
     * Returns the number of 3-picture-rows
     *
     * @return Number of 3-picture-rows
     */
    @Override
    public int getItemCount() {
        return picIds.length;
    }
}
