package org.baschdl.picturevault.uielements;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;

import java.util.List;

/**
 * Created by baschdl on 06.09.17.
 */

public class SavingRecyclerView extends RecyclerView {
    Parcelable layoutManagerSavedState;
    private static final String SAVED_LAYOUT_MANAGER = "layoutman";


    public SavingRecyclerView(Context context) {
        super(context);
    }

    public SavingRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SavingRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            layoutManagerSavedState = ((Bundle) state).getParcelable(SAVED_LAYOUT_MANAGER);
        }
        super.onRestoreInstanceState(state);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        super.onSaveInstanceState();
        Bundle bundle = new Bundle();
        bundle.putParcelable(SAVED_LAYOUT_MANAGER, getLayoutManager().onSaveInstanceState());
        return bundle;
    }

    private void restoreLayoutManagerPosition() {
        if (layoutManagerSavedState != null) {
            getLayoutManager().onRestoreInstanceState(layoutManagerSavedState);
        }
    }


}
