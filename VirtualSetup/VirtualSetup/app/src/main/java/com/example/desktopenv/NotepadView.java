package com.example.desktopenv;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bloco de Notas classico (texto puro), com menus customizados e dialogos proprios. */
public class NotepadView extends LinearLayout {

    private static final int M_NEW = 1, M_SAVE = 2, M_SAVE_AS = 3, M_EXIT = 4,
            M_SELECT_ALL = 5, M_TIME_DATE = 6, M_CLEAR = 7, M_ABOUT = 8;

    private final DesktopCoreEngine engine;
    private File file;
    private DesktopWindow host;
    private final EditText editor;
    private boolean dirty;
    private boolean loading;

    public static DesktopWindow open(DesktopCoreEngine engine, File file) {
        Context c = engine.getContext();
        NotepadView v = new NotepadView(c, engine, file);
        DesktopWindow w = new DesktopWindow(c, v.buildTitle(), PixelIconView.TYPE_NOTEPAD);
        w.setContent(v);
        v.attachHost(w);
        engine.addWindow(w, 400, 320);
        return w;
    }

    public NotepadView(Context context, DesktopCoreEngine engine, File file) {
        super(context);
        this.engine = engine;
        this.file = file;
        setOrientation(VERTICAL);
        setBackgroundColor(PixelUi.FACE);

        MenuBarView bar = new MenuBarView(context, engine);
        final ContextMenuView.OnItemSelectedListener handler = new ContextMenuView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(int id) {
                handleMenu(id);
            }
        };
        bar.addMenu("Arquivo", new MenuBarView.MenuProvider() {
            @Override
            public void onBuildMenu(ContextMenuView m) {
                m.addItem(M_NEW, "Novo");
                m.addItem(M_SAVE, "Salvar");
                m.addItem(M_SAVE_AS, "Salvar como...");
                m.addSeparator();
                m.addItem(M_EXIT, "Sair");
                m.setOnItemSelectedListener(handler);
            }
        });
        bar.addMenu("Editar", new MenuBarView.MenuProvider() {
            @Override
            public void onBuildMenu(ContextMenuView m) {
                m.addItem(M_SELECT_ALL, "Selecionar tudo");
                m.addItem(M_TIME_DATE, "Hora/Data");
                m.addSeparator();
                m.addItem(M_CLEAR, "Limpar tudo");
                m.setOnItemSelectedListener(handler);
            }
        });
        bar.addMenu("Ajuda", new MenuBarView.MenuProvider() {
            @Override
            public void onBuildMenu(ContextMenuView m) {
                m.addItem(M_ABOUT, "Sobre o Bloco de Notas");
                m.setOnItemSelectedListener(handler);
            }
        });
        addView(bar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(PixelUi.etched(context, true), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, PixelUi.dp(context, 2)));

        editor = PixelUi.textField(context, false);
        editor.setTypeface(PixelUi.FONT_MONO);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        editor.setHorizontallyScrolling(false);
        LayoutParams elp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        int m = PixelUi.dp(context, 2);
        elp.setMargins(m, m, m, m * 8);
        addView(editor, elp);

        if (file != null) {
            String text = VirtualDisk.readText(file);
            loading = true;
            editor.setText(text == null ? "" : text);
            loading = false;
        }
        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!loading && !dirty) {
                    dirty = true;
                    updateTitle();
                }
            }
        });
    }

    private void attachHost(DesktopWindow w) {
        host = w;
        w.setCloseHandler(new DesktopWindow.CloseHandler() {
            @Override
            public boolean onCloseRequested(DesktopWindow window) {
                if (!dirty) return true;
                askSaveThen(new Runnable() {
                    @Override
                    public void run() {
                        engine.closeWindow(host);
                    }
                });
                return false;
            }
        });
    }

    private String buildTitle() {
        String name = file != null ? file.getName() : "Sem t\u00edtulo";
        return (dirty ? "*" : "") + name + " - Bloco de Notas";
    }

    private void updateTitle() {
        if (host != null) {
            host.setTitle(buildTitle());
            engine.refreshTaskbar();
        }
    }

    private void handleMenu(int id) {
        switch (id) {
            case M_NEW:
                if (dirty) {
                    askSaveThen(new Runnable() {
                        @Override
                        public void run() {
                            resetDocument();
                        }
                    });
                } else {
                    resetDocument();
                }
                break;
            case M_SAVE:
                save(null);
                break;
            case M_SAVE_AS:
                saveAs(null);
                break;
            case M_EXIT:
                if (host != null) host.requestClose();
                break;
            case M_SELECT_ALL:
                editor.requestFocus();
                editor.selectAll();
                break;
            case M_TIME_DATE:
                String stamp = new SimpleDateFormat("HH:mm dd/MM/yyyy", new Locale("pt", "BR")).format(new Date());
                int pos = Math.max(0, editor.getSelectionStart());
                editor.getText().insert(pos, stamp);
                break;
            case M_CLEAR:
                editor.setText("");
                break;
            case M_ABOUT:
                engine.showMessageBox("Sobre o Bloco de Notas", "Bloco de Notas - Virtual Setup 0.0.13",
                        PixelIconView.TYPE_INFO);
                break;
            default:
                break;
        }
    }

    private void resetDocument() {
        loading = true;
        editor.setText("");
        loading = false;
        file = null;
        dirty = false;
        updateTitle();
    }

    private void askSaveThen(final Runnable after) {
        String name = file != null ? file.getName() : "Sem t\u00edtulo";
        engine.showDialog("Bloco de Notas", "O texto do arquivo " + name + " foi alterado.\n\nDeseja salvar as altera\u00e7\u00f5es?",
                PixelIconView.TYPE_WARNING, new String[] { "Sim", "N\u00e3o", "Cancelar" },
                new DesktopCoreEngine.DialogCallback() {
                    @Override
                    public void onButton(int index) {
                        if (index == 0) {
                            save(after);
                        } else if (index == 1) {
                            dirty = false;
                            if (after != null) after.run();
                        }
                    }
                });
    }

    private void save(Runnable after) {
        if (file == null) {
            saveAs(after);
            return;
        }
        if (VirtualDisk.writeText(file, editor.getText().toString())) {
            dirty = false;
            updateTitle();
            if (after != null) after.run();
        } else {
            engine.showMessageBox("Erro", "N\u00e3o foi poss\u00edvel salvar o arquivo.", PixelIconView.TYPE_ERROR);
        }
    }

    private void saveAs(final Runnable after) {
        final File docs = new File(VirtualDisk.getRoot(getContext()), "Documentos");
        if (!docs.exists()) docs.mkdirs();
        String def = file != null ? file.getName() : "Sem t\u00edtulo.txt";
        engine.showInputBox("Salvar como", "Nome do arquivo (em C:\\Documentos):", def,
                new DesktopCoreEngine.InputCallback() {
                    @Override
                    public void onInput(String value) {
                        if (!VirtualDisk.isValidName(value)) {
                            engine.showMessageBox("Erro", "Nome de arquivo inv\u00e1lido.", PixelIconView.TYPE_ERROR);
                            return;
                        }
                        String name = value.trim();
                        if (VirtualDisk.extension(name).length() == 0) name = name + ".txt";
                        File target = (file != null && file.getParentFile() != null) ? new File(file.getParentFile(), name)
                                : new File(docs, name);
                        file = target;
                        save(after);
                    }
                });
    }
}
