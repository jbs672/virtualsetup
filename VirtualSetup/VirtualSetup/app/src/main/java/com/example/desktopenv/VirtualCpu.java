package com.example.desktopenv;

/**
 * CPU FantasyISA: ciclo fetch/decode/execute, 8 registradores de 16 bits,
 * flags, pilha descendente, E/S e contabilizacao de instrucoes.
 */
public class VirtualCpu {
    public interface CpuListener { void onCpuTick(); }

    private final CpuRegisters registers = new CpuRegisters();
    private final VirtualMemory memory;
    private final VirtualBus bus;
    private CpuListener listener;
    private boolean halted;
    private String lastFault;
    private long instructionCount;
    private long cycleCount;

    public VirtualCpu(VirtualMemory memory) {
        this(memory, new VirtualBus());
    }

    public VirtualCpu(VirtualMemory memory, VirtualBus bus) {
        if (memory == null) throw new IllegalArgumentException("Memoria obrigatoria.");
        this.memory = memory;
        this.bus = bus == null ? new VirtualBus() : bus;
        reset();
    }

    public void setCpuListener(CpuListener l) { listener = l; }
    public CpuRegisters getRegisters() { return registers; }
    public VirtualMemory getMemory() { return memory; }
    public VirtualBus getBus() { return bus; }
    public boolean isHalted() { return halted; }
    public String getLastFault() { return lastFault; }
    public long getInstructionCount() { return instructionCount; }
    public long getCycleCount() { return cycleCount; }

    public void reset() {
        registers.reset();
        halted = false;
        lastFault = null;
        instructionCount = 0;
        cycleCount = 0;
    }

    public void step() {
        if (halted) return;
        try {
            int op = fetch8();
            instructionCount++;
            cycleCount++;
            execute(op);
            if (listener != null) listener.onCpuTick();
        } catch (RuntimeException e) {
            halted = true;
            lastFault = e.getMessage() == null ? "Falha de execucao" : e.getMessage();
        }
    }

    public int runSteps(int maximum) {
        if (maximum < 0) throw new IllegalArgumentException("Quantidade negativa.");
        int executed = 0;
        while (executed < maximum && !halted) {
            long before = instructionCount;
            step();
            if (instructionCount > before) executed++;
            else break;
        }
        return executed;
    }

    private int fetch8() {
        int address = registers.pc & 65535;
        if (address >= memory.size()) throw new IllegalStateException("PC fora da memoria: " + hex(address));
        int value = memory.readByte(address) & 255;
        registers.pc = (address + 1) & 65535;
        return value;
    }

    private int fetch16() {
        int lo = fetch8();
        int hi = fetch8();
        return lo | (hi << 8);
    }

    private int fetchRegister() {
        int r = fetch8();
        if (r > 7) throw new IllegalStateException("Registrador invalido: " + r);
        return r;
    }

    private void execute(int op) {
        int a;
        int b;
        int value;
        int address;
        switch (op) {
            case 0x00:
                break;
            case 0x01:
                a = fetchRegister(); value = fetch16();
                registers.set(a, value); update(value);
                break;
            case 0x02:
                a = fetchRegister(); b = fetchRegister();
                value = registers.get(b); registers.set(a, value); update(value);
                break;
            case 0x03:
                a = fetchRegister(); address = fetch16();
                value = memory.readWord(address); registers.set(a, value); update(value);
                break;
            case 0x04:
                a = fetchRegister(); address = fetch16();
                memory.writeWord(address, registers.get(a));
                break;
            case 0x05:
                a = fetchRegister(); b = fetchRegister();
                arithmeticAdd(a, registers.get(a), registers.get(b));
                break;
            case 0x06:
                a = fetchRegister(); arithmeticAdd(a, registers.get(a), fetch8());
                break;
            case 0x07:
                a = fetchRegister(); b = fetchRegister();
                arithmeticSub(a, registers.get(a), registers.get(b));
                break;
            case 0x08:
                a = fetchRegister(); arithmeticSub(a, registers.get(a), fetch8());
                break;
            case 0x09:
                a = fetchRegister(); b = fetchRegister();
                value = registers.get(a) * registers.get(b);
                registers.set(a, value);
                registers.flagCarry = value > 65535;
                registers.flagOverflow = value > 65535;
                update(value);
                break;
            case 0x0A:
                a = fetchRegister(); b = fetchRegister();
                value = registers.get(b);
                if (value == 0) throw new ArithmeticException("Divisao por zero.");
                registers.set(a, registers.get(a) / value); registers.flagCarry = false;
                registers.flagOverflow = false; update(registers.get(a));
                break;
            case 0x0B:
                a = fetchRegister(); arithmeticAdd(a, registers.get(a), 1);
                break;
            case 0x0C:
                a = fetchRegister(); arithmeticSub(a, registers.get(a), 1);
                break;
            case 0x0D:
                a = fetchRegister(); b = fetchRegister();
                value = registers.get(a) & registers.get(b); registers.set(a, value);
                registers.flagCarry = false; registers.flagOverflow = false; update(value);
                break;
            case 0x0E:
                a = fetchRegister(); b = fetchRegister();
                value = registers.get(a) | registers.get(b); registers.set(a, value);
                registers.flagCarry = false; registers.flagOverflow = false; update(value);
                break;
            case 0x0F:
                a = fetchRegister(); b = fetchRegister();
                value = registers.get(a) ^ registers.get(b); registers.set(a, value);
                registers.flagCarry = false; registers.flagOverflow = false; update(value);
                break;
            case 0x10:
                a = fetchRegister(); value = (~registers.get(a)) & 65535;
                registers.set(a, value); registers.flagCarry = false;
                registers.flagOverflow = false; update(value);
                break;
            case 0x11:
                a = fetchRegister(); value = registers.get(a);
                registers.flagCarry = (value & 32768) != 0;
                value = (value << 1) & 65535; registers.set(a, value); update(value);
                break;
            case 0x12:
                a = fetchRegister(); value = registers.get(a);
                registers.flagCarry = (value & 1) != 0;
                value = value >>> 1; registers.set(a, value); registers.flagOverflow = false; update(value);
                break;
            case 0x13:
                a = fetchRegister(); b = fetchRegister();
                arithmeticSub(-1, registers.get(a), registers.get(b));
                break;
            case 0x14:
                registers.pc = fetch16();
                break;
            case 0x15:
                address = fetch16(); if (registers.flagZero) registers.pc = address;
                break;
            case 0x16:
                address = fetch16(); if (!registers.flagZero) registers.pc = address;
                break;
            case 0x17:
                address = fetch16(); if (registers.flagCarry) registers.pc = address;
                break;
            case 0x18:
                address = fetch16(); if (registers.flagNegative) registers.pc = address;
                break;
            case 0x19:
                a = fetchRegister(); push(registers.get(a));
                break;
            case 0x1A:
                a = fetchRegister(); value = pop(); registers.set(a, value); update(value);
                break;
            case 0x1B:
                address = fetch16(); push(registers.pc); registers.pc = address;
                break;
            case 0x1C:
                registers.pc = pop();
                break;
            case 0x1D:
                address = fetch8(); a = fetchRegister(); bus.write(address, (byte) registers.get(a));
                break;
            case 0x1E:
                a = fetchRegister(); address = fetch8();
                value = bus.read(address) & 255; registers.set(a, value); update(value);
                break;
            case 0xFF:
                halted = true;
                break;
            default:
                throw new IllegalStateException("Opcode invalido " + hex(op) + " em " + hex((registers.pc - 1) & 65535));
        }
    }

    private void arithmeticAdd(int destination, int left, int right) {
        int result = left + right;
        registers.flagCarry = result > 65535;
        registers.flagOverflow = (((left & 65535) ^ (result & 65535))
                & ((right & 65535) ^ (result & 65535)) & 32768) != 0;
        if (destination >= 0) registers.set(destination, result);
        update(result);
    }

    private void arithmeticSub(int destination, int left, int right) {
        int result = left - right;
        registers.flagCarry = left >= right;
        registers.flagOverflow = (((left & 65535) ^ (right & 65535))
                & ((left & 65535) ^ (result & 65535)) & 32768) != 0;
        if (destination >= 0) registers.set(destination, result);
        update(result);
    }

    private void update(int value) { registers.updateZeroNegative(value); }

    private void push(int value) {
        if (registers.sp < 2) throw new IllegalStateException("Pilha cheia.");
        registers.sp = (registers.sp - 2) & 65535;
        if (registers.sp + 1 >= memory.size()) throw new IllegalStateException("Pilha fora da memoria.");
        memory.writeWord(registers.sp, value);
    }

    private int pop() {
        if (registers.sp < 0 || registers.sp + 1 >= memory.size()) {
            throw new IllegalStateException("Pilha vazia ou invalida.");
        }
        int value = memory.readWord(registers.sp);
        registers.sp = (registers.sp + 2) & 65535;
        return value;
    }

    private String hex(int value) {
        String s = Integer.toHexString(value & 65535).toUpperCase();
        while (s.length() < 4) s = "0" + s;
        return "0x" + s;
    }
}
