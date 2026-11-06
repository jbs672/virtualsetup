package com.example.desktopenv;

/** Relogio logico da maquina virtual. */
public class VirtualClock {
    private boolean running;
    private long ticks;

    public synchronized void tick() {
        if (running && ticks < Long.MAX_VALUE) ticks++;
    }

    public synchronized void start() { running = true; }
    public synchronized void stop() { running = false; }
    public synchronized boolean isRunning() { return running; }
    public synchronized void reset() { ticks = 0; running = false; }
    public synchronized long getTicks() { return ticks; }
}
