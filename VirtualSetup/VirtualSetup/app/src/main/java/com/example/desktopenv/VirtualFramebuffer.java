package com.example.desktopenv;

/** Framebuffer ARGB com operacoes limitadas aos pixels validos. */
public class VirtualFramebuffer {
    private final int width;
    private final int height;
    private final int[] pixels;

    public VirtualFramebuffer(int w, int h) {
        if (w < 1 || h < 1 || (long) w * (long) h > 1048576L) {
            throw new IllegalArgumentException("Dimensoes invalidas do framebuffer.");
        }
        width = w;
        height = h;
        pixels = new int[w * h];
    }

    public void setPixel(int x, int y, int color) {
        if (x >= 0 && x < width && y >= 0 && y < height) pixels[y * width + x] = color;
    }

    public int getPixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0;
        return pixels[y * width + x];
    }

    public void fill(int color) {
        int i;
        for (i = 0; i < pixels.length; i++) pixels[i] = color;
    }

    public void fillRect(int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int left = Math.max(0, x);
        int top = Math.max(0, y);
        int right = (int) Math.min((long) width, (long) x + (long) w);
        int bottom = (int) Math.min((long) height, (long) y + (long) h);
        if (right <= left || bottom <= top) return;
        int yy;
        for (yy = top; yy < bottom; yy++) {
            int start = yy * width + left;
            int end = yy * width + right;
            int xx;
            for (xx = start; xx < end; xx++) pixels[xx] = color;
        }
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    /** Retorna o buffer vivo para renderizadores de alta performance. */
    public int[] getPixels() { return pixels; }
}
