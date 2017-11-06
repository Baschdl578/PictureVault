package org.baschdl.picturevault.settings;

import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.support.v4.content.res.ResourcesCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Space;

import org.baschdl.picturevault.AppActivity;
import org.baschdl.picturevault.R;

/**
 * Setting action to view and manage settings
 *
 * @author Sebastian Schindler
 * @version 1.0
 */
public class Settings extends AppActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings);


        int lastModule = -1;
        ModuleSettings currentCategory = null;
        SettingObject[] allSettings = SettingsManager.getSettingArray(this);
        for (int i = 0; i < allSettings.length; i++) {
            if (allSettings[i].getCategorySort() != lastModule) {
                if (currentCategory != null) {
                    ((LinearLayout) findViewById(R.id.container)).addView(currentCategory);
                    //set.setElevation(2f);
                    View sp = new View(this);
                    LinearLayout.LayoutParams paramsSp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (Resources.getSystem().getDisplayMetrics().density * 5));
                    sp.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{ResourcesCompat.getColor(getResources(), R.color.grey600, null), ResourcesCompat.getColor(getResources(), R.color.transparent, null)}));
                    ((LinearLayout) findViewById(R.id.container)).addView(sp);
                    sp.setLayoutParams(paramsSp);
                    Space sp2 = new Space(this);
                    LinearLayout.LayoutParams paramsSp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (Resources.getSystem().getDisplayMetrics().density * 2));
                    ((LinearLayout) findViewById(R.id.container)).addView(sp2);
                    sp2.setLayoutParams(paramsSp2);
                }

                currentCategory = new ModuleSettings(this);
                currentCategory.setCategory(allSettings[i].getCategory());
                lastModule = allSettings[i].getCategorySort();
            }
            if (currentCategory == null) {
                currentCategory = new ModuleSettings(this);
                currentCategory.setCategory(allSettings[i].getCategory());
            }

            boolean last = false;
            if (i == allSettings.length - 1)  {
                last = true;
            } else if (allSettings[i + 1].getCategorySort() != lastModule) {
                last = true;
            }
            currentCategory.addSetting(allSettings[i], last);

        }

        ((LinearLayout) findViewById(R.id.container)).addView(currentCategory);
        //set.setElevation(2f);
        View sp = new View(this);
        LinearLayout.LayoutParams paramsSp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (Resources.getSystem().getDisplayMetrics().density * 5));
        sp.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{ResourcesCompat.getColor(getResources(), R.color.grey600, null), ResourcesCompat.getColor(getResources(), R.color.transparent, null)}));
        ((LinearLayout) findViewById(R.id.container)).addView(sp);
        sp.setLayoutParams(paramsSp);
        Space sp2 = new Space(this);
        LinearLayout.LayoutParams paramsSp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (Resources.getSystem().getDisplayMetrics().density * 2));
        ((LinearLayout) findViewById(R.id.container)).addView(sp2);
        sp2.setLayoutParams(paramsSp2);
    }
}
