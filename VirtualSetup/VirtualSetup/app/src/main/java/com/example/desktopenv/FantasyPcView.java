package com.example.desktopenv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Janela de testes do Fantasy PC.
 * Mantem o renderizador 2D original e oferece um segundo modo 3D independente.
 */
public class FantasyPcView extends LinearLayout {

    private static final long FRAME_MS = 33L;
    private static final int STEPS_PER_FRAME = 64;

    private final FantasyMachine machine = new FantasyMachine();
    private final FantasyScreenView screen;
    private final TextView status;
    private View threeDView;
    private Fantasy3DView hardware3DView;
    private Fantasy3DSoftwareView software3DView;
    private final LinearLayout originalPanel;
    private final LinearLayout threeDPanel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private boolean showing3D;

    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running || showing3D) return;
            for (int i = 0; i < STEPS_PER_FRAME; i++) {
                machine.run();
            }
            machine.renderFrame();
            screen.getMonitor().invalidate();
            if (machine.getCpu().isHalted()) {
                running = false;
                machine.powerOff();
            }
            updateStatus();
            if (running) handler.postDelayed(this, FRAME_MS);
        }
    };

    public static DesktopWindow open(DesktopCoreEngine engine) {
        return engine.openSingleton("fantasy_pc", new DesktopCoreEngine.WindowFactory() {
            @Override
            public DesktopWindow createWindow(Context context) {
                DesktopWindow w = new DesktopWindow(context, "Fantasy PC - Testes de Motor", PixelIconView.TYPE_FANTASY);
                w.setContent(new FantasyPcView(context));
                return w;
            }
        }, 320, 380);
    }

    public FantasyPcView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(PixelUi.FACE);

        // Seletor de motores: o modo original nao e removido nem alterado.
        LinearLayout tabs = new LinearLayout(context);
        tabs.setOrientation(HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        TextView originalTab = PixelUi.button(context, "Motor original 2D", new View.OnClickListener() {
            @Override public void onClick(View v) { showOriginal(); }
        });
        TextView threeDTab = PixelUi.button(context, "Novo motor 3D", new View.OnClickListener() {
            @Override public void onClick(View v) { show3D(); }
        });
        tabs.addView(originalTab, new LayoutParams(0, dp(30), 1.0f));
        LayoutParams tab2 = new LayoutParams(0, dp(30), 1.0f);
        tab2.leftMargin = dp(4);
        tabs.addView(threeDTab, tab2);
        addView(tabs, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(PixelUi.etched(context, true),
                new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        // Painel 2D original: mesmo monitor, CPU virtual e controles existentes.
        originalPanel = new LinearLayout(context);
        originalPanel.setOrientation(VERTICAL);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(3), dp(3), dp(3), dp(3));
        TextView power = PixelUi.button(context, "Ligar", new View.OnClickListener() {
            @Override public void onClick(View v) { start(); }
        });
        TextView pause = PixelUi.button(context, "Pausar", new View.OnClickListener() {
            @Override public void onClick(View v) { stop(); }
        });
        TextView reset = PixelUi.button(context, "Reiniciar", new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean was = running;
                stop();
                machine.reset();
                loadDemo();
                machine.renderFrame();
                screen.getMonitor().invalidate();
                updateStatus();
                if (was) start();
            }
        });
        TextView[] all = { power, pause, reset };
        for (int i = 0; i < all.length; i++) {
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
            if (i > 0) lp.leftMargin = dp(3);
            toolbar.addView(all[i], lp);
        }
        originalPanel.addView(toolbar, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        originalPanel.addView(PixelUi.etched(context, true),
                new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        screen = new FantasyScreenView(context);
        screen.getMonitor().setFramebuffer(machine.getFramebuffer());
        LayoutParams slp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.setMargins(dp(4), dp(4), dp(4), dp(4));
        originalPanel.addView(screen, slp);

        status = PixelUi.statusField(context, "");
        LayoutParams stlp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22));
        stlp.setMargins(dp(2), 0, dp(2), dp(2));
        originalPanel.addView(status, stlp);
        addView(originalPanel, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        // Painel 3D independente. OpenGL ES 2.0 usa GPU e depth buffer real.
        threeDPanel = new LinearLayout(context);
        threeDPanel.setOrientation(VERTICAL);
        threeDPanel.setVisibility(View.GONE);

        LinearLayout infoBar = new LinearLayout(context);
        infoBar.setOrientation(HORIZONTAL);
        infoBar.setGravity(Gravity.CENTER_VERTICAL);
        infoBar.setPadding(dp(5), dp(3), dp(5), dp(3));
        final TextView info = PixelUi.statusField(context, "OPENGL ES 2.0  |  ARRASTE PARA GIRAR A CAMERA");
        info.setTextSize(9);
        infoBar.addView(info, new LayoutParams(0, dp(26), 1.0f));
        TextView resetCamera = PixelUi.button(context, "Camera", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (hardware3DView != null) hardware3DView.resetCamera();
                if (software3DView != null) software3DView.resetCamera();
            }
        });
        infoBar.addView(resetCamera, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)));
        threeDPanel.addView(infoBar, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (Fantasy3DView.supportsOpenGLES2(context)) {
            hardware3DView = new Fantasy3DView(context);
            software3DView = null;
            hardware3DView.setStatusListener(new Fantasy3DView.StatusListener() {
                @Override public void onRenderError(String message) {
                    // Plano B automatico: troca para o 3D por Canvas, que sempre desenha.
                    fallbackToSoftware(info, message);
                }
            });
            threeDView = hardware3DView;
        } else {
            hardware3DView = null;
            software3DView = new Fantasy3DSoftwareView(context);
            threeDView = software3DView;
            info.setText("MODO 3D DE COMPATIBILIDADE - CANVAS");
        }
        threeDPanel.addView(threeDView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        addView(threeDPanel, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        loadDemo();
        machine.renderFrame();
        updateStatus();
    }

    private void fallbackToSoftware(TextView info, String reason) {
        if (hardware3DView == null || software3DView != null) return;
        try {
            hardware3DView.onPause();
        } catch (Throwable ignored) { }
        threeDPanel.removeView(hardware3DView);
        hardware3DView = null;
        software3DView = new Fantasy3DSoftwareView(getContext());
        threeDView = software3DView;
        threeDPanel.addView(software3DView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        info.setText("3D CANVAS (GPU: " + reason + ")");
    }

    private void showOriginal() {
        if (showing3D) {
            showing3D = false;
            if (hardware3DView != null) hardware3DView.onPause();
        }
        threeDPanel.setVisibility(View.GONE);
        originalPanel.setVisibility(View.VISIBLE);
    }

    private void show3D() {
        stop();
        originalPanel.setVisibility(View.GONE);
        threeDPanel.setVisibility(View.VISIBLE);
        if (!showing3D) {
            showing3D = true;
            // Primeiro torna o painel visivel; depois retoma o renderizador.
            threeDView.post(new Runnable() {
                @Override public void run() {
                    if (showing3D && getWindowVisibility() == View.VISIBLE &&
                            hardware3DView != null && threeDView.getWidth() > 0 &&
                            threeDView.getHeight() > 0) {
                        hardware3DView.onResume();
                    }
                }
            });
        } else if (hardware3DView != null) {
            hardware3DView.onResume();
        }
    }


    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (hardware3DView == null) return;
        if (visibility == View.VISIBLE && showing3D) {
            hardware3DView.post(new Runnable() {
                @Override public void run() {
                    if (showing3D && getWindowVisibility() == View.VISIBLE &&
                            hardware3DView != null) {
                        hardware3DView.onResume();
                    }
                }
            });
        } else {
            hardware3DView.onPause();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(frameRunnable);
        running = false;
        machine.powerOff();
        if (hardware3DView != null) hardware3DView.onPause();
        super.onDetachedFromWindow();
    }

    private void loadDemo() {
        // Programa original mantido: incrementa R0 continuamente.
        FantasyProgram demo = new FantasyProgramBuilder("Contador")
                .ldi(0, 0)
                .addi(0, 1)
                .jump(4)
                .build();
        machine.loadProgram(demo);
    }

    private void start() {
        if (showing3D || running) return;
        running = true;
        machine.powerOn();
        handler.post(frameRunnable);
        updateStatus();
    }

    private void stop() {
        running = false;
        machine.powerOff();
        handler.removeCallbacks(frameRunnable);
        updateStatus();
    }

    private void updateStatus() {
        CpuRegisters r = machine.getCpu().getRegisters();
        String state = machine.getCpu().isHalted() ? "HALT"
                : (running ? "Executando" : "Pausado");
        String line = String.format(Locale.US, "%s | PC=%04X | R0=%04X | INS=%d",
                state, r.pc & 0xFFFF, r.r0 & 0xFFFF,
                machine.getCpu().getInstructionCount());
        if (machine.getCpu().getLastFault() != null) {
            line = line + " | ERRO: " + machine.getCpu().getLastFault();
        }
        status.setText(line);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (showing3D && hardware3DView != null) hardware3DView.onResume();
    }

    private int dp(float v) {
        return PixelUi.dp(getContext(), v);
    }
}
