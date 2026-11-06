package com.example.desktopenv;

import android.content.Context;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Icone da area de trabalho: toque simples seleciona, duplo clique (por tempo) abre,
 * arrastar move o icone.
 */
public class DesktopIconView extends LinearLayout {

    public interface IconListener {
        void onIconSelected(DesktopIconView icon);
        void onIconOpened(DesktopIconView icon);
    }

    private static final long DOUBLE_CLICK_MS = 500L;

    private final int appId;
    private final TextView label;
    private final int touchSlop;
    private IconListener listener;
    private boolean iconSelected;

    private float downRawX, downRawY;
    private int startLeft, startTop;
    private boolean dragging;
    private long lastTap;

    public DesktopIconView(Context context, int appId, int iconType, String text) {
        super(context);
        this.appId = appId;
        this.touchSlop = Math.max(ViewConfiguration.get(context).getScaledTouchSlop(), PixelUi.dp(context, 8));
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(PixelUi.dp(context, 2), PixelUi.dp(context, 4), PixelUi.dp(context, 2), PixelUi.dp(context, 2));
        PixelUi.flatten(this);

        PixelIconView icon = new PixelIconView(context, iconType);
        addView(icon, new LayoutParams(PixelUi.dp(context, 34), PixelUi.dp(context, 34)));

        label = PixelUi.label(context, text);
        label.setSingleLine(false);
        label.setMaxLines(2);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        label.setPadding(PixelUi.dp(context, 2), PixelUi.dp(context, 1), PixelUi.dp(context, 2), PixelUi.dp(context, 1));
        LayoutParams llp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = PixelUi.dp(context, 3);
        addView(label, llp);
    }

    public int getAppId() {
        return appId;
    }

    public void setIconListener(IconListener l) {
        this.listener = l;
    }

    public void setIconSelected(boolean value) {
        iconSelected = value;
        label.setBackgroundColor(value ? PixelUi.SELECTION : PixelUi.TRANSPARENT);
    }

    public boolean isIconSelected() {
        return iconSelected;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        ViewGroup.LayoutParams raw = getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return true;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ContextMenuView.dismissCurrent();
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startLeft = lp.leftMargin;
                startTop = lp.topMargin;
                dragging = false;
                if (listener != null) listener.onIconSelected(this);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - downRawX;
                float dy = e.getRawY() - downRawY;
                if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) dragging = true;
                if (dragging) {
                    View parent = (View) getParent();
                    int left = startLeft + Math.round(dx);
                    int top = startTop + Math.round(dy);
                    if (parent != null) {
                        left = Math.max(0, Math.min(left, parent.getWidth() - getWidth()));
                        top = Math.max(0, Math.min(top, parent.getHeight() - getHeight()));
                    }
                    lp.leftMargin = left;
                    lp.topMargin = top;
                    setLayoutParams(lp);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastTap <= DOUBLE_CLICK_MS) {
                        lastTap = 0L;
                        if (listener != null) listener.onIconOpened(this);
                    } else {
                        lastTap = now;
                    }
                } else {
                    lastTap = 0L;
                }
                dragging = false;
                return true;
            default:
                return true;
        }
    }
}
