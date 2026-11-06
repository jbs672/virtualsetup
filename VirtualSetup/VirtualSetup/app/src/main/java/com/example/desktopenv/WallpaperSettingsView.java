package com.example.desktopenv;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/**
 * Janela "Propriedades de Video" para personalizar o papel de parede:
 * previa em um monitor, lista de papeis de parede e botoes OK / Cancelar / Aplicar.
 */
public class WallpaperSettingsView extends LinearLayout {

    private final DesktopCoreEngine engine;
    private final WallpaperManager manager;
    private final WallpaperView desktopWallpaper;
    private final WallpaperView preview;
    private final PresetAdapter adapter;
    private DesktopWindow host;
    private int pendingIndex;

    public static DesktopWindow open(final DesktopCoreEngine engine, final WallpaperManager manager,
                                     final WallpaperView desktopWallpaper) {
        return engine.openSingleton("wallpaper_settings", new DesktopCoreEngine.WindowFactory() {
            @Override
            public DesktopWindow createWindow(Context context) {
                DesktopWindow w = new DesktopWindow(context, "Propriedades de V\u00eddeo", PixelIconView.TYPE_DISPLAY);
                WallpaperSettingsView v = new WallpaperSettingsView(context, engine, manager, desktopWallpaper);
                v.host = w;
                w.setContent(v);
                w.setMaximizable(false);
                return w;
            }
        }, 330, 420);
    }

    public WallpaperSettingsView(Context context, DesktopCoreEngine engine, WallpaperManager manager,
                                 WallpaperView desktopWallpaper) {
        super(context);
        this.engine = engine;
        this.manager = manager;
        this.desktopWallpaper = desktopWallpaper;
        this.pendingIndex = manager.getCurrentIndex();
        setOrientation(VERTICAL);
        setBackgroundColor(PixelUi.FACE);
        setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView tab = PixelUi.label(context, "Plano de fundo");
        tab.setTypeface(PixelUi.FONT_BOLD);
        addView(tab, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Monitor de previa
        FrameLayout monitorRow = new FrameLayout(context);
        LinearLayout monitor = new LinearLayout(context);
        monitor.setOrientation(VERTICAL);
        monitor.setBackgroundResource(R.drawable.win_raised);
        monitor.setPadding(dp(8), dp(8), dp(8), dp(14));
        FrameLayout screen = new FrameLayout(context);
        screen.setBackgroundResource(R.drawable.win_sunken);
        screen.setPadding(dp(2), dp(2), dp(2), dp(2));
        preview = new WallpaperView(context);
        preview.setPatternScale(Math.max(1, dp(1)));
        screen.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        monitor.addView(screen, new LayoutParams(dp(160), dp(104)));
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        monitorRow.addView(monitor, mlp);
        LayoutParams mrlp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mrlp.topMargin = dp(8);
        mrlp.bottomMargin = dp(8);
        addView(monitorRow, mrlp);

        addView(PixelUi.label(context, "Papel de parede:"), new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout listFrame = new FrameLayout(context);
        listFrame.setBackgroundResource(R.drawable.win_sunken);
        listFrame.setPadding(dp(2), dp(2), dp(2), dp(2));
        ListView list = new ListView(context);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new android.graphics.drawable.ColorDrawable(PixelUi.TRANSPARENT));
        list.setCacheColorHint(PixelUi.TRANSPARENT);
        list.setOverScrollMode(OVER_SCROLL_NEVER);
        list.setBackgroundColor(PixelUi.WINDOW);
        adapter = new PresetAdapter(manager.getPresets());
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                pendingIndex = position;
                adapter.notifyDataSetChanged();
                updatePreview();
            }
        });
        listFrame.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        LayoutParams llp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        llp.topMargin = dp(4);
        addView(listFrame, llp);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(HORIZONTAL);
        buttons.setGravity(Gravity.RIGHT);
        TextView ok = PixelUi.button(context, "OK", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                apply();
                close();
            }
        });
        TextView cancel = PixelUi.button(context, "Cancelar", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });
        TextView applyBtn = PixelUi.button(context, "Aplicar", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                apply();
            }
        });
        TextView[] all = { ok, cancel, applyBtn };
        for (int i = 0; i < all.length; i++) {
            all[i].setMinWidth(dp(72));
            LayoutParams blp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
            if (i > 0) blp.leftMargin = dp(6);
            buttons.addView(all[i], blp);
        }
        LayoutParams bl = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = dp(10);
        addView(buttons, bl);

        updatePreview();
        list.setSelection(pendingIndex);
    }

    private void updatePreview() {
        List<WallpaperManager.Preset> presets = manager.getPresets();
        if (pendingIndex >= 0 && pendingIndex < presets.size()) {
            preview.applyPreset(presets.get(pendingIndex));
        }
    }

    private void apply() {
        manager.setCurrentIndex(pendingIndex);
        manager.apply(desktopWallpaper);
    }

    private void close() {
        if (host != null) engine.closeWindow(host);
    }

    private int dp(float v) {
        return PixelUi.dp(getContext(), v);
    }

    private class PresetAdapter extends BaseAdapter {
        private final List<WallpaperManager.Preset> items;

        PresetAdapter(List<WallpaperManager.Preset> items) {
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView t;
            if (convertView instanceof TextView) {
                t = (TextView) convertView;
            } else {
                t = PixelUi.label(getContext(), "");
                t.setPadding(dp(6), 0, dp(6), 0);
                t.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));
            }
            t.setText(items.get(position).name);
            boolean sel = position == pendingIndex;
            t.setBackgroundColor(sel ? PixelUi.SELECTION : PixelUi.WINDOW);
            t.setTextColor(sel ? PixelUi.SELECTION_TEXT : PixelUi.TEXT);
            return t;
        }
    }
}
