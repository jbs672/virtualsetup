package com.example.desktopenv;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Runtime de aplicativos em texto do Fantasy PC.
 *
 * Formato: arquivo UTF-8 .fpc iniciado por FANTASY_APP 1.
 * Interpreta uma lista limitada de componentes e acoes; nao executa Java,
 * bytecode Android, comandos do sistema ou codigo nativo.
 */
public final class FantasyScriptView {

    private static final int MAX_FILE_BYTES = 65536;
    private static final int MAX_LINES = 256;
    private static final int MAX_COMPONENTS = 96;

    private final DesktopCoreEngine engine;
    private final DesktopWindow window;
    private final Map<String, TextView> labels = new HashMap<String, TextView>();
    private final Map<String, EditText> inputs = new HashMap<String, EditText>();
    private final Random random = new Random();

    private FantasyScriptView(DesktopCoreEngine engine, String title, int width, int height,
                              int background, List<String[]> commands) {
        this.engine = engine;
        Context context = engine.getContext();
        window = new DesktopWindow(context, title, PixelIconView.TYPE_APP);
        window.setResizable(true);
        window.setMinimizable(true);
        window.setMaximizable(true);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        root.setBackgroundColor(background);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        for (int i = 0; i < commands.size(); i++) {
            addCommand(context, body, commands.get(i));
        }
        window.setContent(root);
        engine.addWindow(window, width, height);
    }

    public static boolean isScriptFile(File file) {
        if (file == null || !file.isFile() || file.length() > MAX_FILE_BYTES) return false;
        String content = VirtualDisk.readText(file);
        if (content == null) return false;
        String[] lines = content.split("\\r?\\n", 3);
        return lines.length > 0 && "FANTASY_APP 1".equals(lines[0].trim());
    }

    public static DesktopWindow open(DesktopCoreEngine engine, File file) {
        if (engine == null || file == null) return null;
        File root = VirtualDisk.getRoot(engine.getContext());
        if (!VirtualDisk.isInside(root, file)) {
            engine.showMessageBox("Fantasy PC", "O programa esta fora do Disco C:.", PixelIconView.TYPE_ERROR);
            return null;
        }
        if (!file.isFile() || file.length() <= 0 || file.length() > MAX_FILE_BYTES) {
            engine.showMessageBox("Fantasy PC", "Arquivo vazio ou maior que 64 KB.", PixelIconView.TYPE_ERROR);
            return null;
        }
        String source = VirtualDisk.readText(file);
        if (source == null) {
            engine.showMessageBox("Fantasy PC", "Nao foi possivel ler o programa.", PixelIconView.TYPE_ERROR);
            return null;
        }
        try {
            ProgramData data = parse(source, file.getName());
            FantasyScriptView runtime = new FantasyScriptView(engine, data.title, data.width,
                    data.height, data.background, data.commands);
            return runtime.window;
        } catch (IllegalArgumentException e) {
            engine.showMessageBox("Erro no programa Fantasy", e.getMessage(), PixelIconView.TYPE_ERROR);
            return null;
        }
    }

    private static ProgramData parse(String source, String fallbackTitle) {
        String[] lines = source.split("\\r?\\n");
        if (lines.length > MAX_LINES) throw new IllegalArgumentException("Limite de 256 linhas excedido.");
        if (lines.length == 0 || !"FANTASY_APP 1".equals(lines[0].trim())) {
            throw new IllegalArgumentException("Cabecalho ausente. A primeira linha deve ser FANTASY_APP 1.");
        }

        ProgramData data = new ProgramData();
        data.title = fallbackTitle;
        int componentCount = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0 || line.startsWith("#")) continue;
            String[] p = line.split("\\|", -1);
            String op = p[0].trim().toUpperCase(Locale.US);

            if ("TITLE".equals(op)) {
                require(p, 2, i);
                data.title = decode(p[1]);
            } else if ("SIZE".equals(op)) {
                require(p, 3, i);
                data.width = boundedInt(p[1], 220, 600, "largura");
                data.height = boundedInt(p[2], 160, 700, "altura");
            } else if ("BACKGROUND".equals(op)) {
                require(p, 2, i);
                data.background = parseColor(p[1]);
            } else if ("LABEL".equals(op) || "INPUT".equals(op) || "BUTTON".equals(op)) {
                componentCount++;
                if (componentCount > MAX_COMPONENTS) {
                    throw new IllegalArgumentException("Limite de 96 componentes excedido.");
                }
                if ("LABEL".equals(op)) require(p, 3, i);
                else if ("INPUT".equals(op)) require(p, 3, i);
                else require(p, 4, i);
                data.commands.add(p);
            } else {
                throw new IllegalArgumentException("Comando desconhecido na linha " + (i + 1) + ": " + op);
            }
        }
        if (data.title == null || data.title.trim().length() == 0) data.title = "Aplicativo Fantasy";
        return data;
    }

    private void addCommand(Context c, LinearLayout body, final String[] p) {
        String op = p[0].trim().toUpperCase(Locale.US);
        if ("LABEL".equals(op)) {
            final TextView label = new TextView(c);
            label.setText(decode(p[2]));
            label.setTextColor(Color.BLACK);
            label.setTextSize(14);
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setPadding(dp(c, 3), dp(c, 5), dp(c, 3), dp(c, 5));
            body.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            labels.put(p[1].trim(), label);
        } else if ("INPUT".equals(op)) {
            EditText input = new EditText(c);
            input.setSingleLine(true);
            input.setTextSize(14);
            input.setHint(decode(p[2]));
            input.setPadding(dp(c, 7), 0, dp(c, 7), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 42));
            lp.topMargin = dp(c, 5);
            lp.bottomMargin = dp(c, 5);
            body.addView(input, lp);
            inputs.put(p[1].trim(), input);
        } else if ("BUTTON".equals(op)) {
            TextView button = PixelUi.button(c, decode(p[2]), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    executeAction(p);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 36));
            lp.topMargin = dp(c, 4);
            lp.bottomMargin = dp(c, 4);
            body.addView(button, lp);
        }
    }

    private void executeAction(String[] p) {
        String action = p[3].trim().toUpperCase(Locale.US);
        try {
            if ("CLOSE".equals(action)) {
                engine.closeWindow(window);
            } else if ("MESSAGE".equals(action)) {
                if (p.length < 6) throw new IllegalArgumentException("MESSAGE requer titulo e mensagem.");
                engine.showMessageBox(decode(p[4]), decode(p[5]), PixelIconView.TYPE_INFO);
            } else if ("SET_TEXT".equals(action)) {
                if (p.length < 6) throw new IllegalArgumentException("SET_TEXT requer id e texto.");
                TextView target = labels.get(p[4].trim());
                if (target == null) throw new IllegalArgumentException("LABEL nao encontrado: " + p[4]);
                target.setText(decode(p[5]));
            } else if ("APPEND_TEXT".equals(action)) {
                if (p.length < 6) throw new IllegalArgumentException("APPEND_TEXT requer id e texto.");
                TextView target = labels.get(p[4].trim());
                if (target == null) throw new IllegalArgumentException("LABEL nao encontrado: " + p[4]);
                target.append(decode(p[5]));
            } else if ("INCREMENT".equals(action)) {
                if (p.length < 6) throw new IllegalArgumentException("INCREMENT requer id e valor.");
                TextView target = labels.get(p[4].trim());
                if (target == null) throw new IllegalArgumentException("LABEL nao encontrado: " + p[4]);
                int current = Integer.parseInt(target.getText().toString().trim());
                int amount = Integer.parseInt(p[5].trim());
                target.setText(String.valueOf(current + amount));
            } else if ("SET_FROM_INPUT".equals(action)) {
                if (p.length < 6) throw new IllegalArgumentException("SET_FROM_INPUT requer id do texto e id da entrada.");
                TextView target = labels.get(p[4].trim());
                EditText input = inputs.get(p[5].trim());
                if (target == null || input == null) {
                    throw new IllegalArgumentException("Identificador de LABEL ou INPUT nao encontrado.");
                }
                target.setText(input.getText().toString());
            } else if ("MESSAGE_INPUT".equals(action)) {
                if (p.length < 6) throw new IllegalArgumentException("MESSAGE_INPUT requer id da entrada, titulo e prefixo.");
                EditText input = inputs.get(p[4].trim());
                if (input == null) throw new IllegalArgumentException("INPUT nao encontrado: " + p[4]);
                engine.showMessageBox(decode(p[5]), decode(p.length > 6 ? p[6] : "") + input.getText(),
                        PixelIconView.TYPE_INFO);
            } else if ("RANDOM".equals(action)) {
                if (p.length < 7) throw new IllegalArgumentException("RANDOM requer id, minimo e maximo.");
                TextView target = labels.get(p[4].trim());
                if (target == null) throw new IllegalArgumentException("LABEL nao encontrado: " + p[4]);
                int min = Integer.parseInt(p[5].trim());
                int max = Integer.parseInt(p[6].trim());
                if (max < min || max - min > 1000000) throw new IllegalArgumentException("Intervalo RANDOM invalido.");
                target.setText(String.valueOf(min + random.nextInt(max - min + 1)));
            } else {
                throw new IllegalArgumentException("Acao desconhecida: " + action);
            }
        } catch (RuntimeException e) {
            engine.showMessageBox("Erro de execucao", e.getMessage() == null ? "Acao invalida." : e.getMessage(),
                    PixelIconView.TYPE_ERROR);
        }
    }

    private static void require(String[] p, int count, int line) {
        if (p.length < count) throw new IllegalArgumentException("Parametros insuficientes na linha " + (line + 1) + ".");
    }

    private static int boundedInt(String value, int min, int max, String field) {
        try {
            int n = Integer.parseInt(value.trim());
            if (n < min || n > max) throw new IllegalArgumentException(field + " deve ficar entre " + min + " e " + max + ".");
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " invalida.");
        }
    }

    private static int parseColor(String value) {
        try {
            return Color.parseColor(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cor invalida: " + value);
        }
    }

    private static String decode(String value) {
        return value.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }

    private static int dp(Context c, float value) {
        return PixelUi.dp(c, value);
    }

    private static final class ProgramData {
        String title = "Aplicativo Fantasy";
        int width = 340;
        int height = 300;
        int background = Color.rgb(236, 233, 216);
        final List<String[]> commands = new ArrayList<String[]>();
    }

    private FantasyScriptView() {
        throw new AssertionError();
    }
}
