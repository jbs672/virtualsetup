package com.example.desktopenv;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Moldura em baixo-relevo que contem o monitor do Fantasy PC. */
public class FantasyScreenView extends FrameLayout {

    private FantasyMonitorView monitor;

    public FantasyScreenView(Context context) {
        super(context);
        init(context);
    }

    public FantasyScreenView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setBackgroundResource(R.drawable.win_sunken);
        int p = PixelUi.dp(context, 2);
        setPadding(p, p, p, p);
        PixelUi.flatten(this);
        monitor = new FantasyMonitorView(context);
        addView(monitor, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public FantasyMonitorView getMonitor() {
        return monitor;
    }
}
