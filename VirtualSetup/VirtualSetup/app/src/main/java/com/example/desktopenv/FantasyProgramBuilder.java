package com.example.desktopenv;

import java.io.ByteArrayOutputStream;

/** Montador binario minimo e encadeavel para a FantasyISA. */
public class FantasyProgramBuilder {
    private final String name;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public FantasyProgramBuilder(String name) { this.name = name; }

    public FantasyProgramBuilder addInstruction(byte opcode) {
        buffer.write(opcode & 255);
        return this;
    }

    public FantasyProgramBuilder addByte(int value) {
        buffer.write(value & 255);
        return this;
    }

    public FantasyProgramBuilder addWord(int value) {
        buffer.write(value & 255);
        buffer.write((value >> 8) & 255);
        return this;
    }

    public FantasyProgramBuilder ldi(int register, int value) {
        return addInstruction(FantasyIsa.LDI).addByte(register).addWord(value);
    }

    public FantasyProgramBuilder mov(int destination, int source) {
        return addInstruction(FantasyIsa.MOV).addByte(destination).addByte(source);
    }

    public FantasyProgramBuilder add(int destination, int source) {
        return addInstruction(FantasyIsa.ADD).addByte(destination).addByte(source);
    }

    public FantasyProgramBuilder addi(int register, int value) {
        return addInstruction(FantasyIsa.ADDI).addByte(register).addByte(value);
    }

    public FantasyProgramBuilder jump(int address) {
        return addInstruction(FantasyIsa.JMP).addWord(address);
    }

    public FantasyProgramBuilder jumpZero(int address) {
        return addInstruction(FantasyIsa.JZ).addWord(address);
    }

    public FantasyProgramBuilder halt() {
        return addInstruction(FantasyIsa.HALT);
    }

    public int size() { return buffer.size(); }
    public FantasyProgram build() { return new FantasyProgram(name, buffer.toByteArray()); }
}
