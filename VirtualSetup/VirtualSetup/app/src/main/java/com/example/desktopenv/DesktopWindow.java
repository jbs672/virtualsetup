package com.example.desktopenv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Janela classica estilo Win95/2000: moldura chanfrada, barra de titulo azul-marinho
 * plana, botoes Minimizar / Maximizar / Fechar desenhados em pixel, arrasto pela
 * barra de titulo, duplo clique (por tempo) para maximizar e alca de redimensionamento.
 */
public class DesktopWindow extends FrameLayout {

    public interface WindowListener {
        void onClose(DesktopWindow window);
        void onFocus(DesktopWindow window);
        void onMinimize(DesktopWindow window);
    }

    /** Permite que o conteudo cancele o fechamento (ex.: "Deseja salvar?"). */
    public interface CloseHandler {
        boolean onCloseRequested(DesktopWindow window);
    }

    public interface OnSafeClick {
        void onClick(View v);
    }

    private static final long SAFE_CLICK_INTERVAL_MS = 350L;
    private static final long DOUBLE_CLICK_MS = 450L;

    /** Envolve um clique com protecao contra toques repetidos acidentais. */
    public static View.OnClickListener safeClick(final OnSafeClick target) {
        return new View.OnClickListener() {
            private long lastClick = 0L;

            @Override
            public void onClick(View v) {
                long now = SystemClock.uptimeMillis();
                if (now - lastClick < SAFE_CLICK_INTERVAL_MS) return;
                lastClick = now;
                target.onClick(v);
            }
        };
    }

    private String title;
    private final int iconType;
    private final LinearLayout frame;
    private final LinearLayout titleBar;
    private final TextView titleText;
    private final PixelIconView titleIcon;
    private final CaptionButton minButton;
    private final CaptionButton maxButton;
    private final CaptionButton closeButton;
    private final FrameLayout content;
    private final ResizeGrip resizeGrip;
    private final int touchSlop;

    private WindowListener listener;
    private CloseHandler closeHandler;
    private boolean active;
    private boolean maximized;
    private boolean minimized;
    private boolean resizable = true;

    private int savedLeft, savedTop, savedWidth, savedHeight;
    private float downRawX, downRawY;
    private int startLeft, startTop, startWidth, startHeight;
    private boolean dragging;
    private long lastTitleTap;

    public DesktopWindow(Context context, String title) {
        this(context, title, PixelIconView.TYPE_APP);
    }

    public DesktopWindow(Context context, String title, int iconType) {
        super(context);
        this.title = title;
        this.iconType = iconType;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        PixelUi.flatten(this);

        frame = new LinearLayout(context);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setBackgroundResource(R.drawable.win_window);
        int p = dp(3);
        frame.setPadding(p, p, p, p);

        titleBar = new LinearLayout(context);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackgroundColor(PixelUi.TITLE_ACTIVE);
        titleBar.setPadding(dp(3), 0, dp(2), 0);

        titleIcon = new PixelIconView(context, iconType);
        titleBar.addView(titleIcon, new LinearLayout.LayoutParams(dp(16), dp(16)));

        titleText = PixelUi.label(context, title);
        titleText.setTypeface(PixelUi.FONT_BOLD);
        titleText.setTextColor(PixelUi.TITLE_TEXT_ACTIVE);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(4);
        titleBar.addView(titleText, tlp);

        minButton = new CaptionButton(context, CaptionButton.MINIMIZE);
        maxButton = new CaptionButton(context, CaptionButton.MAXIMIZE);
        closeButton = new CaptionButton(context, CaptionButton.CLOSE);
        titleBar.addView(minButton, new LinearLayout.LayoutParams(dp(22), dp(20)));
        titleBar.addView(maxButton, new LinearLayout.LayoutParams(dp(22), dp(20)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(22), dp(20));
        clp.leftMargin = dp(2);
        titleBar.addView(closeButton, clp);

        minButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onMinimize(DesktopWindow.this);
            }
        });
        maxButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMaximize();
            }
        });
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestClose();
            }
        });

        titleBar.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                return handleTitleTouch(e);
            }
        });

        frame.addView(titleBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

        content = new FrameLayout(context);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        contentLp.topMargin = dp(2);
        frame.addView(content, contentLp);

        addView(frame, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        resizeGrip = new ResizeGrip(context);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(18), dp(18),
                Gravity.BOTTOM | Gravity.RIGHT);
        glp.rightMargin = dp(3);
        glp.bottomMargin = dp(3);
        addView(resizeGrip, glp);
        resizeGrip.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                return handleResizeTouch(e);
            }
        });
    }

    // ------------------------------------------------------------------ API publica

    public String getTitle() {
        return title;
    }

    public void setTitle(String newTitle) {
        this.title = newTitle;
        titleText.setText(newTitle);
    }

    public int getIconType() {
        return iconType;
    }

    public void setContent(View view) {
        content.removeAllViews();
        content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public View getContentView() {
        return content.getChildCount() > 0 ? content.getChildAt(0) : null;
    }

    public void setWindowListener(WindowListener l) {
        this.listener = l;
    }

    public void setCloseHandler(CloseHandler h) {
        this.closeHandler = h;
    }

    public void setResizable(boolean value) {
        resizable = value;
        resizeGrip.setVisibility(value && !maximized ? VISIBLE : GONE);
    }

    public void setMaximizable(boolean value) {
        maxButton.setVisibility(value ? VISIBLE : GONE);
    }

    public void setMinimizable(boolean value) {
        minButton.setVisibility(value ? VISIBLE : GONE);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isMaximized() {
        return maximized;
    }

    public boolean isMinimized() {
        return minimized;
    }

    public void setActive(boolean value) {
        active = value;
        titleBar.setBackgroundColor(value ? PixelUi.TITLE_ACTIVE : PixelUi.TITLE_INACTIVE);
        titleText.setTextColor(value ? PixelUi.TITLE_TEXT_ACTIVE : PixelUi.TITLE_TEXT_INACTIVE);
    }

    public void setMinimized(boolean value) {
        minimized = value;
        setVisibility(value ? GONE : VISIBLE);
    }

    /** Pede o fechamento (respeita o CloseHandler). */
    public void requestClose() {
        if (closeHandler != null && !closeHandler.onCloseRequested(this)) {
            return;
        }
        if (listener != null) listener.onClose(this);
    }

    public void toggleMaximize() {
        if (maxButton.getVisibility() != VISIBLE) return;
        ViewGroup.LayoutParams raw = getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
        if (!maximized) {
            savedLeft = lp.leftMargin;
            savedTop = lp.topMargin;
            savedWidth = getWidth();
            savedHeight = getHeight();
            lp.leftMargin = 0;
            lp.topMargin = 0;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            maximized = true;
            maxButton.setGlyph(CaptionButton.RESTORE);
        } else {
            lp.leftMargin = savedLeft;
            lp.topMargin = savedTop;
            lp.width = savedWidth;
            lp.height = savedHeight;
            maximized = false;
            maxButton.setGlyph(CaptionButton.MAXIMIZE);
        }
        resizeGrip.setVisibility(resizable && !maximized ? VISIBLE : GONE);
        setLayoutParams(lp);
    }

    // ------------------------------------------------------------------ Toque

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && listener != null) {
            listener.onFocus(this);
        }
        return false;
    }

    /** Consome toques em areas vazias para nao "vazar" para o papel de parede. */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    private boolean handleTitleTouch(MotionEvent e) {
        ViewGroup.LayoutParams raw = getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return true;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startLeft = lp.leftMargin;
                startTop = lp.topMargin;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (maximized) return true;
                float dx = e.getRawX() - downRawX;
                float dy = e.getRawY() - downRawY;
                if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    dragging = true;
                }
                if (dragging) {
                    moveTo(startLeft + Math.round(dx), startTop + Math.round(dy));
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastTitleTap <= DOUBLE_CLICK_MS) {
                        lastTitleTap = 0L;
                        toggleMaximize();
                    } else {
                        lastTitleTap = now;
                    }
                }
                dragging = false;
                return true;
            default:
                return true;
        }
    }

    private void moveTo(int left, int top) {
        View parent = (View) getParent();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        if (parent != null) {
            int minVisible = dp(48);
            int pw = parent.getWidth();
            int ph = parent.getHeight();
            left = clamp(left, -getWidth() + minVisible, pw - minVisible);
            top = clamp(top, 0, Math.max(0, ph - dp(30)));
        }
        lp.leftMargin = left;
        lp.topMargin = top;
        setLayoutParams(lp);
    }

    private boolean handleResizeTouch(MotionEvent e) {
        if (!resizable || maximized) return false;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (listener != null) listener.onFocus(this);
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startWidth = getWidth();
                startHeight = getHeight();
                return true;
            case MotionEvent.ACTION_MOVE:
                int w = Math.max(dp(180), startWidth + Math.round(e.getRawX() - downRawX));
                int h = Math.max(dp(120), startHeight + Math.round(e.getRawY() - downRawY));
                lp.width = w;
                lp.height = h;
                setLayoutParams(lp);
                return true;
            default:
                return true;
        }
    }

    private int dp(float v) {
        return PixelUi.dp(getContext(), v);
    }

    private static int clamp(int v, int min, int max) {
        if (max < min) return min;
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    // ------------------------------------------------------------------ Botoes da barra de titulo

    static class CaptionButton extends View {
        static final int MINIMIZE = 0;
        static final int MAXIMIZE = 1;
        static final int RESTORE = 2;
        static final int CLOSE = 3;

        private int glyph;
        private final Paint paint = new Paint();

        CaptionButton(Context c, int glyph) {
            super(c);
            this.glyph = glyph;
            setBackgroundResource(R.drawable.win_button);
            setClickable(true);
            setFocusable(false);
            PixelUi.flatten(this);
            paint.setAntiAlias(false);
            paint.setColor(0xFF000000);
        }

        void setGlyph(int g) {
            glyph = g;
            invalidate();
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float s = Math.max(1f, getResources().getDisplayMetrics().density);
            float off = isPressed() ? s : 0f;
            float cx = getWidth() / 2f + off;
            float cy = getHeight() / 2f + off;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF000000);
            switch (glyph) {
                case MINIMIZE:
                    canvas.drawRect(cx - 3.5f * s, cy + 2f * s, cx + 3.5f * s, cy + 4f * s, paint);
                    break;
                case MAXIMIZE:
                    frameRect(canvas, cx - 4.5f * s, cy - 4f * s, cx + 4.5f * s, cy + 4f * s, s);
                    break;
                case RESTORE:
                    frameRect(canvas, cx - 1.5f * s, cy - 4.5f * s, cx + 4.5f * s, cy + 0.5f * s, s);
                    paint.setColor(PixelUi.FACE);
                    canvas.drawRect(cx - 4.5f * s, cy - 1.5f * s, cx + 1.5f * s, cy + 4.5f * s, paint);
                    paint.setColor(0xFF000000);
                    frameRect(canvas, cx - 4.5f * s, cy - 1.5f * s, cx + 1.5f * s, cy + 4.5f * s, s);
                    break;
                case CLOSE:
                default:
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.6f * s);
                    canvas.drawLine(cx - 3.5f * s, cy - 3f * s, cx + 3.5f * s, cy + 3f * s, paint);
                    canvas.drawLine(cx + 3.5f * s, cy - 3f * s, cx - 3.5f * s, cy + 3f * s, paint);
                    paint.setStyle(Paint.Style.FILL);
                    break;
            }
        }

        private void frameRect(Canvas c, float l, float t, float r, float b, float s) {
            c.drawRect(l, t, r, t + 2f * s, paint);
            c.drawRect(l, b - s, r, b, paint);
            c.drawRect(l, t, l + s, b, paint);
            c.drawRect(r - s, t, r, b, paint);
        }
    }

    // ------------------------------------------------------------------ Alca de redimensionamento

    static class ResizeGrip extends View {
        private final Paint paint = new Paint();

        ResizeGrip(Context c) {
            super(c);
            paint.setAntiAlias(false);
            PixelUi.flatten(this);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = Math.max(1f, getResources().getDisplayMetrics().density);
            int w = getWidth();
            int h = getHeight();
            paint.setStrokeWidth(s);
            for (int i = 0; i < 3; i++) {
                float o = (3 + i * 4) * s;
                paint.setColor(PixelUi.HIGHLIGHT);
                canvas.drawLine(w - o, h, w, h - o, paint);
                paint.setColor(PixelUi.SHADOW);
                canvas.drawLine(w - o + s, h, w, h - o + s, paint);
                canvas.drawLine(w - o + 2f * s, h, w, h - o + 2f * s, paint);
            }
        }
    }
}
