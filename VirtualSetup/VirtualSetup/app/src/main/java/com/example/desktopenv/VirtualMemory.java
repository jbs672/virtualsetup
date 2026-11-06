package com.example.desktopenv;

/** Memoria linear de 64 KB com verificacao de limites e interface MMIO. */
public class VirtualMemory {
    public interface MmioListener {
        void onWrite(int address, byte value);
        byte onRead(int address);
    }

    private final byte[] memory;
    private MmioListener mmioListener;
    private long readCount;
    private long writeCount;

    public VirtualMemory(int size) {
        if (size < 1 || size > 65536) {
            throw new IllegalArgumentException("A memoria deve ter entre 1 e 65536 bytes.");
        }
        memory = new byte[size];
    }

    public synchronized void setMmioListener(MmioListener listener) {
        mmioListener = listener;
    }

    public int size() { return memory.length; }

    public synchronized byte readByte(int addr) {
        if (addr < 0 || addr > 65535) return 0;
        readCount++;
        if (addr >= memory.length) {
            return mmioListener == null ? 0 : mmioListener.onRead(addr);
        }
        return memory[addr];
    }

    public synchronized void writeByte(int addr, byte val) {
        if (addr < 0 || addr > 65535) return;
        writeCount++;
        if (addr >= memory.length) {
            if (mmioListener != null) mmioListener.onWrite(addr, val);
            return;
        }
        memory[addr] = val;
    }

    public synchronized int readWord(int addr) {
        int lo = readByte(addr) & 255;
        int hi = readByte(addr + 1) & 255;
        return lo | (hi << 8);
    }

    public synchronized void writeWord(int addr, int value) {
        writeByte(addr, (byte) value);
        writeByte(addr + 1, (byte) (value >> 8));
    }

    public synchronized void load(int address, byte[] data) {
        if (data == null) throw new IllegalArgumentException("Programa nulo.");
        if (address < 0 || address + data.length > memory.length) {
            throw new IndexOutOfBoundsException("Programa excede a memoria.");
        }
        System.arraycopy(data, 0, memory, address, data.length);
    }

    public synchronized void clear() {
        for (int i = 0; i < memory.length; i++) memory[i] = 0;
        readCount = 0;
        writeCount = 0;
    }

    public synchronized long getReadCount() { return readCount; }
    public synchronized long getWriteCount() { return writeCount; }
}
