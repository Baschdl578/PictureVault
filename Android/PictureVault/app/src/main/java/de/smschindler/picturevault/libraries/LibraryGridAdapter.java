package de.smschindler.picturevault.libraries;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import de.smschindler.picturevault.R;
import de.smschindler.picturevault.model.Library;

import java.util.ArrayList;

/**
 * Adapter for the RecyclerView that holds the thumbnails in the ImageGrid
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
class LibraryGridAdapter extends RecyclerView.Adapter<LibraryHolder> {
    private Library[] libIds;
    private Context context;
    private ArrayList<LibraryHolder> holders;

    /**
     * Constructs a new Adapter with a given dataset and context
     *
     * @param pics    Dataset of pictureIds as Long[]
     * @param context Context
     */
    LibraryGridAdapter(Library[] pics, Context context) {
        super();
        this.libIds = pics;
        this.context = context;
        this.holders = new ArrayList<>(10);
        notifyDataSetChanged();
    }


    LibraryGridAdapter(Context context) {
        super();
        this.libIds = new Library[0];
        this.context = context;
        this.holders = new ArrayList<>(10);
        notifyDataSetChanged();
    }


    /**
     * Sets the dataset
     *
     * @param libIds New dataset
     */
    void setLibIds(Library[] libIds) {
        this.libIds = libIds;
        notifyDataSetChanged();
    }

    void stop() {
        for (LibraryHolder holder: holders) {
            holder.stopAnimation();
        }
    }
    void start() {
        for (LibraryHolder holder: holders) {
            holder.startAnimation();
        }
    }

    /**
     * Creates a new ViewHolder
     *
     * @param viewGroup Parent View
     * @param i         ViewType
     * @return New ThumbnailViewHolder
     */
    @Override
    public LibraryHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        View view = inflater.inflate(R.layout.recycler_library, viewGroup, false);
        LibraryHolder newHolder = new LibraryHolder(view);
        if (holders == null)
            this.holders = new ArrayList<>();
        holders.add(newHolder);
        return newHolder;
    }

    /**
     * Sets the id in the ViewHolder and lets it kick off loading the thumbnail
     *
     * @param holder   The ViewHolder
     * @param position The position of the ViewHolder
     */
    @Override
    public void onBindViewHolder(LibraryHolder holder, int position) {
        holder.setLib(libIds[position], context, position);
    }

    /**
     * Returns the number of 3-picture-rows
     *
     * @return Number of 3-picture-rows
     */
    @Override
    public int getItemCount() {
        return libIds.length;
    }
}
