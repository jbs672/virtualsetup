package com.example.desktopenv;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Area de trabalho principal. Usa android.app.Activity pura (sem AppCompat / Material).
 */
public class MainActivity extends Activity {

    // Menu de contexto da area de trabalho
    private static final int MENU_ARRANGE = 1;
    private static final int MENU_REFRESH = 2;
    private static final int MENU_NEXT_WALLPAPER = 3;
    private static final int MENU_WALLPAPER = 4;
    private static final int MENU_PROPERTIES = 5;

    // Menu Iniciar
    private static final int START_EXPLORER = 10;
    private static final int START_DOCUMENTS = 11;
    private static final int START_NOTEPAD = 12;
    private static final int START_FANTASY = 13;
    private static final int START_WALLPAPER = 14;
    private static final int START_ABOUT = 15;
    private static final int START_SHUTDOWN = 16;

    // Icones
    private static final int APP_COMPUTER = 100;
    private static final int APP_DOCUMENTS = 101;
    private static final int APP_NOTEPAD = 102;
    private static final int APP_FANTASY = 103;

    private DesktopCoreEngine engine;
    private WallpaperManager wallpaperManager;
    private WallpaperView wallpaperView;
    private FrameLayout iconLayer;
    private FrameLayout overlayLayer;
    private TaskbarView taskbar;
    private final List<DesktopIconView> icons = new ArrayList<DesktopIconView>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        wallpaperView = (WallpaperView) findViewById(R.id.wallpaper);
        iconLayer = (FrameLayout) findViewById(R.id.icon_layer);
        FrameLayout windowLayer = (FrameLayout) findViewById(R.id.window_layer);
        overlayLayer = (FrameLayout) findViewById(R.id.overlay_layer);
        taskbar = (TaskbarView) findViewById(R.id.taskbar);

        VirtualDisk.getRoot(this);

        engine = new DesktopCoreEngine(this, windowLayer, overlayLayer);
        engine.setTaskbar(taskbar);

        wallpaperManager = new WallpaperManager(this);
        wallpaperManager.apply(wallpaperView);
        wallpaperView.setGestureListener(new WallpaperView.WallpaperGestureListener() {
            @Override
            public void onWallpaperTap(float x, float y) {
                // Toque curto (< 1000 ms) ou esbarrao: apenas limpa a selecao, nunca abre menu.
                ContextMenuView.dismissCurrent();
                clearIconSelection();
            }

            @Override
            public void onWallpaperLongPress(float x, float y) {
                clearIconSelection();
                showDesktopMenu(x, y);
            }
        });

        taskbar.setStartMenuListener(new TaskbarView.StartMenuListener() {
            @Override
            public void onStartMenuClicked() {
                showStartMenu();
            }
        });

        createDesktopIcons();
        enterImmersiveMode();
    }

    // ------------------------------------------------------------------ Icones

    private void createDesktopIcons() {
        addIcon(APP_COMPUTER, PixelIconView.TYPE_COMPUTER, "Meu computador");
        addIcon(APP_DOCUMENTS, PixelIconView.TYPE_MY_DOCUMENTS, "Meus documentos");
        addIcon(APP_NOTEPAD, PixelIconView.TYPE_NOTEPAD, "Bloco de notas");
        addIcon(APP_FANTASY, PixelIconView.TYPE_FANTASY, "Fantasy PC");
        arrangeIcons();
    }

    private void addIcon(int appId, int iconType, String label) {
        DesktopIconView icon = new DesktopIconView(this, appId, iconType, label);
        icon.setIconListener(new DesktopIconView.IconListener() {
            @Override
            public void onIconSelected(DesktopIconView v) {
                for (int i = 0; i < icons.size(); i++) {
                    icons.get(i).setIconSelected(icons.get(i) == v);
                }
            }

            @Override
            public void onIconOpened(DesktopIconView v) {
                launch(v.getAppId());
            }
        });
        icons.add(icon);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(PixelUi.dp(this, 80),
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        iconLayer.addView(icon, lp);
    }

    private void arrangeIcons() {
        for (int i = 0; i < icons.size(); i++) {
            DesktopIconView icon = icons.get(i);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) icon.getLayoutParams();
            lp.leftMargin = PixelUi.dp(this, 6);
            lp.topMargin = PixelUi.dp(this, 8) + i * PixelUi.dp(this, 82);
            icon.setLayoutParams(lp);
        }
    }

    private void clearIconSelection() {
        for (int i = 0; i < icons.size(); i++) {
            icons.get(i).setIconSelected(false);
        }
    }

    private void launch(int appId) {
        File root = VirtualDisk.getRoot(this);
        switch (appId) {
            case APP_COMPUTER:
            case START_EXPLORER:
                FileManagerView.open(engine, root);
                break;
            case APP_DOCUMENTS:
            case START_DOCUMENTS:
                FileManagerView.open(engine, new File(root, "Documentos"));
                break;
            case APP_NOTEPAD:
            case START_NOTEPAD:
                NotepadView.open(engine, null);
                break;
            case APP_FANTASY:
            case START_FANTASY:
                FantasyPcView.open(engine);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ Menu de contexto da area de trabalho

    private void showDesktopMenu(float x, float y) {
        ContextMenuView menu = new ContextMenuView(this);
        menu.addItem(MENU_ARRANGE, "Organizar \u00edcones");
        menu.addItem(MENU_REFRESH, "Atualizar");
        menu.addSeparator();
        menu.addItem(MENU_NEXT_WALLPAPER, "Pr\u00f3ximo papel de parede");
        menu.addItem(MENU_WALLPAPER, "Personalizar papel de parede...");
        menu.addSeparator();
        menu.addItem(MENU_PROPERTIES, "Propriedades");
        menu.setOnItemSelectedListener(new ContextMenuView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(int id) {
                handleDesktopMenu(id);
            }
        });
        int[] origin = PixelUi.positionIn(wallpaperView, overlayLayer);
        menu.showAt(overlayLayer, origin[0] + (int) x, origin[1] + (int) y);
    }

    private void handleDesktopMenu(int id) {
        switch (id) {
            case MENU_ARRANGE:
                arrangeIcons();
                break;
            case MENU_REFRESH:
                wallpaperManager.apply(wallpaperView);
                break;
            case MENU_NEXT_WALLPAPER:
                wallpaperManager.next();
                wallpaperManager.apply(wallpaperView);
                break;
            case MENU_WALLPAPER:
            case MENU_PROPERTIES:
                WallpaperSettingsView.open(engine, wallpaperManager, wallpaperView);
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ Menu Iniciar

    private void showStartMenu() {
        ContextMenuView menu = new ContextMenuView(this);
        menu.setBanner("Virtual Setup");
        menu.addItem(START_EXPLORER, "Explorador de arquivos");
        menu.addItem(START_DOCUMENTS, "Meus documentos");
        menu.addItem(START_NOTEPAD, "Bloco de notas");
        menu.addItem(START_FANTASY, "Fantasy PC");
        menu.addSeparator();
        menu.addItem(START_WALLPAPER, "Papel de parede...");
        menu.addItem(START_ABOUT, "Sobre o Virtual Setup");
        menu.addSeparator();
        menu.addItem(START_SHUTDOWN, "Desligar...");
        menu.setOnItemSelectedListener(new ContextMenuView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(int id) {
                handleStartMenu(id);
            }
        });
        menu.setOnDismissListener(new ContextMenuView.OnDismissListener() {
            @Override
            public void onDismiss(ContextMenuView m) {
                taskbar.setStartPressed(false);
            }
        });
        taskbar.setStartPressed(true);
        int[] pos = PixelUi.positionIn(taskbar, overlayLayer);
        menu.showAbove(overlayLayer, pos[0] + PixelUi.dp(this, 2), pos[1] + PixelUi.dp(this, 2));
    }

    private void handleStartMenu(int id) {
        switch (id) {
            case START_WALLPAPER:
                WallpaperSettingsView.open(engine, wallpaperManager, wallpaperView);
                break;
            case START_ABOUT:
                engine.showMessageBox("Sobre o Virtual Setup",
                        "Virtual Setup 0.0.14\n\nAmbiente de desktop cl\u00e1ssico para Android.\n"
                                + "Fantasy PC: CPU FantasyISA de 16 bits, 64 KB de RAM, 8 registradores, "
                                + "tela 128x128, barramento de E/S e execucao real de programas.",
                        PixelIconView.TYPE_INFO);
                break;
            case START_SHUTDOWN:
                confirmShutdown();
                break;
            default:
                launch(id);
                break;
        }
    }

    private void confirmShutdown() {
        engine.showConfirm("Desligar o Virtual Setup", "Deseja realmente encerrar a sess\u00e3o?", new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });
    }

    // ------------------------------------------------------------------ Sistema

    @Override
    public void onBackPressed() {
        if (ContextMenuView.dismissCurrent()) return;
        if (engine.closeActiveWindow()) return;
        confirmShutdown();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    private void enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }
}
