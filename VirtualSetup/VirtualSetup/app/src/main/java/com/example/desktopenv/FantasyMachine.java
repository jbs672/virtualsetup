package com.example.desktopenv;

/**
 * Placa-mae virtual: CPU, 64 KB RAM, barramento de E/S, relogio e video 128x128.
 * O metodo run() executa uma instrucao por chamada; a cadencia e controlada pela UI.
 */
public class FantasyMachine {
    private final VirtualMemory memory;
    private final VirtualBus bus;
    private final VirtualCpu cpu;
    private final VirtualFramebuffer framebuffer;
    private final VirtualClock clock;
    private long frame;
    private int videoX;
    private int videoY;
    private int videoR;
    private int videoG;
    private int videoB;
    private boolean videoTouched;

    public FantasyMachine() {
        memory = new VirtualMemory(65536);
        bus = new VirtualBus();
        cpu = new VirtualCpu(memory, bus);
        framebuffer = new VirtualFramebuffer(128, 128);
        clock = new VirtualClock();
        configureDevices();
        reset();
    }

    private void configureDevices() {
        bus.setIoListener(new VirtualBus.IoListener() {
            @Override
            public void onIoWrite(int address, byte value) {
                int v = value & 255;
                if (address == 0x10) videoX = v;
                else if (address == 0x11) videoY = v;
                else if (address == 0x12) videoR = v;
                else if (address == 0x13) videoG = v;
                else if (address == 0x14) videoB = v;
                else if (address == 0x15) {
                    if (v == 1) {
                        int color = 0xFF000000 | (videoR << 16) | (videoG << 8) | videoB;
                        framebuffer.setPixel(videoX, videoY, color);
                        videoTouched = true;
                    } else if (v == 2) {
                        framebuffer.fill(0xFF000000);
                        videoTouched = true;
                    }
                }
            }

            @Override
            public byte onIoRead(int address) {
                if (address == 0x10) return (byte) videoX;
                if (address == 0x11) return (byte) videoY;
                if (address == 0x12) return (byte) videoR;
                if (address == 0x13) return (byte) videoG;
                if (address == 0x14) return (byte) videoB;
                if (address == 0x20) return (byte) (cpu.isHalted() ? 1 : 0);
                return 0;
            }
        });
    }

    public void run() {
        if (!clock.isRunning() || cpu.isHalted()) return;
        clock.tick();
        cpu.step();
    }

    public int runInstructions(int maximum) {
        if (!clock.isRunning() || cpu.isHalted()) return 0;
        int count = cpu.runSteps(maximum);
        int i;
        for (i = 0; i < count; i++) clock.tick();
        return count;
    }

    public void powerOn() {
        if (!cpu.isHalted()) clock.start();
    }

    public void powerOff() { clock.stop(); }

    public void reset() {
        clock.reset();
        cpu.reset();
        memory.clear();
        bus.reset();
        frame = 0;
        videoX = 0; videoY = 0; videoR = 0; videoG = 0; videoB = 0;
        videoTouched = false;
        framebuffer.fill(0xFF000000);
    }

    public void loadProgram(FantasyProgram program) {
        if (program == null) throw new IllegalArgumentException("Programa obrigatorio.");
        byte[] code = program.getCode();
        if (code.length > memory.size()) throw new IllegalArgumentException("Programa maior que a memoria.");
        reset();
        memory.load(0, code);
        cpu.getRegisters().pc = 0;
    }

    /** Renderizador de demonstracao para testar o monitor e o framebuffer. */
    public void renderFrame() {
        frame++;
        if (videoTouched) {
            return;
        }
        int w = framebuffer.getWidth();
        int h = framebuffer.getHeight();
        int[] px = framebuffer.getPixels();
        int y;
        int x;
        for (y = 0; y < h; y++) {
            int blue = 0x30 + (y * 0x70 / h);
            int color = 0xFF000000 | blue;
            int base = y * w;
            for (x = 0; x < w; x++) px[base + x] = color;
        }

        int barY = (int) (h / 2 + Math.sin(frame / 12.0) * (h / 3));
        int i;
        for (i = -6; i <= 6; i++) {
            y = barY + i;
            if (y < 0 || y >= h) continue;
            int intensity = 255 - Math.abs(i) * 36;
            int color = 0xFF000000 | (intensity << 16) | ((intensity / 2) << 8);
            int base = y * w;
            for (x = 0; x < w; x++) px[base + x] = color;
        }

        int size = 12;
        int rangeX = w - size - 4;
        int rangeY = h - size - 16;
        int tx = (int) ((frame * 2) % (rangeX * 2));
        int ty = (int) ((frame * 3) % (rangeY * 2));
        int bx = 2 + (tx < rangeX ? tx : rangeX * 2 - tx);
        int by = 2 + (ty < rangeY ? ty : rangeY * 2 - ty);
        framebuffer.fillRect(bx, by, size, size, 0xFFFFFFFF);
        framebuffer.fillRect(bx + 2, by + 2, size - 4, size - 4, 0xFF00C000);
        for (x = 0; x < w; x++) {
            px[x] = 0xFFC0C0C0;
            px[(h - 1) * w + x] = 0xFFC0C0C0;
        }
        for (y = 0; y < h; y++) {
            px[y * w] = 0xFFC0C0C0;
            px[y * w + w - 1] = 0xFFC0C0C0;
        }
        int pc = cpu.getRegisters().pc;
        for (i = 0; i < 16; i++) {
            boolean on = ((pc >> (15 - i)) & 1) != 0;
            framebuffer.fillRect(8 + i * 7, h - 9, 5, 5,
                    on ? 0xFF00FF00 : 0xFF003000);
        }
    }

    public VirtualFramebuffer getFramebuffer() { return framebuffer; }
    public VirtualCpu getCpu() { return cpu; }
    public VirtualMemory getMemory() { return memory; }
    public VirtualBus getBus() { return bus; }
    public VirtualClock getClock() { return clock; }
    public long getFrame() { return frame; }
}
