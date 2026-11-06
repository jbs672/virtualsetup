package com.example.desktopenv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/** Renderiza o framebuffer do Fantasy PC com escala inteira (pixels nitidos, sem filtro). */
public class FantasyMonitorView extends View {

    private VirtualFramebuffer framebuffer;
    private Bitmap bitmap;
    private final Paint paint = new Paint();
    private final Rect src = new Rect();
    private final Rect dst = new Rect();

    public FantasyMonitorView(Context context) {
        super(context);
        init();
    }

    public FantasyMonitorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setFilterBitmap(false);
        paint.setAntiAlias(false);
        PixelUi.flatten(this);
    }

    public void setFramebuffer(VirtualFramebuffer fb) {
        this.framebuffer = fb;
        if (fb != null) {
            bitmap = Bitmap.createBitmap(fb.getWidth(), fb.getHeight(), Bitmap.Config.ARGB_8888);
            src.set(0, 0, fb.getWidth(), fb.getHeight());
        } else {
            bitmap = null;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Render framebuffer to screen
        canvas.drawColor(0xFF000000);
        if (framebuffer == null || bitmap == null) return;
        int fw = framebuffer.getWidth();
        int fh = framebuffer.getHeight();
        bitmap.setPixels(framebuffer.getPixels(), 0, fw, 0, 0, fw, fh);

        int vw = getWidth();
        int vh = getHeight();
        int scale = Math.min(vw / fw, vh / fh);
        int dw, dh;
        if (scale >= 1) {
            dw = fw * scale;
            dh = fh * scale;
        } else {
            float f = Math.min(vw / (float) fw, vh / (float) fh);
            dw = (int) (fw * f);
            dh = (int) (fh * f);
        }
        int left = (vw - dw) / 2;
        int top = (vh - dh) / 2;
        dst.set(left, top, left + dw, top + dh);
        canvas.drawBitmap(bitmap, src, dst, paint);
    }
}
