package com.example.desktopenv;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Disco virtual "C:\" mapeado para a pasta interna do app (getFilesDir()/drive_c).
 * Nao exige permissoes de armazenamento. Contem utilitarios de arquivo em Java puro.
 */
public final class VirtualDisk {

    public static final String DRIVE = "C:";
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static File sRoot;

    private VirtualDisk() {
    }

    public static File getRoot(Context context) {
        if (sRoot == null) {
            sRoot = new File(context.getFilesDir(), "drive_c");
            if (!sRoot.exists()) {
                sRoot.mkdirs();
                createSampleContent(sRoot);
            }
        }
        return sRoot;
    }

    private static void createSampleContent(File root) {
        File docs = new File(root, "Documentos");
        File images = new File(root, "Imagens");
        File programs = new File(root, "Programas");
        File fantasy = new File(programs, "Fantasy PC");
        File system = new File(root, "Sistema");
        docs.mkdirs();
        images.mkdirs();
        fantasy.mkdirs();
        system.mkdirs();

        writeText(new File(docs, "Leia-me.txt"),
                "Bem-vindo ao Virtual Setup 0.0.14\r\n\r\n"
                        + "- Toque duas vezes rapidamente para abrir pastas e arquivos.\r\n"
                        + "- Mantenha o dedo parado por 1 segundo na area de trabalho para abrir o menu.\r\n"
                        + "- Arraste as janelas pela barra de titulo azul.\r\n");
        writeText(new File(docs, "Notas.txt"), "Lista de tarefas:\r\n[ ] Testar o Fantasy PC\r\n");
        writeText(new File(system, "config.ini"),
                "[Desktop]\r\nWallpaper=0\r\nTheme=Classic\r\n\r\n[FantasyPC]\r\nRAM=65536\r\nDisplay=128x128\r\n");
        writeText(new File(system, "boot.log"), "POST OK\r\nCPU FantasyISA OK\r\nRAM 64 KB OK\r\n");

        FantasyProgramBuilder demoBuilder = new FantasyProgramBuilder("demo");
        demoBuilder.ldi(0, 5);
        demoBuilder.ldi(1, 7);
        demoBuilder.add(0, 1);
        demoBuilder.addInstruction(FantasyIsa.STORE).addByte(0).addWord(0x0200);
        demoBuilder.halt();
        FantasyProgram demo = demoBuilder.build();
        writeBytes(new File(fantasy, "demo.fpc"), demo.getCode());

        writeText(new File(fantasy, "Exemplo.fpc"),
                "FANTASY_APP 1\n"
                        + "# Aplicativo de exemplo em texto puro\n"
                        + "TITLE|Meu primeiro aplicativo\n"
                        + "SIZE|340|300\n"
                        + "BACKGROUND|#ECE9D8\n"
                        + "LABEL|welcome|Bem-vindo ao Fantasy PC!\n"
                        + "LABEL|score|0\n"
                        + "INPUT|player|Digite seu nome\n"
                        + "BUTTON|hello|Saudar|MESSAGE_INPUT|player|Saudacao|Ola, \n"
                        + "BUTTON|count|Somar ponto|INCREMENT|score|1\n"
                        + "BUTTON|random|Numero aleatorio|RANDOM|score|1|100\n"
                        + "BUTTON|change|Alterar mensagem|SET_TEXT|welcome|Voce criou seu primeiro programa!\n"
                        + "BUTTON|exit|Fechar|CLOSE\n");
    }

    // ------------------------------------------------------------------ Caminhos

    public static boolean isInside(File root, File f) {
        try {
            String r = root.getCanonicalPath();
            String p = f.getCanonicalPath();
            return p.equals(r) || p.startsWith(r + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isRoot(File root, File f) {
        try {
            return root.getCanonicalPath().equals(f.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    /** Ex.: /data/.../drive_c/Documentos  =>  C:\Documentos */
    public static String toDisplayPath(File root, File f) {
        String r = root.getAbsolutePath();
        String p = f.getAbsolutePath();
        if (!p.startsWith(r)) return DRIVE + "\\";
        String rel = p.substring(r.length()).replace('/', '\\');
        if (rel.length() == 0) return DRIVE + "\\";
        return DRIVE + rel;
    }

    /** Converte o texto da barra de endereco em File (ou null se invalido / fora do disco). */
    public static File resolve(File root, File current, String input) {
        if (input == null) return null;
        String text = input.trim().replace('/', '\\');
        if (text.length() == 0) return null;
        File base;
        if (text.length() >= 2 && text.substring(0, 2).equalsIgnoreCase(DRIVE)) {
            text = text.substring(2);
            base = root;
        } else if (text.startsWith("\\")) {
            base = root;
        } else {
            base = current != null ? current : root;
        }
        String[] parts = text.split("\\\\");
        File result = base;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.length() == 0 || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!isRoot(root, result)) result = result.getParentFile();
                continue;
            }
            result = new File(result, part);
        }
        if (result == null || !isInside(root, result)) return null;
        return result;
    }

    public static boolean isValidName(String name) {
        if (name == null) return false;
        String n = name.trim();
        if (n.length() == 0 || n.equals(".") || n.equals("..")) return false;
        String invalid = "\\/:*?\"<>|";
        for (int i = 0; i < n.length(); i++) {
            if (invalid.indexOf(n.charAt(i)) >= 0) return false;
        }
        return true;
    }

    public static String uniqueName(File dir, String base, String ext) {
        String name = base + ext;
        int i = 2;
        while (new File(dir, name).exists()) {
            name = base + " (" + i + ")" + ext;
            i++;
        }
        return name;
    }

    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    public static boolean isTextFile(File f) {
        String ext = extension(f.getName());
        return ext.equals("txt") || ext.equals("ini") || ext.equals("log") || ext.equals("cfg")
                || ext.equals("md") || ext.equals("bat") || ext.equals("csv");
    }

    // ------------------------------------------------------------------ Descricao

    public static String typeName(File f) {
        if (f.isDirectory()) return "Pasta de arquivos";
        String ext = extension(f.getName());
        if (ext.equals("txt")) return "Documento de texto";
        if (ext.equals("ini") || ext.equals("cfg")) return "Defini\u00e7\u00f5es de configura\u00e7\u00e3o";
        if (ext.equals("log")) return "Arquivo de log";
        if (ext.equals("fpc")) return "Programa Fantasy PC";
        if (ext.equals("png") || ext.equals("jpg") || ext.equals("bmp") || ext.equals("gif")) return "Imagem";
        if (ext.length() == 0) return "Arquivo";
        return "Arquivo " + ext.toUpperCase(Locale.US);
    }

    public static int iconFor(File f) {
        if (f.isDirectory()) return PixelIconView.TYPE_FOLDER;
        String ext = extension(f.getName());
        if (ext.equals("txt") || ext.equals("log") || ext.equals("md")) return PixelIconView.TYPE_DOCUMENT;
        if (ext.equals("ini") || ext.equals("cfg")) return PixelIconView.TYPE_NOTEPAD;
        if (ext.equals("fpc")) return PixelIconView.TYPE_PROGRAM;
        return PixelIconView.TYPE_FILE;
    }

    /** Coluna "Tamanho" do Explorer: KB arredondado para cima. */
    public static String formatKb(long bytes) {
        long kb = (bytes + 1023L) / 1024L;
        DecimalFormat df = new DecimalFormat("#,##0", new DecimalFormatSymbols(PT_BR));
        return df.format(kb) + " KB";
    }

    public static String formatHuman(long bytes) {
        DecimalFormat df = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(PT_BR));
        if (bytes < 1024L) return bytes + " bytes";
        if (bytes < 1024L * 1024L) return df.format(bytes / 1024.0) + " KB";
        return df.format(bytes / (1024.0 * 1024.0)) + " MB";
    }

    public static String formatDate(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy HH:mm", PT_BR);
        return f.format(new Date(millis));
    }

    public static long sizeOf(File f) {
        if (!f.isDirectory()) return f.length();
        long total = 0;
        File[] children = f.listFiles();
        if (children != null) {
            for (int i = 0; i < children.length; i++) total += sizeOf(children[i]);
        }
        return total;
    }

    // ------------------------------------------------------------------ E/S

    public static String readText(File f) {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        } catch (IOException e) {
            return null;
        } finally {
            closeQuietly(reader);
        }
        return sb.toString();
    }

    public static boolean writeText(File f, String text) {
        Writer w = null;
        try {
            w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            w.write(text);
            w.flush();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            closeQuietly(w);
        }
    }

    public static boolean writeBytes(File f, byte[] data) {
        OutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(data);
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            closeQuietly(out);
        }
    }

    public static boolean copyRecursive(File src, File dst) {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) return false;
            File[] children = src.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    if (!copyRecursive(children[i], new File(dst, children[i].getName()))) return false;
                }
            }
            return true;
        }
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    public static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteRecursive(children[i]);
                }
            }
        }
        return f.delete();
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
            // sem acao
        }
    }
}
