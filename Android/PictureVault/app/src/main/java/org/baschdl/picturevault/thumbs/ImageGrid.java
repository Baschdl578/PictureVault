package org.baschdl.picturevault.thumbs;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;
import android.widget.Toast;

import org.baschdl.picturevault.AppActivity;
import org.baschdl.picturevault.R;
import org.baschdl.picturevault.Server;
import org.baschdl.picturevault.model.Library;
import org.baschdl.picturevault.model.LibraryPicture;
import org.baschdl.picturevault.uielements.GridAutofitLayout;
import org.baschdl.picturevault.uielements.ImageVideoView;

import java.util.Locale;

/**
 * Created by baschdl on 21.08.17.
 */

public class ImageGrid extends AppActivity {
    public static final String EXTRA_LIBRARY = "libId";
    public static final String KEY_SCROLLPOS = "scrollpos";
    public static final String KEY_POSY = "posy";
    public static final String KEY_POSX = "posx";


    private Library lib;
    RecyclerView mRecyclerView;
    Parcelable layoutState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thumbs);
        lib = getIntent().getParcelableExtra(EXTRA_LIBRARY);
        if (lib != null)
            ((TextView) findViewById(R.id.title)).setText(lib.getName());
        Toolbar bar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(bar);
        if (Build.VERSION.SDK_INT >= 21) {
            getSupportActionBar().setElevation(getResources().getDimension(R.dimen.actionBarElevation));
        }

        layoutState = null;
        if (savedInstanceState != null) {
            layoutState = savedInstanceState.getParcelable(KEY_SCROLLPOS);
        }

        GridAutofitLayout layoutManager = new GridAutofitLayout(this, 350);
        mRecyclerView = (RecyclerView) findViewById(R.id.recycler);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(new ThumbGridAdapter(this));

        if (lib != null)
            new Loader().execute(lib.getId());
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putParcelable(KEY_SCROLLPOS, mRecyclerView.getLayoutManager().onSaveInstanceState());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_SCROLLPOS, mRecyclerView.getLayoutManager().onSaveInstanceState());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            layoutState = savedInstanceState.getParcelable(KEY_SCROLLPOS);
            if (mRecyclerView != null && mRecyclerView.getAdapter() != null && mRecyclerView.getAdapter().getItemCount() > 0) {
                mRecyclerView.getLayoutManager().onRestoreInstanceState(layoutState);
            }
        }
    }

    class Loader extends AsyncTask<Long, Void, LibraryPicture[]> {
        @Override
        protected LibraryPicture[] doInBackground(Long... libIds) {
            return Server.getPictureIds(ImageGrid.this, libIds[0]);
        }

        @Override
        protected void onPostExecute(LibraryPicture[] pics) {
            super.onPostExecute(pics);
            ((ThumbGridAdapter) mRecyclerView.getAdapter()).setPicIds(pics);
            if (layoutState != null) {
                mRecyclerView.getLayoutManager().onRestoreInstanceState(layoutState);
            }

        }
    }
}
