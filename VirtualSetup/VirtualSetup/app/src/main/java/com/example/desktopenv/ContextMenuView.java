package com.example.desktopenv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu de contexto flutuante 100% customizado (substitui qualquer dialogo/PopupMenu nativo).
 * Borda preta solida de 1 linha, fundo cinza plano, itens de texto simples com
 * indicador de foco (faixa azul-marinho) ao passar o dedo ou o cursor do mouse.
 */
public class ContextMenuView extends LinearLayout {

    public interface OnItemSelectedListener {
        void onItemSelected(int id);
    }

    public interface OnDismissListener {
        void onDismiss(ContextMenuView menu);
    }

    private static final int MARK_NONE = 0;
    private static final int MARK_CHECK = 1;
    private static final int MARK_RADIO = 2;

    private static class Item {
        int id;
        String label;
        boolean enabled = true;
        boolean separator;
        boolean bold;
        int mark = MARK_NONE;
    }

    private static ContextMenuView sCurrent;

    private final List<Item> items = new ArrayList<Item>();
    private final List<View> rows = new ArrayList<View>();
    private final List<TextView[]> rowTexts = new ArrayList<TextView[]>();
    private final LinearLayout column;

    private String banner;
    private OnItemSelectedListener itemListener;
    private OnDismissListener dismissListener;
    private FrameLayout overlay;
    private FrameLayout catcher;
    private int highlighted = -1;
    private boolean showing;

    public ContextMenuView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setBackgroundResource(R.drawable.win_menu);
        int p = PixelUi.dp(context, 2);
        setPadding(p, p, p, p);
        PixelUi.flatten(this);
        column = new LinearLayout(context);
        column.setOrientation(VERTICAL);
        addView(column, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    // ------------------------------------------------------------------ API de construcao

    public ContextMenuView addItem(int id, String label) {
        return addItem(id, label, true);
    }

    public ContextMenuView addItem(int id, String label, boolean enabled) {
        Item it = new Item();
        it.id = id;
        it.label = label;
        it.enabled = enabled;
        items.add(it);
        return this;
    }

    /** Item padrao (negrito), como "Abrir" no Explorer. */
    public ContextMenuView addDefaultItem(int id, String label) {
        addItem(id, label, true);
        items.get(items.size() - 1).bold = true;
        return this;
    }

    public ContextMenuView addCheckItem(int id, String label, boolean checked) {
        addItem(id, label, true);
        items.get(items.size() - 1).mark = checked ? MARK_CHECK : MARK_NONE;
        return this;
    }

    public ContextMenuView addRadioItem(int id, String label, boolean selected) {
        addItem(id, label, true);
        items.get(items.size() - 1).mark = selected ? MARK_RADIO : MARK_NONE;
        return this;
    }

    public ContextMenuView addSeparator() {
        Item it = new Item();
        it.separator = true;
        it.enabled = false;
        items.add(it);
        return this;
    }

    /** Faixa vertical lateral (estilo menu Iniciar). */
    public void setBanner(String text) {
        this.banner = text;
    }

    public void setOnItemSelectedListener(OnItemSelectedListener l) {
        this.itemListener = l;
    }

    public void setOnDismissListener(OnDismissListener l) {
        this.dismissListener = l;
    }

    public boolean isShowing() {
        return showing;
    }

    // ------------------------------------------------------------------ Exibicao

    public void showAt(FrameLayout overlayLayer, int x, int y) {
        showInternal(overlayLayer, x, y, false);
    }

    /** Exibe o menu com a borda inferior em bottomY (ex.: menu Iniciar acima da barra). */
    public void showAbove(FrameLayout overlayLayer, int x, int bottomY) {
        showInternal(overlayLayer, x, bottomY, true);
    }

    private void showInternal(FrameLayout overlayLayer, int x, int y, boolean above) {
        if (sCurrent != null) {
            sCurrent.dismiss();
        }
        buildRows();
        overlay = overlayLayer;

        // Captura toques fora do menu: fecha o menu e consome o toque (como no PC).
        catcher = new FrameLayout(getContext());
        catcher.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dismiss();
                }
                return true;
            }
        });
        overlay.addView(catcher, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        int ow = overlay.getWidth();
        int oh = overlay.getHeight();
        if (ow <= 0) ow = getResources().getDisplayMetrics().widthPixels;
        if (oh <= 0) oh = getResources().getDisplayMetrics().heightPixels;
        measure(MeasureSpec.makeMeasureSpec(ow, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(oh, MeasureSpec.AT_MOST));
        int mw = getMeasuredWidth();
        int mh = getMeasuredHeight();

        int left = x;
        int top = above ? y - mh : y;
        if (left + mw > ow) left = ow - mw;
        if (left < 0) left = 0;
        if (top + mh > oh) top = Math.max(0, (above ? oh : y) - mh);
        if (top < 0) top = 0;

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = left;
        lp.topMargin = top;
        overlay.addView(this, lp);
        showing = true;
        sCurrent = this;
    }

    public void dismiss() {
        if (!showing) return;
        showing = false;
        if (overlay != null) {
            overlay.removeView(this);
            if (catcher != null) overlay.removeView(catcher);
        }
        catcher = null;
        if (sCurrent == this) sCurrent = null;
        setHighlighted(-1);
        if (dismissListener != null) {
            dismissListener.onDismiss(this);
        }
    }

    /** Fecha o menu aberto (se houver). Retorna true se algum menu foi fechado. */
    public static boolean dismissCurrent() {
        if (sCurrent != null) {
            sCurrent.dismiss();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Construcao visual

    private void buildRows() {
        Context c = getContext();
        column.removeAllViews();
        rows.clear();
        rowTexts.clear();
        if (getChildCount() > 1) {
            removeViewAt(0);
        }
        if (banner != null) {
            BannerView bv = new BannerView(c, banner);
            addView(bv, 0, new LayoutParams(PixelUi.dp(c, 24), LayoutParams.MATCH_PARENT));
        }

        int rowHeight = PixelUi.dp(c, 30);
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.separator) {
                FrameLayout sep = new FrameLayout(c);
                View line = PixelUi.etched(c, true);
                FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, PixelUi.dp(c, 2), Gravity.CENTER_VERTICAL);
                llp.leftMargin = PixelUi.dp(c, 2);
                llp.rightMargin = PixelUi.dp(c, 2);
                sep.addView(line, llp);
                column.addView(sep, new LayoutParams(LayoutParams.MATCH_PARENT, PixelUi.dp(c, 9)));
                rows.add(sep);
                rowTexts.add(null);
                continue;
            }
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumWidth(PixelUi.dp(c, banner != null ? 170 : 150));

            TextView mark = PixelUi.label(c, markText(it.mark));
            mark.setGravity(Gravity.CENTER);
            row.addView(mark, new LayoutParams(PixelUi.dp(c, 22), LayoutParams.MATCH_PARENT));

            TextView text = PixelUi.label(c, it.label);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            text.setTypeface(it.bold ? PixelUi.FONT_BOLD : PixelUi.FONT);
            text.setPadding(0, 0, PixelUi.dp(c, 24), 0);
            row.addView(text, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

            column.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, rowHeight));
            rows.add(row);
            rowTexts.add(new TextView[] { mark, text });
            applyRowState(i, false);
        }
    }

    private static String markText(int mark) {
        if (mark == MARK_CHECK) return "\u2713";
        if (mark == MARK_RADIO) return "\u2022";
        return "";
    }

    private void applyRowState(int index, boolean focused) {
        if (index < 0 || index >= rows.size()) return;
        Item it = items.get(index);
        if (it.separator) return;
        View row = rows.get(index);
        TextView[] texts = rowTexts.get(index);
        row.setBackgroundColor(focused ? PixelUi.SELECTION : PixelUi.TRANSPARENT);
        for (int i = 0; i < texts.length; i++) {
            TextView t = texts[i];
            if (focused) {
                t.setTextColor(it.enabled ? PixelUi.SELECTION_TEXT : PixelUi.TEXT_DISABLED);
                t.setShadowLayer(0f, 0f, 0f, 0);
            } else if (it.enabled) {
                t.setTextColor(PixelUi.TEXT);
                t.setShadowLayer(0f, 0f, 0f, 0);
            } else {
                t.setTextColor(PixelUi.TEXT_DISABLED);
                t.setShadowLayer(0.01f, 1f, 1f, PixelUi.HIGHLIGHT);
            }
        }
    }

    private void setHighlighted(int index) {
        if (index >= 0 && index < items.size() && items.get(index).separator) {
            index = -1;
        }
        if (index == highlighted) return;
        int old = highlighted;
        highlighted = index;
        applyRowState(old, false);
        applyRowState(highlighted, true);
    }

    private int indexAt(float x, float y) {
        float cx = x - column.getLeft();
        float cy = y - column.getTop();
        if (cx < 0 || cx >= column.getWidth()) return -1;
        for (int i = 0; i < rows.size(); i++) {
            View r = rows.get(i);
            if (cy >= r.getTop() && cy < r.getBottom()) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ Toque / cursor

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            setHighlighted(indexAt(event.getX(), event.getY()));
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            int index = indexAt(event.getX(), event.getY());
            setHighlighted(index);
            if (index >= 0) {
                Item it = items.get(index);
                if (!it.separator && it.enabled) {
                    final int id = it.id;
                    final OnItemSelectedListener l = itemListener;
                    dismiss();
                    if (l != null) {
                        l.onItemSelected(id);
                    }
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            setHighlighted(-1);
        }
        return true;
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
            setHighlighted(indexAt(event.getX(), event.getY()));
        } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
            setHighlighted(-1);
        }
        return true;
    }

    // ------------------------------------------------------------------ Faixa lateral

    private static class BannerView extends View {
        private final String text;
        private final Paint paint = new Paint();

        BannerView(Context c, String text) {
            super(c);
            this.text = text;
            paint.setAntiAlias(true);
            paint.setColor(0xFFFFFFFF);
            paint.setTypeface(PixelUi.FONT_BOLD);
            paint.setTextSize(PixelUi.dp(c, 14));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(PixelUi.SHADOW);
            canvas.save();
            canvas.rotate(-90f);
            float pad = PixelUi.dp(getContext(), 8);
            float baseline = getWidth() - PixelUi.dp(getContext(), 6);
            canvas.drawText(text, -getHeight() + pad, baseline, paint);
            canvas.restore();
        }
    }
}
