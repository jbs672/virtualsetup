package com.example.desktopenv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Explorador de arquivos no estilo Windows Explorer classico / Thunar (XFCE):
 *  - Barra de menu: Arquivo, Editar, Exibir, Ajuda.
 *  - Barra de ferramentas: Voltar, Avancar, Acima.
 *  - Barra de endereco editavel (C:\...) com botao "Ir".
 *  - Lista em detalhes com colunas Nome, Tamanho, Tipo e Data de modificacao (clique ordena).
 *  - Duplo clique implementado manualmente por intervalo de tempo entre toques.
 *  - Menu de contexto customizado por toque longo (1000 ms) ou clique direito do mouse.
 */
public class FileManagerView extends LinearLayout {

    public interface ToggleListener {
        void onToggle();
    }

    public static final long DOUBLE_CLICK_MS = 500L;

    private static final int COL_NAME = 0;
    private static final int COL_SIZE = 1;
    private static final int COL_TYPE = 2;
    private static final int COL_DATE = 3;
    private static final int[] COL_WIDTH_DP = { 210, 80, 150, 125 };
    private static final String[] COL_TITLES = { "Nome", "Tamanho", "Tipo", "Data de modifica\u00e7\u00e3o" };

    // ids de menu
    private static final int M_OPEN = 1, M_NEW_FOLDER = 2, M_NEW_TEXT = 3, M_DELETE = 4, M_RENAME = 5,
            M_PROPERTIES = 6, M_CLOSE = 7, M_CUT = 8, M_COPY = 9, M_PASTE = 10, M_REFRESH = 11,
            M_SORT_NAME = 12, M_SORT_SIZE = 13, M_SORT_TYPE = 14, M_SORT_DATE = 15, M_STATUSBAR = 16,
            M_ABOUT = 17, M_UP = 18;

    // area de transferencia compartilhada entre janelas do Explorer
    private static File sClipboard;
    private static boolean sClipboardCut;
    private static final List<FileManagerView> sInstances = new ArrayList<FileManagerView>();

    private final DesktopCoreEngine engine;
    private final File root;
    private File current;
    private DesktopWindow hostWindow;
    private ToggleListener toggleListener;

    private final List<File> backStack = new ArrayList<File>();
    private final List<File> forwardStack = new ArrayList<File>();
    private final List<File> entries = new ArrayList<File>();

    private int selected = -1;
    private int sortColumn = COL_NAME;
    private boolean sortAscending = true;

    private TextView backButton, forwardButton, upButton;
    private EditText addressField;
    private final TextView[] headerCells = new TextView[4];
    private ListView listView;
    private FileAdapter adapter;
    private LinearLayout statusBar;
    private TextView statusLeft, statusRight;

    // controle manual de toque / duplo clique
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int touchSlop;
    private float touchDownX, touchDownY;
    private boolean touchMoved;
    private boolean longPressFired;
    private long lastTapTime;
    private int lastTapPosition = -1;
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (touchMoved) return;
            longPressFired = true;
            showContextMenuAt(touchDownX, touchDownY);
        }
    };

    // ------------------------------------------------------------------ Abertura

    public static DesktopWindow open(DesktopCoreEngine engine, File startDir) {
        Context c = engine.getContext();
        FileManagerView view = new FileManagerView(c, engine, startDir);
        DesktopWindow w = new DesktopWindow(c, "Explorando", PixelIconView.TYPE_FOLDER);
        w.setContent(view);
        view.setHostWindow(w);
        engine.addWindow(w, 470, 360);
        return w;
    }

    public FileManagerView(Context context) {
        this(context, null, null);
    }

    public FileManagerView(Context context, DesktopCoreEngine engine, File startDir) {
        super(context);
        this.engine = engine;
        this.root = VirtualDisk.getRoot(context);
        this.touchSlop = Math.max(ViewConfiguration.get(context).getScaledTouchSlop(), PixelUi.dp(context, 8));
        setOrientation(VERTICAL);
        setBackgroundColor(PixelUi.FACE);
        PixelUi.flatten(this);

        buildMenuBar(context);
        addView(PixelUi.etched(context, true), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        buildToolbar(context);
        addView(PixelUi.etched(context, true), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        buildAddressBar(context);
        buildFileList(context);
        buildStatusBar(context);

        File start = startDir != null && startDir.isDirectory() && VirtualDisk.isInside(root, startDir)
                ? startDir : root;
        navigateTo(start, false);
    }

    public void setHostWindow(DesktopWindow w) {
        this.hostWindow = w;
        updateTitle();
    }

    public void setToggleListener(ToggleListener l) {
        this.toggleListener = l;
    }

    // ------------------------------------------------------------------ Construcao da UI

    private void buildMenuBar(Context c) {
        if (engine == null) return;
        MenuBarView bar = new MenuBarView(c, engine);
        bar.addMenu("Arquivo", new MenuBarView.MenuProvider() {
            @Override
            public void onBuildMenu(ContextMenuView m) {
                boolean has = getSelectedFile() != null;
                m.addDefaultItem(M_OPEN, "Abrir");
                m.addSeparator();
                m.addItem(M_NEW_FOLDER, "Nova pasta");
                m.addItem(M_NEW_TEXT, "Novo documento de texto");
                m.addSeparator();
                m.addItem(M_DELETE, "Excluir", has);
                m.addItem(M_RENAME, "Renomear", has);
                m.addItem(M_PROPERTIES, "Propriedades");
                m.addSeparator();
                m.addItem(M_CLOSE, "Fechar");
                m.setOnItemSelectedListener(menuHandler);
            }
        });
        bar.addMenu("Editar", new MenuBarView.MenuProvider() {
            @Override
            public void onBuildMenu(ContextMenuView m) {
                boolean has = getSelectedFile() != null;
                m.addItem(M_CUT, "Recortar", has);
                m.addItem(M_COPY, "Copiar", has);
                m.addItem(M_PASTE, "Colar", sClipboard != null && sClipboard.exists());
                m.setOnItemSelectedListener(menuHandler);
            }
        });
        bar.addMenu("Exibir", new MenuBarView.MenuProvider() {
            @Override
            public void onBuildMenu(ContextMenuView m) {
                m.addCheckItem(M_STATUSBAR, "Barra de status", statusBar.getVisibility() == VISIBLE);
                m.addSeparator();
                m.addRadioItem(M_SORT_NAME, "Organizar por nome", sortColumn == COL_NAME);
                m.addRadioItem(M_SORT_SIZE, "Organizar por tamanho", sortColumn == COL_SIZE);
                m.addRadioItem(M_SORT_TYPE, "Organizar por tipo", sortColumn == COL_TYPE);
                m.addRadioItem(M_SORT_DATE, "Organizar por data", sortColumn == COL_DATE);
                m.addSeparator();
                m.addItem(M_UP, "Acima um n\u00edvel", !VirtualDisk.isRoot(root, current));
                m.addItem(M_REFRESH, "Atualizar");
                m.setOnItemSelectedListener(menuHandler);
            }
        });
        bar.addMenu("Ajuda", new MenuBarView.MenuProvider() {
            @Override
            public void onBuildMenu(ContextMenuView m) {
                m.addItem(M_ABOUT, "Sobre o Explorador");
                m.setOnItemSelectedListener(menuHandler);
            }
        });
        addView(bar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildToolbar(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(3), dp(3), dp(3), dp(3));

        backButton = PixelUi.button(c, "\u2190 Voltar", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goBack();
            }
        });
        forwardButton = PixelUi.button(c, "Avan\u00e7ar \u2192", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goForward();
            }
        });
        upButton = PixelUi.button(c, "\u2191 Acima", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goUp();
            }
        });
        bar.addView(backButton, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
        LayoutParams flp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
        flp.leftMargin = dp(3);
        bar.addView(forwardButton, flp);
        View sep = PixelUi.etched(c, false);
        LayoutParams slp = new LayoutParams(dp(2), dp(24));
        slp.leftMargin = dp(5);
        slp.rightMargin = dp(5);
        bar.addView(sep, slp);
        bar.addView(upButton, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));
        addView(bar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildAddressBar(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(3), dp(3), dp(3));

        TextView label = PixelUi.label(c, "Endere\u00e7o");
        row.addView(label, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addressField = PixelUi.textField(c, true);
        addressField.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_UP)) {
                    goToAddress();
                    return true;
                }
                return false;
            }
        });
        LayoutParams alp = new LayoutParams(0, dp(28), 1f);
        alp.leftMargin = dp(6);
        row.addView(addressField, alp);

        TextView go = PixelUi.button(c, "Ir", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToAddress();
            }
        });
        go.setMinWidth(dp(40));
        LayoutParams glp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
        glp.leftMargin = dp(3);
        row.addView(go, glp);
        addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildFileList(Context c) {
        FrameLayout sunken = new FrameLayout(c);
        sunken.setBackgroundResource(R.drawable.win_sunken);
        sunken.setPadding(dp(2), dp(2), dp(2), dp(2));

        HorizontalScrollView hscroll = new HorizontalScrollView(c);
        hscroll.setFillViewport(true);
        hscroll.setOverScrollMode(OVER_SCROLL_NEVER);
        hscroll.setBackgroundColor(PixelUi.WINDOW);

        int totalWidth = 0;
        for (int i = 0; i < COL_WIDTH_DP.length; i++) totalWidth += dp(COL_WIDTH_DP[i]);

        LinearLayout table = new LinearLayout(c);
        table.setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            final int col = i;
            TextView cell = PixelUi.button(c, COL_TITLES[i], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sortBy(col);
                }
            });
            cell.setGravity(Gravity.CENTER_VERTICAL | (i == COL_SIZE ? Gravity.RIGHT : Gravity.LEFT));
            cell.setPadding(dp(6), 0, dp(6), 0);
            cell.setMinHeight(0);
            headerCells[i] = cell;
            header.addView(cell, new LayoutParams(dp(COL_WIDTH_DP[i]), dp(24)));
        }
        table.addView(header, new LayoutParams(totalWidth, dp(24)));

        listView = new ListView(c);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setSelector(new android.graphics.drawable.ColorDrawable(PixelUi.TRANSPARENT));
        listView.setCacheColorHint(PixelUi.TRANSPARENT);
        listView.setOverScrollMode(OVER_SCROLL_NEVER);
        listView.setBackgroundColor(PixelUi.WINDOW);
        listView.setVerticalFadingEdgeEnabled(false);
        adapter = new FileAdapter();
        listView.setAdapter(adapter);
        listView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                handleListTouch(e);
                return false; // deixa a ListView rolar normalmente
            }
        });
        table.addView(listView, new LayoutParams(totalWidth, 0, 1f));

        hscroll.addView(table, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        sunken.addView(hscroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        addView(sunken, lp);
        updateHeaderTitles();
    }

    private void buildStatusBar(Context c) {
        statusBar = new LinearLayout(c);
        statusBar.setOrientation(HORIZONTAL);
        statusBar.setPadding(dp(2), dp(3), dp(20), dp(2));
        statusLeft = PixelUi.statusField(c, "");
        statusRight = PixelUi.statusField(c, "");
        statusBar.addView(statusLeft, new LayoutParams(0, dp(22), 1f));
        LayoutParams rlp = new LayoutParams(dp(120), dp(22));
        rlp.leftMargin = dp(2);
        statusBar.addView(statusRight, rlp);
        addView(statusBar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ------------------------------------------------------------------ Toque manual / duplo clique

    private void handleListTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = e.getX();
                touchDownY = e.getY();
                touchMoved = false;
                longPressFired = false;
                handler.removeCallbacks(longPressRunnable);
                if ((e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                    longPressFired = true;
                    int pos = listView.pointToPosition((int) touchDownX, (int) touchDownY);
                    if (pos != AdapterView.INVALID_POSITION) setSelected(pos);
                    showContextMenuAt(touchDownX, touchDownY);
                } else {
                    handler.postDelayed(longPressRunnable, WallpaperView.LONG_PRESS_DELAY_MS);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(e.getX() - touchDownX) > touchSlop || Math.abs(e.getY() - touchDownY) > touchSlop) {
                    touchMoved = true;
                    handler.removeCallbacks(longPressRunnable);
                }
                break;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);
                if (!touchMoved && !longPressFired) {
                    handleTap(listView.pointToPosition((int) e.getX(), (int) e.getY()));
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                touchMoved = true;
                handler.removeCallbacks(longPressRunnable);
                break;
            default:
                break;
        }
    }

    /** Duplo clique: dois toques no MESMO item em ate DOUBLE_CLICK_MS milissegundos. */
    private void handleTap(int position) {
        long now = SystemClock.uptimeMillis();
        if (position == AdapterView.INVALID_POSITION || position >= entries.size()) {
            setSelected(-1);
            lastTapPosition = -1;
            lastTapTime = 0L;
            return;
        }
        if (position == lastTapPosition && (now - lastTapTime) <= DOUBLE_CLICK_MS) {
            lastTapPosition = -1;
            lastTapTime = 0L;
            setSelected(position);
            openEntry(entries.get(position));
        } else {
            lastTapPosition = position;
            lastTapTime = now;
            setSelected(position);
        }
    }

    private void showContextMenuAt(float x, float y) {
        if (engine == null) return;
        int pos = listView.pointToPosition((int) x, (int) y);
        ContextMenuView m = new ContextMenuView(getContext());
        if (pos != AdapterView.INVALID_POSITION && pos < entries.size()) {
            setSelected(pos);
            m.addDefaultItem(M_OPEN, "Abrir");
            m.addSeparator();
            m.addItem(M_CUT, "Recortar");
            m.addItem(M_COPY, "Copiar");
            m.addSeparator();
            m.addItem(M_DELETE, "Excluir");
            m.addItem(M_RENAME, "Renomear");
            m.addSeparator();
            m.addItem(M_PROPERTIES, "Propriedades");
        } else {
            setSelected(-1);
            m.addItem(M_REFRESH, "Atualizar");
            m.addSeparator();
            m.addItem(M_PASTE, "Colar", sClipboard != null && sClipboard.exists());
            m.addSeparator();
            m.addItem(M_NEW_FOLDER, "Nova pasta");
            m.addItem(M_NEW_TEXT, "Novo documento de texto");
            m.addSeparator();
            m.addItem(M_PROPERTIES, "Propriedades");
        }
        m.setOnItemSelectedListener(menuHandler);
        FrameLayout overlay = engine.getOverlayLayer();
        int[] origin = PixelUi.positionIn(listView, overlay);
        m.showAt(overlay, origin[0] + (int) x, origin[1] + (int) y);
    }

    private final ContextMenuView.OnItemSelectedListener menuHandler = new ContextMenuView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(int id) {
            handleMenu(id);
        }
    };

    private void handleMenu(int id) {
        File sel = getSelectedFile();
        switch (id) {
            case M_OPEN:
                if (sel != null) openEntry(sel);
                break;
            case M_NEW_FOLDER:
                createNewFolder();
                break;
            case M_NEW_TEXT:
                createNewTextFile();
                break;
            case M_DELETE:
                if (sel != null) confirmDelete(sel);
                break;
            case M_RENAME:
                if (sel != null) rename(sel);
                break;
            case M_PROPERTIES:
                showProperties(sel != null ? sel : current);
                break;
            case M_CLOSE:
                if (hostWindow != null && engine != null) engine.closeWindow(hostWindow);
                break;
            case M_CUT:
                if (sel != null) {
                    sClipboard = sel;
                    sClipboardCut = true;
                }
                break;
            case M_COPY:
                if (sel != null) {
                    sClipboard = sel;
                    sClipboardCut = false;
                }
                break;
            case M_PASTE:
                paste();
                break;
            case M_REFRESH:
                reload();
                break;
            case M_SORT_NAME:
                setSort(COL_NAME, true);
                break;
            case M_SORT_SIZE:
                setSort(COL_SIZE, true);
                break;
            case M_SORT_TYPE:
                setSort(COL_TYPE, true);
                break;
            case M_SORT_DATE:
                setSort(COL_DATE, true);
                break;
            case M_STATUSBAR:
                statusBar.setVisibility(statusBar.getVisibility() == VISIBLE ? GONE : VISIBLE);
                if (toggleListener != null) toggleListener.onToggle();
                break;
            case M_UP:
                goUp();
                break;
            case M_ABOUT:
                if (engine != null) {
                    engine.showMessageBox("Sobre o Explorador",
                            "Explorador de Arquivos - Virtual Setup 0.0.13\n\n"
                                    + "Disco C:\\ virtual armazenado na mem\u00f3ria interna do aplicativo.",
                            PixelIconView.TYPE_INFO);
                }
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ Navegacao

    public void navigateTo(File dir, boolean addHistory) {
        if (dir == null || !dir.isDirectory() || !VirtualDisk.isInside(root, dir)) {
            showError("O caminho n\u00e3o est\u00e1 dispon\u00edvel.");
            return;
        }
        if (addHistory && current != null && !current.equals(dir)) {
            backStack.add(current);
            forwardStack.clear();
        }
        current = dir;
        lastTapPosition = -1;
        reload();
    }

    public void goBack() {
        if (backStack.isEmpty()) return;
        forwardStack.add(current);
        current = backStack.remove(backStack.size() - 1);
        reload();
    }

    public void goForward() {
        if (forwardStack.isEmpty()) return;
        backStack.add(current);
        current = forwardStack.remove(forwardStack.size() - 1);
        reload();
    }

    public void goUp() {
        if (current == null || VirtualDisk.isRoot(root, current)) return;
        File parent = current.getParentFile();
        if (parent != null) navigateTo(parent, true);
    }

    private void goToAddress() {
        PixelUi.hideKeyboard(addressField);
        String text = addressField.getText().toString();
        File target = VirtualDisk.resolve(root, current, text);
        if (target == null || !target.exists()) {
            if (engine != null) {
                engine.showMessageBox("Endere\u00e7o",
                        "N\u00e3o foi poss\u00edvel encontrar '" + text
                                + "'. Verifique a ortografia e tente novamente.",
                        PixelIconView.TYPE_ERROR);
            }
            addressField.setText(VirtualDisk.toDisplayPath(root, current));
            return;
        }
        if (target.isDirectory()) {
            navigateTo(target, true);
        } else {
            openEntry(target);
            addressField.setText(VirtualDisk.toDisplayPath(root, current));
        }
    }

    private void openEntry(File f) {
        if (f.isDirectory()) {
            navigateTo(f, true);
            return;
        }
        if (engine == null) return;
        if (VirtualDisk.isTextFile(f)) {
            if ("txt".equals(VirtualDisk.extension(f.getName()))
                    && FantasyScriptView.isScriptFile(f)) {
                FantasyScriptView.open(engine, f);
            } else {
                NotepadView.open(engine, f);
            }
        } else if (VirtualDisk.extension(f.getName()).equals("fpc")) {
            if (FantasyScriptView.isScriptFile(f)) {
                FantasyScriptView.open(engine, f);
            } else {
                // Compatibilidade com os antigos arquivos binarios da FantasyISA.
                FantasyPcView.open(engine);
            }
        } else {
            engine.showMessageBox("Abrir arquivo",
                    "N\u00e3o h\u00e1 nenhum programa associado ao arquivo '" + f.getName() + "'.",
                    PixelIconView.TYPE_WARNING);
        }
    }

    // ------------------------------------------------------------------ Operacoes de arquivo

    private void createNewFolder() {
        if (engine == null) return;
        engine.showInputBox("Nova pasta", "Nome da nova pasta:",
                VirtualDisk.uniqueName(current, "Nova pasta", ""), new DesktopCoreEngine.InputCallback() {
                    @Override
                    public void onInput(String value) {
                        if (!VirtualDisk.isValidName(value)) {
                            showError("O nome de arquivo n\u00e3o pode conter: \\ / : * ? \" < > |");
                            return;
                        }
                        File f = new File(current, value.trim());
                        if (f.exists()) {
                            showError("J\u00e1 existe um item com o nome '" + value.trim() + "'.");
                            return;
                        }
                        if (!f.mkdirs()) showError("N\u00e3o foi poss\u00edvel criar a pasta.");
                        notifyAllChanged();
                        selectFile(f);
                    }
                });
    }

    private void createNewTextFile() {
        if (engine == null) return;
        engine.showInputBox("Novo documento de texto", "Nome do arquivo:",
                VirtualDisk.uniqueName(current, "Novo documento de texto", ".txt"),
                new DesktopCoreEngine.InputCallback() {
                    @Override
                    public void onInput(String value) {
                        if (!VirtualDisk.isValidName(value)) {
                            showError("O nome de arquivo n\u00e3o pode conter: \\ / : * ? \" < > |");
                            return;
                        }
                        String name = value.trim();
                        if (VirtualDisk.extension(name).length() == 0) name = name + ".txt";
                        File f = new File(current, name);
                        if (f.exists()) {
                            showError("J\u00e1 existe um item com o nome '" + name + "'.");
                            return;
                        }
                        if (!VirtualDisk.writeText(f, "")) showError("N\u00e3o foi poss\u00edvel criar o arquivo.");
                        notifyAllChanged();
                        selectFile(f);
                    }
                });
    }

    private void confirmDelete(final File f) {
        if (engine == null) return;
        String what = f.isDirectory() ? "a pasta '" + f.getName() + "' e todo o seu conte\u00fado"
                : "'" + f.getName() + "'";
        engine.showConfirm("Confirmar exclus\u00e3o", "Tem certeza de que deseja excluir " + what + "?",
                new Runnable() {
                    @Override
                    public void run() {
                        if (!VirtualDisk.deleteRecursive(f)) showError("N\u00e3o foi poss\u00edvel excluir '" + f.getName() + "'.");
                        if (sClipboard != null && sClipboard.equals(f)) sClipboard = null;
                        selected = -1;
                        notifyAllChanged();
                    }
                });
    }

    private void rename(final File f) {
        if (engine == null) return;
        engine.showInputBox("Renomear", "Novo nome para '" + f.getName() + "':", f.getName(),
                new DesktopCoreEngine.InputCallback() {
                    @Override
                    public void onInput(String value) {
                        if (!VirtualDisk.isValidName(value)) {
                            showError("O nome de arquivo n\u00e3o pode conter: \\ / : * ? \" < > |");
                            return;
                        }
                        File dest = new File(f.getParentFile(), value.trim());
                        if (dest.equals(f)) return;
                        if (dest.exists()) {
                            showError("J\u00e1 existe um item com o nome '" + value.trim() + "'.");
                            return;
                        }
                        if (!f.renameTo(dest)) showError("N\u00e3o foi poss\u00edvel renomear o item.");
                        notifyAllChanged();
                        selectFile(dest);
                    }
                });
    }

    private void paste() {
        if (sClipboard == null || !sClipboard.exists()) return;
        File src = sClipboard;
        if (src.isDirectory() && VirtualDisk.isInside(src, current)) {
            showError("A pasta de destino \u00e9 uma subpasta da pasta de origem.");
            return;
        }
        File dest = new File(current, src.getName());
        if (dest.exists()) {
            if (sClipboardCut && dest.equals(src)) return;
            String name = src.getName();
            String ext = VirtualDisk.extension(name);
            String base = (ext.length() > 0 && !src.isDirectory()) ? name.substring(0, name.length() - ext.length() - 1) : name;
            String suffix = (ext.length() > 0 && !src.isDirectory()) ? "." + ext : "";
            dest = new File(current, VirtualDisk.uniqueName(current, "C\u00f3pia de " + base, suffix));
        }
        boolean ok;
        if (sClipboardCut) {
            ok = src.renameTo(dest);
            if (ok) sClipboard = null;
        } else {
            ok = VirtualDisk.copyRecursive(src, dest);
        }
        if (!ok) showError("N\u00e3o foi poss\u00edvel colar '" + src.getName() + "'.");
        notifyAllChanged();
        selectFile(dest);
    }

    private void showProperties(File f) {
        if (engine == null || f == null) return;
        String location = f.getParentFile() != null && VirtualDisk.isInside(root, f.getParentFile())
                ? VirtualDisk.toDisplayPath(root, f.getParentFile()) : "";
        StringBuilder sb = new StringBuilder();
        sb.append("Nome: ").append(VirtualDisk.isRoot(root, f) ? "Disco local (C:)" : f.getName()).append('\n');
        sb.append("Tipo: ").append(VirtualDisk.isRoot(root, f) ? "Disco local" : VirtualDisk.typeName(f)).append('\n');
        if (location.length() > 0) sb.append("Local: ").append(location).append('\n');
        long size = VirtualDisk.sizeOf(f);
        sb.append("Tamanho: ").append(VirtualDisk.formatHuman(size)).append(" (").append(size).append(" bytes)\n");
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            sb.append("Cont\u00e9m: ").append(children == null ? 0 : children.length).append(" item(ns)\n");
        }
        sb.append("Modificado em: ").append(VirtualDisk.formatDate(f.lastModified()));
        engine.showMessageBox("Propriedades", sb.toString(), VirtualDisk.isRoot(root, f)
                ? PixelIconView.TYPE_COMPUTER : VirtualDisk.iconFor(f));
    }

    private void showError(String message) {
        if (engine != null) engine.showMessageBox("Erro", message, PixelIconView.TYPE_ERROR);
    }

    // ------------------------------------------------------------------ Listagem / ordenacao

    public void reload() {
        File keep = getSelectedFile();
        entries.clear();
        File[] children = current != null ? current.listFiles() : null;
        if (children != null) {
            for (int i = 0; i < children.length; i++) entries.add(children[i]);
        }
        sortEntries();
        selected = -1;
        if (keep != null) {
            int idx = entries.indexOf(keep);
            if (idx >= 0) selected = idx;
        }
        adapter.notifyDataSetChanged();
        addressField.setText(VirtualDisk.toDisplayPath(root, current));
        PixelUi.setEnabledLook(backButton, !backStack.isEmpty());
        PixelUi.setEnabledLook(forwardButton, !forwardStack.isEmpty());
        PixelUi.setEnabledLook(upButton, !VirtualDisk.isRoot(root, current));
        updateTitle();
        updateStatus();
    }

    private void sortEntries() {
        Collections.sort(entries, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                boolean da = a.isDirectory();
                boolean db = b.isDirectory();
                if (da != db) return da ? -1 : 1;
                int r;
                switch (sortColumn) {
                    case COL_SIZE:
                        r = compareLong(da ? 0L : a.length(), db ? 0L : b.length());
                        break;
                    case COL_TYPE:
                        r = VirtualDisk.typeName(a).compareToIgnoreCase(VirtualDisk.typeName(b));
                        break;
                    case COL_DATE:
                        r = compareLong(a.lastModified(), b.lastModified());
                        break;
                    default:
                        r = a.getName().compareToIgnoreCase(b.getName());
                        break;
                }
                if (r == 0) r = a.getName().compareToIgnoreCase(b.getName());
                return sortAscending ? r : -r;
            }
        });
    }

    private static int compareLong(long x, long y) {
        return x < y ? -1 : (x == y ? 0 : 1);
    }

    private void sortBy(int column) {
        if (sortColumn == column) {
            setSort(column, !sortAscending);
        } else {
            setSort(column, true);
        }
    }

    private void setSort(int column, boolean ascending) {
        sortColumn = column;
        sortAscending = ascending;
        updateHeaderTitles();
        reload();
    }

    private void updateHeaderTitles() {
        for (int i = 0; i < 4; i++) {
            String t = COL_TITLES[i];
            if (i == sortColumn) t = t + (sortAscending ? "  \u25B2" : "  \u25BC");
            headerCells[i].setText(t);
        }
    }

    private void setSelected(int position) {
        selected = position;
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void selectFile(File f) {
        int idx = entries.indexOf(f);
        if (idx >= 0) {
            setSelected(idx);
            listView.setSelection(idx);
        }
    }

    private File getSelectedFile() {
        if (selected >= 0 && selected < entries.size()) return entries.get(selected);
        return null;
    }

    private void updateTitle() {
        if (hostWindow == null || current == null) return;
        hostWindow.setTitle("Explorando - " + VirtualDisk.toDisplayPath(root, current));
        if (engine != null) engine.refreshTaskbar();
    }

    private void updateStatus() {
        if (statusLeft == null) return;
        File sel = getSelectedFile();
        if (sel != null) {
            statusLeft.setText("1 objeto(s) selecionado(s)");
            statusRight.setText(sel.isDirectory() ? "" : VirtualDisk.formatHuman(sel.length()));
        } else {
            long total = 0;
            for (int i = 0; i < entries.size(); i++) {
                File f = entries.get(i);
                if (!f.isDirectory()) total += f.length();
            }
            statusLeft.setText(entries.size() + " objeto(s)");
            statusRight.setText(VirtualDisk.formatHuman(total));
        }
    }

    private static void notifyAllChanged() {
        List<FileManagerView> copy = new ArrayList<FileManagerView>(sInstances);
        for (int i = 0; i < copy.size(); i++) {
            FileManagerView v = copy.get(i);
            if (v.current != null && !v.current.exists()) {
                v.current = v.root;
            }
            v.reload();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!sInstances.contains(this)) sInstances.add(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable);
        sInstances.remove(this);
        super.onDetachedFromWindow();
    }

    private int dp(float v) {
        return PixelUi.dp(getContext(), v);
    }

    // ------------------------------------------------------------------ Adaptador (linhas da tabela)

    private static class RowHolder {
        LinearLayout row;
        PixelIconView icon;
        TextView name;
        TextView size;
        TextView type;
        TextView date;
    }

    private class FileAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Object getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowHolder h;
            if (convertView == null) {
                Context c = getContext();
                h = new RowHolder();
                h.row = new LinearLayout(c);
                h.row.setOrientation(HORIZONTAL);
                h.row.setGravity(Gravity.CENTER_VERTICAL);
                h.row.setLayoutParams(new AbsListViewParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

                LinearLayout nameCell = new LinearLayout(c);
                nameCell.setOrientation(HORIZONTAL);
                nameCell.setGravity(Gravity.CENTER_VERTICAL);
                nameCell.setPadding(dp(4), 0, dp(4), 0);
                h.icon = new PixelIconView(c, PixelIconView.TYPE_FILE);
                nameCell.addView(h.icon, new LayoutParams(dp(16), dp(16)));
                h.name = PixelUi.label(c, "");
                LayoutParams nlp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                nlp.leftMargin = dp(5);
                nameCell.addView(h.name, nlp);
                h.row.addView(nameCell, new LayoutParams(dp(COL_WIDTH_DP[COL_NAME]), ViewGroup.LayoutParams.MATCH_PARENT));

                h.size = cell(c, Gravity.RIGHT);
                h.row.addView(h.size, new LayoutParams(dp(COL_WIDTH_DP[COL_SIZE]), ViewGroup.LayoutParams.MATCH_PARENT));
                h.type = cell(c, Gravity.LEFT);
                h.row.addView(h.type, new LayoutParams(dp(COL_WIDTH_DP[COL_TYPE]), ViewGroup.LayoutParams.MATCH_PARENT));
                h.date = cell(c, Gravity.LEFT);
                h.row.addView(h.date, new LayoutParams(dp(COL_WIDTH_DP[COL_DATE]), ViewGroup.LayoutParams.MATCH_PARENT));

                h.row.setTag(h);
                convertView = h.row;
            } else {
                h = (RowHolder) convertView.getTag();
            }

            File f = entries.get(position);
            boolean isSel = position == selected;
            h.icon.setType(VirtualDisk.iconFor(f));
            h.name.setText(f.getName());
            h.size.setText(f.isDirectory() ? "" : VirtualDisk.formatKb(f.length()));
            h.type.setText(VirtualDisk.typeName(f));
            h.date.setText(VirtualDisk.formatDate(f.lastModified()));

            int fg = isSel ? PixelUi.SELECTION_TEXT : PixelUi.TEXT;
            h.row.setBackgroundColor(isSel ? PixelUi.SELECTION : PixelUi.WINDOW);
            h.name.setTextColor(fg);
            h.size.setTextColor(fg);
            h.type.setTextColor(fg);
            h.date.setTextColor(fg);
            return convertView;
        }

        private TextView cell(Context c, int gravity) {
            TextView t = PixelUi.label(c, "");
            t.setGravity(Gravity.CENTER_VERTICAL | gravity);
            t.setPadding(dp(6), 0, dp(6), 0);
            return t;
        }
    }

    /** LayoutParams exigido pelas linhas de ListView. */
    private static class AbsListViewParams extends android.widget.AbsListView.LayoutParams {
        AbsListViewParams(int w, int h) {
            super(w, h);
        }
    }
}
