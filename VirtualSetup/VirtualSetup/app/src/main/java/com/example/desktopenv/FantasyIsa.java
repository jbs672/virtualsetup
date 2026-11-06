package com.example.desktopenv;

/**
 * Conjunto de instrucoes oficial da FantasyISA.
 * Todas as instrucoes sao codificadas em bytes e usam enderecos de 16 bits.
 * Registradores validos: R0 a R7.
 */
public final class FantasyIsa {
    private FantasyIsa() { }

    public static final byte NOP   = 0x00;
    public static final byte LDI   = 0x01; // LDI reg, imediato16
    public static final byte MOV   = 0x02; // MOV destino, origem
    public static final byte LOAD  = 0x03; // LOAD reg, endereco16
    public static final byte STORE = 0x04; // STORE reg, endereco16
    public static final byte ADD   = 0x05; // ADD destino, origem
    public static final byte ADDI  = 0x06; // ADDI reg, imediato8
    public static final byte SUB   = 0x07; // SUB destino, origem
    public static final byte SUBI  = 0x08; // SUBI reg, imediato8
    public static final byte MUL   = 0x09; // MUL destino, origem
    public static final byte DIV   = 0x0A; // DIV destino, origem
    public static final byte INC   = 0x0B; // INC reg
    public static final byte DEC   = 0x0C; // DEC reg
    public static final byte AND   = 0x0D; // AND destino, origem
    public static final byte OR    = 0x0E; // OR destino, origem
    public static final byte XOR   = 0x0F; // XOR destino, origem
    public static final byte NOT   = 0x10; // NOT reg
    public static final byte SHL   = 0x11; // SHL reg
    public static final byte SHR   = 0x12; // SHR reg
    public static final byte CMP   = 0x13; // CMP reg, reg
    public static final byte JMP   = 0x14; // JMP endereco16
    public static final byte JZ    = 0x15; // JZ endereco16
    public static final byte JNZ   = 0x16; // JNZ endereco16
    public static final byte JC    = 0x17; // JC endereco16
    public static final byte JN    = 0x18; // JN endereco16
    public static final byte PUSH  = 0x19; // PUSH reg
    public static final byte POP   = 0x1A; // POP reg
    public static final byte CALL  = 0x1B; // CALL endereco16
    public static final byte RET   = 0x1C; // RET
    public static final byte OUT   = 0x1D; // OUT porta8, reg
    public static final byte IN    = 0x1E; // IN reg, porta8
    public static final byte HALT  = (byte) 0xFF;

    public static String name(byte opcode) {
        switch (opcode & 255) {
            case 0x00: return "NOP";
            case 0x01: return "LDI";
            case 0x02: return "MOV";
            case 0x03: return "LOAD";
            case 0x04: return "STORE";
            case 0x05: return "ADD";
            case 0x06: return "ADDI";
            case 0x07: return "SUB";
            case 0x08: return "SUBI";
            case 0x09: return "MUL";
            case 0x0A: return "DIV";
            case 0x0B: return "INC";
            case 0x0C: return "DEC";
            case 0x0D: return "AND";
            case 0x0E: return "OR";
            case 0x0F: return "XOR";
            case 0x10: return "NOT";
            case 0x11: return "SHL";
            case 0x12: return "SHR";
            case 0x13: return "CMP";
            case 0x14: return "JMP";
            case 0x15: return "JZ";
            case 0x16: return "JNZ";
            case 0x17: return "JC";
            case 0x18: return "JN";
            case 0x19: return "PUSH";
            case 0x1A: return "POP";
            case 0x1B: return "CALL";
            case 0x1C: return "RET";
            case 0x1D: return "OUT";
            case 0x1E: return "IN";
            case 0xFF: return "HALT";
            default: return "INVALID";
        }
    }
}
