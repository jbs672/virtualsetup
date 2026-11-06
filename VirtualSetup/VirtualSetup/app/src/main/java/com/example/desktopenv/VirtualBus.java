package com.example.desktopenv;

/** Barramento simples de E/S por portas de 8 bits. */
public class VirtualBus {
    public interface IoListener {
        void onIoWrite(int address, byte value);
        byte onIoRead(int address);
    }

    private IoListener listener;
    private final byte[] ports = new byte[256];
    private long reads;
    private long writes;

    public synchronized void setIoListener(IoListener l) { listener = l; }

    public synchronized void write(int addr, byte val) {
        int port = addr & 255;
        ports[port] = val;
        writes++;
        if (listener != null) listener.onIoWrite(port, val);
    }

    public synchronized byte read(int addr) {
        int port = addr & 255;
        reads++;
        if (listener != null) return listener.onIoRead(port);
        return ports[port];
    }

    public synchronized long getReadCount() { return reads; }
    public synchronized long getWriteCount() { return writes; }
    public synchronized void reset() {
        int i;
        for (i = 0; i < ports.length; i++) ports[i] = 0;
        reads = 0;
        writes = 0;
    }
}
