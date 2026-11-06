package com.example.desktopenv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Barra de tarefas classica: botao Iniciar, botoes de tarefa (relevo / baixo-relevo
 * para a janela ativa) e relogio na bandeja. Sem ripple, sem elevacao.
 */
public class TaskbarView extends LinearLayout {

    public interface StartMenuListener {
        void onStartMenuClicked();
    }

    public interface TaskClickListener {
        void onTaskClicked(DesktopWindow window);
    }

    private LinearLayout startButton;
    private LinearLayout taskArea;
    private TextView clockText;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 1000L);
        }
    };
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private StartMenuListener startMenuListener;
    private TaskClickListener taskClickListener;
    private final List<DesktopWindow> windows = new ArrayList<DesktopWindow>();
    private DesktopWindow activeWindow;

    public TaskbarView(Context context) {
        super(context);
        init(context);
    }

    public TaskbarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context c) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.win_taskbar);
        setPadding(dp(2), dp(4), dp(2), dp(2));
        PixelUi.flatten(this);

        startButton = new LinearLayout(c);
        startButton.setOrientation(HORIZONTAL);
        startButton.setGravity(Gravity.CENTER_VERTICAL);
        startButton.setClickable(true);
        setStartPressed(false);
        PixelIconView logo = new PixelIconView(c, PixelIconView.TYPE_LOGO);
        startButton.addView(logo, new LayoutParams(dp(16), dp(16)));
        TextView startText = PixelUi.label(c, "Iniciar");
        startText.setTypeface(PixelUi.FONT_BOLD);
        LayoutParams stlp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stlp.leftMargin = dp(4);
        startButton.addView(startText, stlp);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (startMenuListener != null) startMenuListener.onStartMenuClicked();
            }
        });
        addView(startButton, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View sep = PixelUi.etched(c, false);
        LayoutParams slp = new LayoutParams(dp(2), ViewGroup.LayoutParams.MATCH_PARENT);
        slp.leftMargin = dp(4);
        slp.rightMargin = dp(4);
        addView(sep, slp);

        taskArea = new LinearLayout(c);
        taskArea.setOrientation(HORIZONTAL);
        addView(taskArea, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        clockText = PixelUi.statusField(c, "");
        clockText.setGravity(Gravity.CENTER);
        clockText.setPadding(dp(10), 0, dp(10), 0);
        LayoutParams clp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        clp.leftMargin = dp(4);
        addView(clockText, clp);
        updateClock();
    }

    public void setStartMenuListener(StartMenuListener l) {
        this.startMenuListener = l;
    }

    public void setTaskClickListener(TaskClickListener l) {
        this.taskClickListener = l;
    }

    public View getStartButton() {
        return startButton;
    }

    /** Aparencia "afundada" do botao Iniciar enquanto o menu estiver aberto. */
    public void setStartPressed(boolean pressed) {
        startButton.setBackgroundResource(pressed ? R.drawable.win_pressed : R.drawable.win_button);
        startButton.setPadding(dp(pressed ? 5 : 4), dp(pressed ? 1 : 0), dp(6), 0);
    }

    public void setWindows(List<DesktopWindow> list, DesktopWindow active) {
        windows.clear();
        windows.addAll(list);
        activeWindow = active;
        rebuildTasks();
    }

    private void rebuildTasks() {
        taskArea.removeAllViews();
        int n = windows.size();
        if (n == 0) return;
        Context c = getContext();
        int avail = taskArea.getWidth();
        int max = dp(160);
        int each = avail > 0 ? Math.min(max, (avail - dp(3) * n) / n) : max;
        each = Math.max(each, dp(36));

        for (int i = 0; i < n; i++) {
            final DesktopWindow w = windows.get(i);
            boolean isActive = (w == activeWindow) && !w.isMinimized();
            LinearLayout btn = new LinearLayout(c);
            btn.setOrientation(HORIZONTAL);
            btn.setGravity(Gravity.CENTER_VERTICAL);
            btn.setBackgroundResource(isActive ? R.drawable.win_pressed : R.drawable.win_button);
            btn.setPadding(dp(isActive ? 5 : 4), dp(isActive ? 1 : 0), dp(4), 0);
            btn.setClickable(true);
            PixelUi.flatten(btn);

            PixelIconView icon = new PixelIconView(c, w.getIconType());
            btn.addView(icon, new LayoutParams(dp(14), dp(14)));
            TextView t = PixelUi.label(c, w.getTitle());
            t.setTypeface(isActive ? PixelUi.FONT_BOLD : PixelUi.FONT);
            LayoutParams tlp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = dp(4);
            btn.addView(t, tlp);

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (taskClickListener != null) taskClickListener.onTaskClicked(w);
                }
            });
            LayoutParams blp = new LayoutParams(each, ViewGroup.LayoutParams.MATCH_PARENT);
            blp.rightMargin = dp(3);
            taskArea.addView(btn, blp);
        }
    }

    private void updateClock() {
        clockText.setText(clockFormat.format(new Date()));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) {
            post(new Runnable() {
                @Override
                public void run() {
                    rebuildTasks();
                }
            });
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(clockRunnable);
        handler.post(clockRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(clockRunnable);
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    private int dp(float v) {
        return PixelUi.dp(getContext(), v);
    }
}
