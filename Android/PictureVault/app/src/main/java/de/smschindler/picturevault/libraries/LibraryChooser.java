package de.smschindler.picturevault.libraries;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PersistableBundle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.smschindler.picturevault.AppActivity;
import de.smschindler.picturevault.R;
import de.smschindler.picturevault.Server;
import de.smschindler.picturevault.Test;
import de.smschindler.picturevault.model.Library;
import de.smschindler.picturevault.settings.Settings;
import de.smschindler.picturevault.sync.ForegroundSync;
import de.smschindler.picturevault.uielements.GridAutofitLayout;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Activity to choose a media library
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class LibraryChooser extends AppActivity {
    private final static String KEY_SCROLLPOS = "scrollpos";

    RecyclerView mRecyclerView;
    TextView notConnected;
    Parcelable layoutState;
    LinearLayout notConnectedLayout;
    ImageButton refresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_libraries);

        layoutState = null;
        if (savedInstanceState != null) {
            layoutState = savedInstanceState.getParcelable(KEY_SCROLLPOS);
        }

        Toolbar bar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(bar);
        if (Build.VERSION.SDK_INT >= 21) {
            getSupportActionBar().setElevation(getResources().getDimension(R.dimen.actionBarElevation));
        }

        findViewById(R.id.settings).setOnClickListener(new View.OnClickListener() {
            @Override
                public void onClick(View view) {
                    Intent intent = new Intent(LibraryChooser.this, Settings.class);
                    LibraryChooser.this.startActivity(intent);
                }
        });

        findViewById(R.id.upload).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LibraryChooser.this, ForegroundSync.class);
                LibraryChooser.this.startService(intent);
            }
        });

        findViewById(R.id.test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(LibraryChooser.this, Test.class);
                LibraryChooser.this.startActivity(intent);
            }
        });

        notConnected = (TextView) findViewById(R.id.notconnected);
        notConnectedLayout = (LinearLayout) findViewById(R.id.notconectedLayout);
        refresh = (ImageButton) findViewById(R.id.refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new PulseCheck().execute();
            }
        });

        GridAutofitLayout layoutManager = new GridAutofitLayout(this, 500);
        mRecyclerView = (RecyclerView) findViewById(R.id.recycler);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(new LibraryGridAdapter(this));
        new PulseCheck().execute();

    }

    @Override
    protected void onRestart() {
        super.onRestart();
        ((LibraryGridAdapter) mRecyclerView.getAdapter()).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ((LibraryGridAdapter) mRecyclerView.getAdapter()).stop();
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

    class PulseCheck extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Animation rotation = AnimationUtils.loadAnimation(LibraryChooser.this, R.anim.counter_clockwise_refresh);
            rotation.setRepeatCount(Animation.INFINITE);
            refresh.startAnimation(rotation);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            return Server.checkPulse(LibraryChooser.this);
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            refresh.clearAnimation();
            if (aBoolean) {
                new LibLoader().execute();
                notConnectedLayout.setVisibility(View.GONE);
            } else {
                notConnectedLayout.setVisibility(View.VISIBLE);
                notConnected.setText(getString(R.string.notconnected));
            }
        }
    }

    class LibLoader extends AsyncTask<Void, Void, LinkedList<Library>> {
        @Override
        protected LinkedList<Library> doInBackground(Void... voids) {
            return Server.getLibraries(LibraryChooser.this);
        }

        @Override
        protected void onPostExecute(LinkedList<Library> libs) {
            super.onPostExecute(libs);
            if (libs.size() <= 0) {
                notConnected.setText(R.string.nolibraries);
                notConnectedLayout.setVisibility(View.VISIBLE);
                return;
            }
            Library[] libArray = new Library[libs.size()];
            int i = 0;
            Iterator<Library> iter = libs.iterator();
            while (iter.hasNext()) {
                libArray[i] = iter.next();
                i++;
            }
            ((LibraryGridAdapter) mRecyclerView.getAdapter()).setLibIds(libArray);
            if (layoutState != null) {
                mRecyclerView.getLayoutManager().onRestoreInstanceState(layoutState);
            }
        }
    }
}
