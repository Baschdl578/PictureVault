package org.baschdl.picturevault.image;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.baschdl.picturevault.AppActivity;
import org.baschdl.picturevault.MyApplication;
import org.baschdl.picturevault.R;
import org.baschdl.picturevault.loaders.PictureInfoLoader;
import org.baschdl.picturevault.model.LibraryPicture;
import org.baschdl.picturevault.model.Media;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Activity to view all videos and images in a library in a viewpager
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class FullImage extends FragmentActivity {
    public static final String EXTRA_ALLPICS = "images";
    public static final String EXTRA_POSITION = "pos";
    
    MediaFragmentAdapter adapter;
    private TextView path;
    private TextView resolution;
    private TextView created;
    private TextView modified;
    private TextView gpsCoordinates;
    private TextView gpsTitle;
    private MapView map;
    private FrameLayout mapFrame;
    private View mapOverlay;
    private BottomSheetBehavior behavior;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); /*
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions); */
        setContentView(R.layout.activity_fullimage);

        Parcelable[] parcelables = getIntent().getParcelableArrayExtra(EXTRA_ALLPICS);
        final LibraryPicture[] pictures = new LibraryPicture[parcelables.length];
        for (int i = 0; i < parcelables.length; i++) {
            pictures[i] = (LibraryPicture) parcelables[i];
        }
        int position = getIntent().getIntExtra(EXTRA_POSITION, 0);

        path = (TextView) findViewById(R.id.filename2);
        resolution = (TextView) findViewById(R.id.resolution2);
        created = (TextView) findViewById(R.id.created2);
        modified = (TextView) findViewById(R.id.modified2);
        gpsCoordinates = (TextView) findViewById(R.id.gpscoordiantes2);
        gpsTitle = (TextView) findViewById(R.id.gpscoordiantes1);
        map = (MapView) findViewById(R.id.map);
        mapFrame = (FrameLayout) findViewById(R.id.mapframe);
        mapOverlay = findViewById(R.id.mapOverlay);
        behavior = BottomSheetBehavior.from(findViewById(R.id.sheet));
        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        findViewById(R.id.space).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleInfo(true);
            }
        });

        ViewPager pager = (ViewPager) findViewById(R.id.pager);

        pager.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleInfo(false);
            }
        });

        adapter = new MediaFragmentAdapter(getSupportFragmentManager(), pictures);
        pager.setOffscreenPageLimit(2);
        pager.setAdapter(adapter);

        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                new InfoLoader().execute(pictures[position].id);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });



        if (position < pictures.length) {
            pager.setCurrentItem(position);
            new InfoLoader().execute(pictures[position].id);
        }
    }

    public void toggleInfo(boolean expandFully) {
        if (behavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
            behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            return;
        }
        if (behavior.getState() == BottomSheetBehavior.STATE_COLLAPSED && expandFully) {
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            return;
        }
        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    public boolean behaviorIsCollapsed() {
        return behavior.getState() == BottomSheetBehavior.STATE_COLLAPSED;
    }

    public void hideSheet() {
        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    public void showSheet() {
        behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    public void expandSheet() {
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }



    private class InfoLoader extends PictureInfoLoader {

        @Override
        public void onPostExecute(final Media media) {
            if (media == null) {
                Log.e("ERROR", "Could not load mediaInfo");
                return;
            }
            String fullpath = media.getPath();
            if (!fullpath.endsWith("/")) fullpath += "/";
            fullpath += media.getFilename();
            path.setText(fullpath);
            resolution.setText(media.getResolution());
            created.setText(timestampToDate(media.getCreated()));
            modified.setText(timestampToDate(media.getModified()));
            if (media.getLatitude() > 0.0 && media.getLongitude() > 0.0) {
                gpsCoordinates.setText(media.getLatitude() + ", " + media.getLongitude());
                gpsTitle.setVisibility(View.VISIBLE);
                gpsCoordinates.setVisibility(View.VISIBLE);

                mapFrame.setVisibility(View.VISIBLE);

                map.setVisibility(View.VISIBLE);
                map.setTileSource(TileSourceFactory.MAPNIK);

                mapOverlay.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String uri = String.format(Locale.ENGLISH, "http://maps.google.com/maps?q=loc:%f,%f (%s)", media.getLatitude(), media.getLongitude(), MyApplication.getInstance().getApplicationContext().getString(R.string.piclocation));
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                        intent.setClassName("com.google.android.apps.maps", "com.google.android.maps.MapsActivity");
                        try {
                            MyApplication.getInstance().getApplicationContext().startActivity(intent);
                        } catch (ActivityNotFoundException ex) {
                            try {
                                Intent unrestrictedIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                                MyApplication.getInstance().getApplicationContext().startActivity(unrestrictedIntent);
                            } catch (ActivityNotFoundException innerEx) {
                                Toast.makeText(AppActivity.getContext(), "Please install a maps application", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });


                IMapController mapController = map.getController();
                mapController.setZoom(14);
                GeoPoint startPoint = new GeoPoint(media.getLatitude(), media.getLongitude());
                mapController.setCenter(startPoint);

                LinkedList<OverlayItem> overlays = new LinkedList<>();
                overlays.add(new OverlayItem("Position", "Coordinates of the Image", new GeoPoint(media.getLatitude(), media.getLongitude())));
                ItemizedIconOverlay markersOverlay = new
                        ItemizedIconOverlay<>(overlays,
                        MyApplication.getInstance().getApplicationContext().getResources().getDrawable(R.drawable.marker_default), null, AppActivity.getContext());
                map.getOverlays().clear();
                map.getOverlays().add(markersOverlay);
            } else {
                gpsTitle.setVisibility(View.GONE);
                gpsCoordinates.setVisibility(View.GONE);
                mapFrame.setVisibility(View.GONE);
                map.setVisibility(View.GONE);
            }

        }

        private String timestampToDate(Long timestamp) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(timestamp);
            Date date = cal.getTime();
            return new SimpleDateFormat("dd. MMMM yyyy HH:mm").format(date);
        }
    }
}
