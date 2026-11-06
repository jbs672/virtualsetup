package com.example.desktopenv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Icones desenhados em pixel art (grade 16x16) via Canvas.
 * Nenhum recurso de imagem externo e necessario.
 */
public class PixelIconView extends View {

    public static final int TYPE_APP = 0;
    public static final int TYPE_COMPUTER = 1;
    public static final int TYPE_FOLDER = 2;
    public static final int TYPE_DOCUMENT = 3;
    public static final int TYPE_NOTEPAD = 4;
    public static final int TYPE_FANTASY = 5;
    public static final int TYPE_DISPLAY = 6;
    public static final int TYPE_INFO = 7;
    public static final int TYPE_WARNING = 8;
    public static final int TYPE_QUESTION = 9;
    public static final int TYPE_ERROR = 10;
    public static final int TYPE_FILE = 11;
    public static final int TYPE_LOGO = 12;
    public static final int TYPE_MY_DOCUMENTS = 13;
    public static final int TYPE_PROGRAM = 14;

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFC0C0C0;
    private static final int DARK_GRAY = 0xFF808080;
    private static final int NAVY = 0xFF000080;
    private static final int TEAL = 0xFF008080;
    private static final int YELLOW = 0xFFFFE070;
    private static final int YELLOW_DARK = 0xFFC8A000;
    private static final int YELLOW_LIGHT = 0xFFFFF4B0;

    private int type;
    private final Paint paint = new Paint();
    private final Path path = new Path();

    public PixelIconView(Context context, int type) {
        super(context);
        this.type = type;
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.FILL);
        PixelUi.flatten(this);
    }

    public void setType(int type) {
        this.type = type;
        invalidate();
    }

    public int getType() {
        return type;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float u = Math.min(getWidth(), getHeight()) / 16f;
        if (u <= 0f) return;
        float ox = (getWidth() - 16f * u) / 2f;
        float oy = (getHeight() - 16f * u) / 2f;
        canvas.save();
        canvas.translate(ox, oy);
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.FILL);

        switch (type) {
            case TYPE_COMPUTER:
                drawMonitor(canvas, u, TEAL);
                break;
            case TYPE_FANTASY:
                drawMonitor(canvas, u, BLACK);
                rect(canvas, u, 4, 4, 6, 6, 0xFFFF4040);
                rect(canvas, u, 7, 5, 9, 7, 0xFF40FF40);
                rect(canvas, u, 10, 4, 12, 6, 0xFF4080FF);
                rect(canvas, u, 5, 7, 11, 8, 0xFFFFFF40);
                break;
            case TYPE_DISPLAY:
                drawMonitor(canvas, u, 0xFF3A6EA5);
                rect(canvas, u, 4, 6, 8, 8, 0xFF00A000);
                rect(canvas, u, 8, 4, 12, 8, 0xFF80C0FF);
                break;
            case TYPE_FOLDER:
                drawFolder(canvas, u);
                break;
            case TYPE_MY_DOCUMENTS:
                drawFolder(canvas, u);
                box(canvas, u, 6, 2, 12, 9, WHITE);
                rect(canvas, u, 7, 4, 11, 5, DARK_GRAY);
                rect(canvas, u, 7, 6, 11, 7, DARK_GRAY);
                box(canvas, u, 1, 7, 15, 14, YELLOW);
                break;
            case TYPE_DOCUMENT:
                drawPage(canvas, u, WHITE);
                rect(canvas, u, 5, 4, 11, 5, DARK_GRAY);
                rect(canvas, u, 5, 6, 11, 7, DARK_GRAY);
                rect(canvas, u, 5, 8, 11, 9, DARK_GRAY);
                rect(canvas, u, 5, 10, 9, 11, DARK_GRAY);
                break;
            case TYPE_NOTEPAD:
                drawPage(canvas, u, WHITE);
                rect(canvas, u, 4, 2, 12, 4, NAVY);
                rect(canvas, u, 5, 6, 11, 7, DARK_GRAY);
                rect(canvas, u, 5, 8, 11, 9, DARK_GRAY);
                rect(canvas, u, 5, 10, 11, 11, DARK_GRAY);
                rect(canvas, u, 5, 12, 8, 13, DARK_GRAY);
                break;
            case TYPE_FILE:
                drawPage(canvas, u, WHITE);
                rect(canvas, u, 5, 5, 11, 11, GRAY);
                break;
            case TYPE_PROGRAM:
                box(canvas, u, 1, 2, 15, 14, WHITE);
                rect(canvas, u, 2, 3, 14, 5, NAVY);
                rect(canvas, u, 4, 7, 7, 10, 0xFF00A000);
                rect(canvas, u, 9, 7, 12, 10, 0xFFC00000);
                break;
            case TYPE_LOGO:
                rect(canvas, u, 1, 2, 7, 7, 0xFFFF0000);
                rect(canvas, u, 8, 2, 14, 7, 0xFF00A000);
                rect(canvas, u, 1, 8, 7, 13, 0xFF0000FF);
                rect(canvas, u, 8, 8, 14, 13, 0xFFFFD800);
                break;
            case TYPE_INFO:
                circle(canvas, u, WHITE);
                rect(canvas, u, 7, 3, 9, 5, NAVY);
                rect(canvas, u, 7, 6, 9, 12, NAVY);
                rect(canvas, u, 6, 11, 10, 12, NAVY);
                break;
            case TYPE_QUESTION:
                circle(canvas, u, WHITE);
                paint.setAntiAlias(true);
                paint.setColor(NAVY);
                paint.setTypeface(PixelUi.FONT_BOLD);
                paint.setTextSize(11f * u);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("?", 8f * u, 12f * u, paint);
                paint.setAntiAlias(false);
                break;
            case TYPE_ERROR:
                circle(canvas, u, 0xFFE00000);
                paint.setColor(WHITE);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f * u);
                canvas.drawLine(5f * u, 5f * u, 11f * u, 11f * u, paint);
                canvas.drawLine(11f * u, 5f * u, 5f * u, 11f * u, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            case TYPE_WARNING:
                paint.setColor(BLACK);
                path.reset();
                path.moveTo(8f * u, 0.5f * u);
                path.lineTo(15.5f * u, 15f * u);
                path.lineTo(0.5f * u, 15f * u);
                path.close();
                canvas.drawPath(path, paint);
                paint.setColor(0xFFFFE000);
                path.reset();
                path.moveTo(8f * u, 2.5f * u);
                path.lineTo(14f * u, 14f * u);
                path.lineTo(2f * u, 14f * u);
                path.close();
                canvas.drawPath(path, paint);
                rect(canvas, u, 7, 5, 9, 10, BLACK);
                rect(canvas, u, 7, 11, 9, 13, BLACK);
                break;
            case TYPE_APP:
            default:
                box(canvas, u, 1, 2, 15, 14, WHITE);
                rect(canvas, u, 2, 3, 14, 5, NAVY);
                break;
        }
        canvas.restore();
    }

    private void drawMonitor(Canvas c, float u, int screen) {
        box(c, u, 1, 1, 15, 11, GRAY);
        box(c, u, 3, 3, 13, 9, screen);
        box(c, u, 5, 10, 11, 13, GRAY);
        box(c, u, 2, 12, 14, 15, GRAY);
    }

    private void drawFolder(Canvas c, float u) {
        box(c, u, 1, 3, 8, 6, YELLOW_DARK);
        box(c, u, 1, 5, 15, 14, YELLOW);
        rect(c, u, 2, 6, 14, 7, YELLOW_LIGHT);
    }

    private void drawPage(Canvas c, float u, int fill) {
        box(c, u, 3, 1, 13, 15, fill);
    }

    private void circle(Canvas c, float u, int fill) {
        paint.setAntiAlias(true);
        paint.setColor(BLACK);
        c.drawCircle(8f * u, 8f * u, 7.5f * u, paint);
        paint.setColor(fill);
        c.drawCircle(8f * u, 8f * u, 6.5f * u, paint);
        paint.setAntiAlias(false);
    }

    /** Retangulo com contorno preto de 1 unidade. */
    private void box(Canvas c, float u, int l, int t, int r, int b, int fill) {
        paint.setColor(BLACK);
        c.drawRect(l * u, t * u, r * u, b * u, paint);
        paint.setColor(fill);
        c.drawRect((l + 1) * u, (t + 1) * u, (r - 1) * u, (b - 1) * u, paint);
    }

    private void rect(Canvas c, float u, int l, int t, int r, int b, int color) {
        paint.setColor(color);
        c.drawRect(l * u, t * u, r * u, b * u, paint);
    }
}
