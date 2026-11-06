package com.example.desktopenv;

/** Programa compilado para a FantasyISA. */
public class FantasyProgram {
    private final String name;
    private final byte[] code;

    public FantasyProgram(String name, byte[] code) {
        if (code == null) throw new IllegalArgumentException("Codigo nulo.");
        this.name = name == null ? "Sem nome" : name;
        this.code = new byte[code.length];
        System.arraycopy(code, 0, this.code, 0, code.length);
    }

    public String getName() { return name; }
    public byte[] getCode() {
        byte[] copy = new byte[code.length];
        System.arraycopy(code, 0, copy, 0, code.length);
        return copy;
    }
    public int getSize() { return code.length; }
}
