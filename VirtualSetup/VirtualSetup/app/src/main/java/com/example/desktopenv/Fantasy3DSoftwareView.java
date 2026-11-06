package com.example.desktopenv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;

/**
 * Renderizador 3D de compatibilidade baseado em Canvas.
 * Usado quando o aparelho nao anuncia suporte a OpenGL ES 2.0.
 * Nao cria contexto EGL nem thread grafica separada.
 */
public final class Fantasy3DSoftwareView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float yaw = 0.45f;
    private float pitch = 0.22f;
    private float lastX;
    private float lastY;
    private long startTime = System.currentTimeMillis();

    public Fantasy3DSoftwareView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(true);
    }

    public void resetCamera() {
        yaw = 0.45f;
        pitch = 0.22f;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        canvas.drawColor(Color.rgb(7, 13, 25));
        drawFloor(canvas, w, h);
        float t = (System.currentTimeMillis() - startTime) / 1000.0f;
        drawBox(canvas, w, h, 0, 1.55f, 0, 1.25f, 1.45f, 0.72f,
                Color.rgb(53, 103, 137), t * 0.25f);
        drawBox(canvas, w, h, 0, 2.72f, 0, 0.82f, 0.72f, 0.68f,
                Color.rgb(76, 126, 157), -t * 0.12f);
        drawBox(canvas, w, h, -0.22f, 2.78f, 0.36f, 0.13f, 0.09f, 0.04f,
                Color.rgb(30, 220, 255), 0);
        drawBox(canvas, w, h, 0.22f, 2.78f, 0.36f, 0.13f, 0.09f, 0.04f,
                Color.rgb(30, 220, 255), 0);
        drawBox(canvas, w, h, -0.88f, 1.55f, 0, 0.34f, 1.18f, 0.40f,
                Color.rgb(65, 91, 116), (float)Math.sin(t * 2.0f) * 0.12f);
        drawBox(canvas, w, h, 0.88f, 1.55f, 0, 0.34f, 1.18f, 0.40f,
                Color.rgb(65, 91, 116), -(float)Math.sin(t * 2.0f) * 0.12f);
        drawBox(canvas, w, h, -0.36f, 0.52f, 0, 0.42f, 0.92f, 0.46f,
                Color.rgb(38, 68, 91), 0);
        drawBox(canvas, w, h, 0.36f, 0.52f, 0, 0.42f, 0.92f, 0.46f,
                Color.rgb(38, 68, 91), 0);
        drawBox(canvas, w, h, -2.1f, 1.0f, 0.2f, 0.42f, 0.42f, 0.42f,
                Color.rgb(28, 164, 203), t * 0.5f);
        postInvalidateDelayed(33L);
    }

    private void drawFloor(Canvas c, int w, int h) {
        paint.setColor(Color.rgb(16, 29, 47));
        Path floor = new Path();
        float horizon = h * 0.48f;
        floor.moveTo(0, horizon);
        floor.lineTo(w, horizon);
        floor.lineTo(w, h);
        floor.lineTo(0, h);
        floor.close();
        c.drawPath(floor, paint);
        paint.setColor(Color.rgb(31, 72, 99));
        paint.setStrokeWidth(1.0f);
        for (int i = -6; i <= 6; i++) {
            float x = w * 0.5f + i * w * 0.075f;
            c.drawLine(w * 0.5f, horizon, x, h, paint);
        }
        for (int i = 1; i <= 8; i++) {
            float y = horizon + (h - horizon) * i / 9.0f;
            c.drawLine(0, y, w, y, paint);
        }
    }

    private void drawBox(Canvas c, int w, int h, float x, float y, float z,
                         float sx, float sy, float sz, int color, float rotation) {
        float[][] p = new float[8][2];
        int k = 0;
        for (int iy = -1; iy <= 1; iy += 2) {
            for (int iz = -1; iz <= 1; iz += 2) {
                for (int ix = -1; ix <= 1; ix += 2) {
                    float px = ix * sx;
                    float pz = iz * sz;
                    float rx = (float)(px * Math.cos(rotation) - pz * Math.sin(rotation));
                    float rz = (float)(px * Math.sin(rotation) + pz * Math.cos(rotation));
                    p[k][0] = projectX(rx + x, iy * sy + y, rz + z, w, h);
                    p[k][1] = projectY(rx + x, iy * sy + y, rz + z, w, h);
                    k++;
                }
            }
        }
        // Face indices follow the vertex order generated above.
        drawFace(c, p, new int[] {0,1,3,2}, shade(color, 0.62f));
        drawFace(c, p, new int[] {4,5,7,6}, shade(color, 0.82f));
        drawFace(c, p, new int[] {0,1,5,4}, shade(color, 0.72f));
        drawFace(c, p, new int[] {2,3,7,6}, shade(color, 1.12f));
        drawFace(c, p, new int[] {0,2,6,4}, shade(color, 0.92f));
        drawFace(c, p, new int[] {1,3,7,5}, shade(color, 1.22f));
    }

    private float projectX(float x, float y, float z, int w, int h) {
        float rx = (float)(x * Math.cos(yaw) - z * Math.sin(yaw));
        float rz = (float)(x * Math.sin(yaw) + z * Math.cos(yaw));
        float depth = Math.max(2.0f, 10.0f + rz - y * (float)Math.sin(pitch));
        return w * 0.5f + rx * h * 0.48f / depth;
    }

    private float projectY(float x, float y, float z, int w, int h) {
        float rx = (float)(x * Math.cos(yaw) - z * Math.sin(yaw));
        float rz = (float)(x * Math.sin(yaw) + z * Math.cos(yaw));
        float vertical = y * (float)Math.cos(pitch) - rz * (float)Math.sin(pitch);
        float depth = Math.max(2.0f, 10.0f + rz - y * (float)Math.sin(pitch));
        return h * 0.57f - vertical * h * 0.48f / depth;
    }

    private void drawFace(Canvas c, float[][] p, int[] ids, int color) {
        Path path = new Path();
        path.moveTo(p[ids[0]][0], p[ids[0]][1]);
        for (int i = 1; i < ids.length; i++) path.lineTo(p[ids[i]][0], p[ids[i]][1]);
        path.close();
        paint.setColor(color);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f);
        paint.setColor(Color.argb(95, 160, 220, 245));
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private int shade(int color, float factor) {
        int r = Math.min(255, (int)(Color.red(color) * factor));
        int g = Math.min(255, (int)(Color.green(color) * factor));
        int b = Math.min(255, (int)(Color.blue(color) * factor));
        return Color.rgb(r, g, b);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            yaw += (event.getX() - lastX) * 0.008f;
            pitch += (event.getY() - lastY) * 0.004f;
            if (pitch < -0.35f) pitch = -0.35f;
            if (pitch > 0.8f) pitch = 0.8f;
            lastX = event.getX();
            lastY = event.getY();
            invalidate();
            return true;
        }
        return event.getAction() == MotionEvent.ACTION_UP;
    }
}
