package com.example.desktopenv;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Gerenciador de janelas: ordem Z, foco, minimizar/restaurar, barra de tarefas e
 * caixas de dialogo customizadas (sem nenhum AlertDialog nativo do Android).
 */
public class DesktopCoreEngine implements DesktopWindow.WindowListener, TaskbarView.TaskClickListener {

    public interface WindowFactory {
        DesktopWindow createWindow(Context context);
    }

    public interface DialogCallback {
        /** index do botao pressionado, ou -1 se a janela foi fechada no "X". */
        void onButton(int index);
    }

    public interface InputCallback {
        void onInput(String value);
    }

    private final Context context;
    private final FrameLayout windowLayer;
    private final FrameLayout overlayLayer;
    private TaskbarView taskbar;

    /** Ordem Z (ultimo = topo). */
    private final List<DesktopWindow> windows = new ArrayList<DesktopWindow>();
    /** Ordem de criacao (botoes da barra de tarefas). */
    private final List<DesktopWindow> taskOrder = new ArrayList<DesktopWindow>();
    private final Map<String, DesktopWindow> singletons = new HashMap<String, DesktopWindow>();
    private DesktopWindow activeWindow;
    private int cascadeIndex;

    public DesktopCoreEngine(Context context, FrameLayout windowLayer, FrameLayout overlayLayer) {
        this.context = context;
        this.windowLayer = windowLayer;
        this.overlayLayer = overlayLayer;
    }

    public Context getContext() {
        return context;
    }

    public FrameLayout getOverlayLayer() {
        return overlayLayer;
    }

    public FrameLayout getWindowLayer() {
        return windowLayer;
    }

    public void setTaskbar(TaskbarView bar) {
        this.taskbar = bar;
        bar.setTaskClickListener(this);
        refreshTaskbar();
    }

    public List<DesktopWindow> getWindows() {
        return new ArrayList<DesktopWindow>(windows);
    }

    public DesktopWindow getActiveWindow() {
        return activeWindow;
    }

    // ------------------------------------------------------------------ Janelas

    public void addWindow(DesktopWindow window) {
        addWindow(window, 380, 280);
    }

    public void addWindow(DesktopWindow window, int widthDp, int heightDp) {
        int lw = windowLayer.getWidth();
        int lh = windowLayer.getHeight();
        int ww = dp(widthDp);
        int wh = dp(heightDp);
        if (lw > 0) ww = Math.min(ww, lw - dp(8));
        if (lh > 0) wh = Math.min(wh, lh - dp(8));

        int offset = dp(12) + (cascadeIndex % 6) * dp(22);
        cascadeIndex++;
        int left = offset;
        int top = offset;
        if (lw > 0 && left + ww > lw) left = Math.max(0, (lw - ww) / 2);
        if (lh > 0 && top + wh > lh) top = Math.max(0, (lh - wh) / 2);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ww, wh);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = left;
        lp.topMargin = top;
        attach(window, lp);
    }

    /** Janela de dialogo: largura fixa, altura pelo conteudo, centralizada. */
    public void addDialogWindow(DesktopWindow window, int widthDp) {
        int lw = windowLayer.getWidth();
        int lh = windowLayer.getHeight();
        int ww = dp(widthDp);
        if (lw > 0) ww = Math.min(ww, lw - dp(8));
        window.measure(View.MeasureSpec.makeMeasureSpec(ww, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(lh > 0 ? lh : dp(2000), View.MeasureSpec.AT_MOST));
        int wh = window.getMeasuredHeight();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ww, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.leftMargin = lw > 0 ? Math.max(0, (lw - ww) / 2) : dp(20);
        lp.topMargin = lh > 0 ? Math.max(0, (lh - wh) / 3) : dp(40);
        attach(window, lp);
    }

    private void attach(DesktopWindow window, FrameLayout.LayoutParams lp) {
        ContextMenuView.dismissCurrent();
        window.setWindowListener(this);
        windows.add(window);
        taskOrder.add(window);
        windowLayer.addView(window, lp);
        focusWindow(window);
    }

    /** Abre (ou traz para frente) uma janela de instancia unica. */
    public DesktopWindow openSingleton(String key, WindowFactory factory, int widthDp, int heightDp) {
        DesktopWindow existing = singletons.get(key);
        if (existing != null && windows.contains(existing)) {
            focusWindow(existing);
            return existing;
        }
        DesktopWindow w = factory.createWindow(context);
        singletons.put(key, w);
        addWindow(w, widthDp, heightDp);
        return w;
    }

    /** Remove a janela imediatamente (sem consultar o CloseHandler). */
    public void closeWindow(DesktopWindow window) {
        if (window == null) return;
        PixelUi.hideKeyboard(windowLayer);
        windowLayer.removeView(window);
        windows.remove(window);
        taskOrder.remove(window);
        Iterator<Map.Entry<String, DesktopWindow>> it = singletons.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() == window) it.remove();
        }
        if (activeWindow == window) {
            activeWindow = null;
            activateTopmost();
        } else {
            refreshTaskbar();
        }
    }

    /** Usado pelo botao Voltar do aparelho: fecha a janela ativa. */
    public boolean closeActiveWindow() {
        if (activeWindow != null && !activeWindow.isMinimized()) {
            activeWindow.requestClose();
            return true;
        }
        return false;
    }

    public void focusWindow(DesktopWindow window) {
        if (window == null) return;
        boolean onTop = !windows.isEmpty() && windows.get(windows.size() - 1) == window;
        if (window == activeWindow && onTop && !window.isMinimized()) return;
        if (window.isMinimized()) window.setMinimized(false);
        window.bringToFront();
        windowLayer.invalidate();
        windows.remove(window);
        windows.add(window);
        for (int i = 0; i < windows.size(); i++) {
            DesktopWindow w = windows.get(i);
            w.setActive(w == window);
        }
        activeWindow = window;
        refreshTaskbar();
    }

    public void minimizeWindow(DesktopWindow window) {
        window.setMinimized(true);
        window.setActive(false);
        if (activeWindow == window) {
            activeWindow = null;
            activateTopmost();
        } else {
            refreshTaskbar();
        }
    }

    private void activateTopmost() {
        for (int i = windows.size() - 1; i >= 0; i--) {
            DesktopWindow w = windows.get(i);
            if (!w.isMinimized()) {
                focusWindow(w);
                return;
            }
        }
        for (int i = 0; i < windows.size(); i++) {
            windows.get(i).setActive(false);
        }
        activeWindow = null;
        refreshTaskbar();
    }

    public void refreshTaskbar() {
        if (taskbar != null) {
            taskbar.setWindows(taskOrder, activeWindow);
        }
    }

    // ------------------------------------------------------------------ WindowListener

    @Override
    public void onClose(DesktopWindow window) {
        closeWindow(window);
    }

    @Override
    public void onFocus(DesktopWindow window) {
        ContextMenuView.dismissCurrent();
        focusWindow(window);
    }

    @Override
    public void onMinimize(DesktopWindow window) {
        minimizeWindow(window);
    }

    // ------------------------------------------------------------------ TaskClickListener

    @Override
    public void onTaskClicked(DesktopWindow window) {
        if (window == activeWindow && !window.isMinimized()) {
            minimizeWindow(window);
        } else {
            focusWindow(window);
        }
    }

    // ------------------------------------------------------------------ Dialogos customizados

    public DesktopWindow showMessageBox(String title, String message, int iconType) {
        return showDialog(title, message, iconType, new String[] { "OK" }, null);
    }

    public DesktopWindow showConfirm(String title, String message, final Runnable onYes) {
        return showDialog(title, message, PixelIconView.TYPE_QUESTION, new String[] { "Sim", "N\u00e3o" },
                new DialogCallback() {
                    @Override
                    public void onButton(int index) {
                        if (index == 0 && onYes != null) onYes.run();
                    }
                });
    }

    public DesktopWindow showDialog(String title, String message, int iconType, String[] buttons,
                                    final DialogCallback callback) {
        final DesktopWindow w = new DesktopWindow(context, title, PixelIconView.TYPE_APP);
        w.setResizable(false);
        w.setMinimizable(false);
        w.setMaximizable(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PixelUi.FACE);
        root.setPadding(dp(12), dp(12), dp(12), dp(10));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (iconType >= 0) {
            PixelIconView icon = new PixelIconView(context, iconType);
            row.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
        }
        TextView msg = PixelUi.multiLabel(context, message);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mlp.leftMargin = dp(12);
        mlp.topMargin = dp(4);
        row.addView(msg, mlp);
        root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_HORIZONTAL);
        for (int i = 0; i < buttons.length; i++) {
            final int index = i;
            TextView b = PixelUi.button(context, buttons[i], DesktopWindow.safeClick(new DesktopWindow.OnSafeClick() {
                @Override
                public void onClick(View v) {
                    closeWindow(w);
                    if (callback != null) callback.onButton(index);
                }
            }));
            b.setMinWidth(dp(76));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
            if (i > 0) blp.leftMargin = dp(8);
            buttonRow.addView(b, blp);
        }
        LinearLayout.LayoutParams brlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brlp.topMargin = dp(14);
        root.addView(buttonRow, brlp);

        w.setContent(root);
        w.setCloseHandler(new DesktopWindow.CloseHandler() {
            @Override
            public boolean onCloseRequested(DesktopWindow window) {
                if (callback != null) callback.onButton(-1);
                return true;
            }
        });
        addDialogWindow(w, 310);
        return w;
    }

    public DesktopWindow showInputBox(String title, String prompt, String defaultValue,
                                      final InputCallback callback) {
        final DesktopWindow w = new DesktopWindow(context, title, PixelIconView.TYPE_APP);
        w.setResizable(false);
        w.setMinimizable(false);
        w.setMaximizable(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PixelUi.FACE);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView label = PixelUi.multiLabel(context, prompt);
        root.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText field = PixelUi.textField(context, true);
        field.setText(defaultValue == null ? "" : defaultValue);
        field.setSelectAllOnFocus(true);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
        flp.topMargin = dp(8);
        root.addView(field, flp);

        final Runnable confirm = new Runnable() {
            @Override
            public void run() {
                String value = field.getText().toString();
                closeWindow(w);
                if (callback != null) callback.onInput(value);
            }
        };
        field.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_UP)) {
                    confirm.run();
                    return true;
                }
                return false;
            }
        });

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.RIGHT);
        TextView ok = PixelUi.button(context, "OK", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirm.run();
            }
        });
        ok.setMinWidth(dp(76));
        TextView cancel = PixelUi.button(context, "Cancelar", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeWindow(w);
            }
        });
        cancel.setMinWidth(dp(76));
        buttonRow.addView(ok, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
        clp.leftMargin = dp(8);
        buttonRow.addView(cancel, clp);
        LinearLayout.LayoutParams brlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brlp.topMargin = dp(12);
        root.addView(buttonRow, brlp);

        w.setContent(root);
        addDialogWindow(w, 300);
        field.requestFocus();
        field.postDelayed(new Runnable() {
            @Override
            public void run() {
                PixelUi.showKeyboard(field);
            }
        }, 150L);
        return w;
    }

    private int dp(float v) {
        return PixelUi.dp(context, v);
    }
}
