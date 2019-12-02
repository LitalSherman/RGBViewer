package com.sherman.lital.rgbviewer.view;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sherman.lital.rgbviewer.R;

import java.text.DecimalFormat;

public class CommonColorView extends LinearLayout {

    public TextView rgbPercentageV;
    public TextView rValueV;
    public TextView gValueV;
    public TextView bValueV;

    public CommonColorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater.from(context).inflate(R.layout.common_color_view, this, true);

        setOrientation(LinearLayout.VERTICAL);
        setGravity(Gravity.CENTER);

        rgbPercentageV = findViewById(R.id.rgb_percentage);
        rValueV = findViewById(R.id.R);
        gValueV = findViewById(R.id.G);
        bValueV = findViewById(R.id.B);
    }

    public void setRgbPercentage(double percentage) {
        DecimalFormat df = new DecimalFormat("##.##%");
        String formattedPercent = df.format(percentage);
        rgbPercentageV.setText(formattedPercent);
    }

    public void setRValue(int rValue) {
        rValueV.setText(getResources().getString(R.string.R) + Integer.toString(rValue));
    }

    public void setGValue(int gValue) {
        gValueV.setText(getResources().getString(R.string.G) + Integer.toString(gValue));
    }

    public void setBValue(int bValue) {
        bValueV.setText(getResources().getString(R.string.B) + Integer.toString(bValue));
    }

    public void setColor(String hexColor) {
        rgbPercentageV.setBackgroundColor(Color.parseColor("#" + hexColor));
    }
}
