package com.example.desktopenv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ImageView;

/**
 * Papel de parede da area de trabalho (cor solida, padrao 8x8 estilo Win3.1/95 ou imagem).
 *
 * DETECCAO DE TOQUE LONGO RIGOROSA:
 *  - Handler + Runnable com atraso de LONG_PRESS_DELAY_MS (1000 ms).
 *  - Cancelado se o dedo se mover alem da tolerancia (logica de arrasto),
 *    se um segundo dedo tocar a tela, ou se o toque for cancelado.
 *  - Ao disparar, o tempo real decorrido e conferido novamente (SystemClock) antes de abrir o menu.
 *  - Toques menores que 1000 ms sem movimento sao tratados como clique simples.
 *  - Clique direito de mouse abre o menu imediatamente (comportamento de PC).
 */
public class WallpaperView extends ImageView {

    public interface WallpaperGestureListener {
        void onWallpaperTap(float x, float y);
        void onWallpaperLongPress(float x, float y);
    }

    public static final long LONG_PRESS_DELAY_MS = 1000L;

    public static final int MODE_SOLID = 0;
    public static final int MODE_PATTERN = 1;
    public static final int MODE_IMAGE = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            onLongPressTimeout();
        }
    };

    private WallpaperGestureListener gestureListener;
    private int moveTolerancePx;

    private float downX;
    private float downY;
    private long downTime;
    private boolean tracking;
    private boolean moved;
    private boolean longPressFired;

    private int mode = MODE_SOLID;
    private int backgroundColor = PixelUi.DESKTOP;
    private int foregroundColor = 0xFF000000;
    private byte[] pattern;
    private int patternScalePx;
    private final Paint patternPaint = new Paint();

    public WallpaperView(Context context) {
        super(context);
        init(context);
    }

    public WallpaperView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.CENTER_CROP);
        int slop = ViewConfiguration.get(context).getScaledTouchSlop();
        moveTolerancePx = Math.max(slop, PixelUi.dp(context, 10));
        patternScalePx = Math.max(1, PixelUi.dp(context, 2));
        patternPaint.setFilterBitmap(false);
        patternPaint.setAntiAlias(false);
        setHapticFeedbackEnabled(true);
        PixelUi.flatten(this);
    }

    public void setGestureListener(WallpaperGestureListener l) {
        this.gestureListener = l;
    }

    /** Tamanho em pixels de cada "pixel" do padrao 8x8 (preview usa escala menor). */
    public void setPatternScale(int px) {
        patternScalePx = Math.max(1, px);
        if (mode == MODE_PATTERN) buildTile();
        invalidate();
    }

    // ------------------------------------------------------------------ Aparencia

    public void applyPreset(WallpaperManager.Preset preset) {
        if (preset == null) return;
        if (preset.type == WallpaperManager.TYPE_PATTERN && preset.pattern != null) {
            setPattern(preset.pattern, preset.foreground, preset.background);
        } else if (preset.type == WallpaperManager.TYPE_IMAGE && preset.resId != 0) {
            backgroundColor = preset.background;
            setImageWallpaper(preset.resId);
        } else {
            setSolidColor(preset.background);
        }
    }

    public void setSolidColor(int color) {
        mode = MODE_SOLID;
        backgroundColor = color;
        pattern = null;
        patternPaint.setShader(null);
        setImageDrawable(null);
        invalidate();
    }

    public void setPattern(byte[] rows, int fg, int bg) {
        mode = MODE_PATTERN;
        pattern = rows;
        foregroundColor = fg;
        backgroundColor = bg;
        setImageDrawable(null);
        buildTile();
        invalidate();
    }

    public void setImageWallpaper(int resId) {
        mode = MODE_IMAGE;
        patternPaint.setShader(null);
        setImageResource(resId);
        invalidate();
    }

    private void buildTile() {
        if (pattern == null || pattern.length < 8) return;
        Bitmap small = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 8; y++) {
            int row = pattern[y] & 0xFF;
            for (int x = 0; x < 8; x++) {
                boolean on = ((row >> (7 - x)) & 1) != 0;
                small.setPixel(x, y, on ? foregroundColor : backgroundColor);
            }
        }
        int size = 8 * patternScalePx;
        Bitmap tile = Bitmap.createScaledBitmap(small, size, size, false);
        patternPaint.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(backgroundColor);
        if (mode == MODE_IMAGE) {
            super.onDraw(canvas);
            return;
        }
        if (mode == MODE_PATTERN && patternPaint.getShader() != null) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), patternPaint);
        }
    }

    // ------------------------------------------------------------------ Toque longo rigoroso

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureListener == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelPendingLongPress();
                if ((event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                    // Mouse: botao direito abre o menu na hora, como em um PC.
                    tracking = false;
                    gestureListener.onWallpaperLongPress(event.getX(), event.getY());
                    return true;
                }
                downX = event.getX();
                downY = event.getY();
                downTime = SystemClock.uptimeMillis();
                tracking = true;
                moved = false;
                longPressFired = false;
                handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY_MS);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!tracking) return true;
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (dx * dx + dy * dy > (float) moveTolerancePx * moveTolerancePx) {
                    // Arrasto detectado: nunca dispara o menu neste gesto.
                    moved = true;
                    cancelPendingLongPress();
                }
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                // Segundo dedo (pinça / esbarrao): descarta o gesto inteiro.
                tracking = false;
                cancelPendingLongPress();
                return true;

            case MotionEvent.ACTION_UP:
                cancelPendingLongPress();
                if (tracking && !moved && !longPressFired) {
                    long held = SystemClock.uptimeMillis() - downTime;
                    if (held < LONG_PRESS_DELAY_MS) {
                        gestureListener.onWallpaperTap(event.getX(), event.getY());
                    }
                }
                tracking = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                cancelPendingLongPress();
                tracking = false;
                return true;

            default:
                return true;
        }
    }

    private void onLongPressTimeout() {
        if (!tracking || moved || longPressFired || gestureListener == null) return;
        long held = SystemClock.uptimeMillis() - downTime;
        if (held < LONG_PRESS_DELAY_MS) {
            // Garantia extra: so dispara apos 1000 ms reais de pressao continua.
            handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY_MS - held);
            return;
        }
        longPressFired = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        gestureListener.onWallpaperLongPress(downX, downY);
    }

    private void cancelPendingLongPress() {
        handler.removeCallbacks(longPressRunnable);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) {
            cancelPendingLongPress();
            tracking = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelPendingLongPress();
        tracking = false;
        super.onDetachedFromWindow();
    }
}
