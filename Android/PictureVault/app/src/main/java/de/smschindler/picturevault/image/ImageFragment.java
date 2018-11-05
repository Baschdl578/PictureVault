package de.smschindler.picturevault.image;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import de.smschindler.picturevault.R;
import de.smschindler.picturevault.loaders.PictureLoader;

import java.io.File;

/**
 * Created by baschdl on 05.09.17.
 */

public class ImageFragment extends Fragment {
    public static final String ARG_ID = "id";
    public static final String ARG_NAME = "name";
    private SubsamplingScaleImageView imageView;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(
                R.layout.fragment_image, container, false);
        Bundle args = getArguments();
        Long id = args.getLong(ARG_ID);
        String name = args.getString(ARG_NAME);
        imageView = rootView.findViewById(R.id.image);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((FullImage) getActivity()).toggleInfo(false);
            }
        });

        new MyPictureLoader().execute(id.toString(), name);

        return rootView;
    }


    private class MyPictureLoader extends PictureLoader {

        @Override
        protected void onPostExecute(File file) {
            super.onPostExecute(file);
            if (file.exists()) {
                imageView.setImage(ImageSource.uri(Uri.fromFile(file)));
            }
        }
    }
}
