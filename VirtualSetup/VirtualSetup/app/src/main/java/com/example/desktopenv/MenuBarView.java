package com.example.desktopenv;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Barra de menu tradicional (Arquivo, Editar, Exibir, Ajuda).
 * Abre o menu suspenso no ACTION_DOWN, como em um PC.
 */
public class MenuBarView extends LinearLayout {

    public interface MenuProvider {
        void onBuildMenu(ContextMenuView menu);
    }

    private final DesktopCoreEngine engine;

    public MenuBarView(Context context, DesktopCoreEngine engine) {
        super(context);
        this.engine = engine;
        setOrientation(HORIZONTAL);
        setBackgroundColor(PixelUi.FACE);
        int p = PixelUi.dp(context, 1);
        setPadding(p * 2, p, p * 2, p);
        PixelUi.flatten(this);
    }

    public void addMenu(String title, final MenuProvider provider) {
        final TextView label = PixelUi.label(getContext(), title);
        int h = PixelUi.dp(getContext(), 8);
        int v = PixelUi.dp(getContext(), 5);
        label.setPadding(h, v, h, v);
        label.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    openMenu(label, provider);
                }
                return true;
            }
        });
        addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    private void openMenu(final TextView label, MenuProvider provider) {
        ContextMenuView menu = new ContextMenuView(getContext());
        provider.onBuildMenu(menu);
        label.setBackgroundColor(PixelUi.SELECTION);
        label.setTextColor(PixelUi.SELECTION_TEXT);
        menu.setOnDismissListener(new ContextMenuView.OnDismissListener() {
            @Override
            public void onDismiss(ContextMenuView m) {
                label.setBackgroundColor(PixelUi.TRANSPARENT);
                label.setTextColor(PixelUi.TEXT);
            }
        });
        FrameLayout overlay = engine.getOverlayLayer();
        int[] pos = PixelUi.positionIn(label, overlay);
        menu.showAt(overlay, pos[0], pos[1] + label.getHeight());
    }
}
