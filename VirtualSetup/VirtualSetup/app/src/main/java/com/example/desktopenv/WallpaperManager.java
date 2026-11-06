package com.example.desktopenv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Gerencia os papeis de parede disponiveis (cores solidas e padroes 8x8 classicos)
 * e persiste a escolha em SharedPreferences.
 * Observacao: nao confundir com android.app.WallpaperManager (nao e usado).
 */
public class WallpaperManager {

    public static final int TYPE_SOLID = 0;
    public static final int TYPE_PATTERN = 1;
    public static final int TYPE_IMAGE = 2;

    public static class Preset {
        public final String name;
        public final int type;
        public final int background;
        public final int foreground;
        public final byte[] pattern;
        public final int resId;

        public Preset(String name, int type, int background, int foreground, byte[] pattern, int resId) {
            this.name = name;
            this.type = type;
            this.background = background;
            this.foreground = foreground;
            this.pattern = pattern;
            this.resId = resId;
        }
    }

    private static final String PREFS = "virtual_setup_desktop";
    private static final String KEY_INDEX = "wallpaper_index";

    private final List<Preset> presets = new ArrayList<Preset>();
    private final SharedPreferences prefs;
    private final int builtInCount;
    private int currentIndex;
    private int currentWallpaperResId;

    public WallpaperManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        buildPresets();
        builtInCount = presets.size();
        int saved = prefs.getInt(KEY_INDEX, 0);
        currentIndex = (saved >= 0 && saved < builtInCount) ? saved : 0;
    }

    private void buildPresets() {
        solid("(Nenhum) Verde-azulado", 0xFF008080);
        solid("Azul Windows 2000", 0xFF3A6EA5);
        solid("Azul-marinho", 0xFF000080);
        solid("Cinza ard\u00f3sia (XFCE)", 0xFF4A5A6A);
        solid("Cinza", 0xFF808080);
        solid("Preto", 0xFF000000);
        pattern("Pontilhado 50%", 0xFF006060, 0xFF008080,
                new byte[] { (byte) 0xAA, 0x55, (byte) 0xAA, 0x55, (byte) 0xAA, 0x55, (byte) 0xAA, 0x55 });
        pattern("Tijolos", 0xFFC0C0C0, 0xFF904020,
                new byte[] { (byte) 0xFF, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                        (byte) 0xFF, 0x08, 0x08, 0x08 });
        pattern("Diamantes", 0xFF000080, 0xFF3A6EA5,
                new byte[] { 0x10, 0x28, 0x44, (byte) 0x82, 0x44, 0x28, 0x10, 0x00 });
        pattern("Grade", 0xFF006666, 0xFF008080,
                new byte[] { (byte) 0xFF, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                        (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80 });
        pattern("Tecido", 0xFF000000, 0xFF008080,
                new byte[] { (byte) 0x88, 0x54, 0x22, 0x45, (byte) 0x88, 0x15, 0x22, 0x51 });
        pattern("Xadrez", 0xFF404040, 0xFF808080,
                new byte[] { (byte) 0xF0, (byte) 0xF0, (byte) 0xF0, (byte) 0xF0, 0x0F, 0x0F, 0x0F, 0x0F });
    }

    private void solid(String name, int color) {
        presets.add(new Preset(name, TYPE_SOLID, color, 0, null, 0));
    }

    private void pattern(String name, int fg, int bg, byte[] rows) {
        presets.add(new Preset(name, TYPE_PATTERN, bg, fg, rows, 0));
    }

    /** Compatibilidade com a versao anterior: define uma imagem (drawable) como papel de parede. */
    public void setWallpaper(int resId) {
        this.currentWallpaperResId = resId;
        presets.add(new Preset("Imagem personalizada", TYPE_IMAGE, PixelUi.DESKTOP, 0, null, resId));
        currentIndex = presets.size() - 1;
    }

    public int getCurrentWallpaper() {
        return currentWallpaperResId;
    }

    public List<Preset> getPresets() {
        return presets;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public Preset getCurrent() {
        return presets.get(currentIndex);
    }

    public void setCurrentIndex(int index) {
        if (index < 0 || index >= presets.size()) return;
        currentIndex = index;
        if (index < builtInCount) {
            prefs.edit().putInt(KEY_INDEX, index).apply();
        }
    }

    public void next() {
        setCurrentIndex((currentIndex + 1) % presets.size());
    }

    public void apply(WallpaperView view) {
        if (view != null) view.applyPreset(getCurrent());
    }
}
