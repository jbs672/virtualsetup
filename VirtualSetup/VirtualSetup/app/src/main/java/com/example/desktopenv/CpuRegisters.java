package com.example.desktopenv;

/** Banco de registradores da FantasyISA (valores de 16 bits). */
public class CpuRegisters {
    public int pc;
    public int sp;
    public int r0, r1, r2, r3, r4, r5, r6, r7;
    public boolean flagZero;
    public boolean flagCarry;
    public boolean flagNegative;
    public boolean flagOverflow;

    public int get(int index) {
        switch (index) {
            case 0: return r0 & 65535;
            case 1: return r1 & 65535;
            case 2: return r2 & 65535;
            case 3: return r3 & 65535;
            case 4: return r4 & 65535;
            case 5: return r5 & 65535;
            case 6: return r6 & 65535;
            case 7: return r7 & 65535;
            default: throw new IllegalArgumentException("Registrador invalido: " + index);
        }
    }

    public void set(int index, int value) {
        value &= 65535;
        switch (index) {
            case 0: r0 = value; break;
            case 1: r1 = value; break;
            case 2: r2 = value; break;
            case 3: r3 = value; break;
            case 4: r4 = value; break;
            case 5: r5 = value; break;
            case 6: r6 = value; break;
            case 7: r7 = value; break;
            default: throw new IllegalArgumentException("Registrador invalido: " + index);
        }
    }

    public void updateZeroNegative(int value) {
        int v = value & 65535;
        flagZero = v == 0;
        flagNegative = (v & 32768) != 0;
    }

    public void reset() {
        pc = 0;
        sp = 65535;
        r0 = 0; r1 = 0; r2 = 0; r3 = 0;
        r4 = 0; r5 = 0; r6 = 0; r7 = 0;
        flagZero = false;
        flagCarry = false;
        flagNegative = false;
        flagOverflow = false;
    }
}
