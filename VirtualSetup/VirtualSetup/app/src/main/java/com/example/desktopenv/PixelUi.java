package com.example.desktopenv;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Utilitarios de UI no estilo PC classico (Windows 95/2000 / XFCE classico).
 * Cores planas, bordas chanfradas de 1 linha, sem ripple, sem elevacao.
 */
public final class PixelUi {

    public static final int FACE = 0xFFC0C0C0;
    public static final int LIGHT = 0xFFDFDFDF;
    public static final int HIGHLIGHT = 0xFFFFFFFF;
    public static final int SHADOW = 0xFF808080;
    public static final int DARK = 0xFF000000;
    public static final int TITLE_ACTIVE = 0xFF000080;
    public static final int TITLE_INACTIVE = 0xFF808080;
    public static final int TITLE_TEXT_ACTIVE = 0xFFFFFFFF;
    public static final int TITLE_TEXT_INACTIVE = 0xFFC0C0C0;
    public static final int SELECTION = 0xFF000080;
    public static final int SELECTION_TEXT = 0xFFFFFFFF;
    public static final int TEXT = 0xFF000000;
    public static final int TEXT_DISABLED = 0xFF808080;
    public static final int DESKTOP = 0xFF008080;
    public static final int WINDOW = 0xFFFFFFFF;
    public static final int TRANSPARENT = 0x00000000;

    public static final float TEXT_SP = 12f;

    public static final Typeface FONT = Typeface.create("sans-serif", Typeface.NORMAL);
    public static final Typeface FONT_BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    public static final Typeface FONT_MONO = Typeface.MONOSPACE;

    private PixelUi() {
    }

    public static int dp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                c.getResources().getDisplayMetrics()));
    }

    /** Remove elevacao, sombra e animacoes de estado do Material (API 21+). */
    public static void flatten(View v) {
        if (Build.VERSION.SDK_INT >= 21) {
            v.setElevation(0f);
            v.setTranslationZ(0f);
            v.setStateListAnimator(null);
        }
    }

    public static TextView label(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(TEXT);
        t.setTypeface(FONT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL);
        flatten(t);
        return t;
    }

    public static TextView multiLabel(Context c, String text) {
        TextView t = label(c, text);
        t.setSingleLine(false);
        t.setEllipsize(null);
        t.setMaxLines(30);
        t.setGravity(Gravity.START | Gravity.TOP);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    /** Botao classico: TextView com seletor relevo/baixo-relevo (sem ripple). */
    public static TextView button(Context c, String text, View.OnClickListener listener) {
        TextView b = label(c, text);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundResource(R.drawable.win_button);
        b.setPadding(dp(c, 10), dp(c, 3), dp(c, 10), dp(c, 3));
        b.setMinHeight(dp(c, 24));
        b.setClickable(true);
        b.setFocusable(false);
        if (listener != null) {
            b.setOnClickListener(listener);
        }
        return b;
    }

    /** Aparencia de item desabilitado no estilo Win95 (texto cinza "gravado"). */
    public static void setEnabledLook(TextView t, boolean enabled) {
        t.setEnabled(enabled);
        t.setClickable(enabled);
        if (enabled) {
            t.setTextColor(TEXT);
            t.setShadowLayer(0f, 0f, 0f, 0);
        } else {
            t.setTextColor(TEXT_DISABLED);
            t.setShadowLayer(0.01f, 1f, 1f, HIGHLIGHT);
        }
    }

    public static EditText textField(Context c, boolean singleLine) {
        EditText e = new EditText(c);
        e.setBackgroundResource(R.drawable.win_sunken);
        e.setTextColor(TEXT);
        e.setTypeface(FONT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        e.setHighlightColor(0x66000080);
        e.setPadding(dp(c, 5), dp(c, 3), dp(c, 5), dp(c, 3));
        e.setIncludeFontPadding(false);
        if (singleLine) {
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        } else {
            e.setSingleLine(false);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setGravity(Gravity.START | Gravity.TOP);
            e.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        }
        flatten(e);
        return e;
    }

    public static TextView statusField(Context c, String text) {
        TextView t = label(c, text);
        t.setBackgroundResource(R.drawable.win_status);
        t.setPadding(dp(c, 6), 0, dp(c, 6), 0);
        return t;
    }

    /** Linha gravada (separador) horizontal ou vertical, 2dp. */
    public static View etched(Context c, boolean horizontal) {
        View v = new View(c);
        v.setBackgroundResource(horizontal ? R.drawable.win_etched_h : R.drawable.win_etched_v);
        return v;
    }

    /** Posicao de uma view relativa a outra (ex.: camada de overlay). */
    public static int[] positionIn(View v, View reference) {
        int[] a = new int[2];
        int[] b = new int[2];
        v.getLocationInWindow(a);
        reference.getLocationInWindow(b);
        return new int[] { a[0] - b[0], a[1] - b[1] };
    }

    public static LinearLayout.LayoutParams lin(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lin(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static void hideKeyboard(View anyView) {
        if (anyView == null) return;
        InputMethodManager imm = (InputMethodManager) anyView.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(anyView.getWindowToken(), 0);
        }
    }

    public static void showKeyboard(View target) {
        if (target == null) return;
        InputMethodManager imm = (InputMethodManager) target.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
        }
    }
}
