package de.smschindler.picturevault.image;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import de.smschindler.picturevault.model.LibraryPicture;

/**
 * Adapter that creates image and video fragements
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
class MediaFragmentAdapter extends FragmentStatePagerAdapter {
    private LibraryPicture[] dataSet;

    MediaFragmentAdapter(FragmentManager fm, LibraryPicture[] dataSet) {
        super(fm);
        this.dataSet = dataSet;
    }

    @Override
    public Fragment getItem(int i) {
        Fragment fragment;
        if (dataSet[i].duration < 0) {
            fragment = new ImageFragment();
            Bundle args = new Bundle();
            args.putLong(ImageFragment.ARG_ID, dataSet[i].id);
            args.putString(ImageFragment.ARG_NAME, dataSet[i].name);
            fragment.setArguments(args);
        } else {
            fragment = new VideoFragment();
            Bundle args = new Bundle();
            args.putLong(VideoFragment.ARG_ID, dataSet[i].id);
            args.putString(VideoFragment.ARG_NAME, dataSet[i].name);
            args.putLong(VideoFragment.ARG_SIZE, dataSet[i].size);
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Override
    public int getCount() {
        return dataSet.length;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return dataSet[position].name;
    }

}
