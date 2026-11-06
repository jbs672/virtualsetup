package com.example.desktopenv;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SuperComputerEngine
 *
 * Motor paralelo secundario do Fantasy PC / VirtualSetup.
 * Java 7 puro, sem bibliotecas externas.
 *
 * O motor so inicia quando getInstance() e chamado.
 * Nao altera nem substitui DesktopCoreEngine.
 */
public final class SuperComputerEngine {

    private static volatile SuperComputerEngine instance;

    private static final int MAX_PENDING = 128;

    private final ConcurrentLinkedQueue<Runnable> taskQueue;
    private final AtomicInteger pendingTasks;
    private final AtomicLong completedTasks;
    private final AtomicBoolean running;

    private final int cpuCores;
    private final int workerCount;

    private ThreadPoolExecutor workerPool;
    private Thread dispatcherThread;

    private SuperComputerEngine() {
        taskQueue = new ConcurrentLinkedQueue<Runnable>();
        pendingTasks = new AtomicInteger(0);
        completedTasks = new AtomicLong(0);
        running = new AtomicBoolean(false);

        cpuCores = Runtime.getRuntime().availableProcessors();

        // Reserva pelo menos um nucleo logico para o Android quando possivel.
        workerCount = Math.max(1, Math.min(4, cpuCores - 1));

        workerPool = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(MAX_PENDING),
                new ThreadFactory() {
                    private final AtomicInteger ids = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(
                                runnable,
                                "FantasyCompute-" + ids.getAndIncrement()
                        );
                        thread.setDaemon(true);
                        thread.setPriority(Thread.NORM_PRIORITY);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy()
        );

        startDispatcher();
    }

    public static SuperComputerEngine getInstance() {
        if (instance == null) {
            synchronized (SuperComputerEngine.class) {
                if (instance == null) {
                    instance = new SuperComputerEngine();
                }
            }
        }
        return instance;
    }

    private void startDispatcher() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        dispatcherThread = new Thread(new Runnable() {
            @Override
            public void run() {
                long lastTelemetry = System.currentTimeMillis();

                while (running.get()) {
                    try {
                        long now = System.currentTimeMillis();

                        if (now - lastTelemetry >= 5000L) {
                            android.util.Log.i(
                                    "FantasyCompute",
                                    getTelemetry()
                            );
                            lastTelemetry = now;
                        }

                        final Runnable task = taskQueue.poll();

                        if (task == null) {
                            Thread.sleep(2L);
                            continue;
                        }

                        try {
                            workerPool.execute(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        task.run();
                                    } catch (Throwable error) {
                                        android.util.Log.e(
                                                "FantasyCompute",
                                                "Erro durante tarefa",
                                                error
                                        );
                                    } finally {
                                        completedTasks.incrementAndGet();
                                        pendingTasks.decrementAndGet();
                                    }
                                }
                            });
                        } catch (RejectedExecutionException error) {
                            taskQueue.offer(task);
                            Thread.sleep(5L);
                        }

                    } catch (InterruptedException error) {
                        if (!running.get()) {
                            break;
                        }
                    } catch (Throwable error) {
                        android.util.Log.e(
                                "FantasyCompute",
                                "Erro no dispatcher",
                                error
                        );
                    }
                }
            }
        }, "FantasyDispatcher");

        dispatcherThread.setDaemon(true);
        dispatcherThread.setPriority(Thread.NORM_PRIORITY);
        dispatcherThread.start();
    }

    /**
     * Submete uma tarefa ao motor.
     * Retorna false se o motor estiver encerrado ou a fila estiver cheia.
     */
    public boolean submit(Runnable task) {
        if (task == null || !running.get()) {
            return false;
        }

        int count = pendingTasks.incrementAndGet();

        if (count > MAX_PENDING) {
            pendingTasks.decrementAndGet();
            return false;
        }

        taskQueue.offer(task);
        return true;
    }

    // -------------------- MATEMATICA 3D --------------------

    public static class Vector3 {
        public float x;
        public float y;
        public float z;

        public Vector3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public float length() {
            return (float) Math.sqrt(x * x + y * y + z * z);
        }

        public static float distance(Vector3 a, Vector3 b) {
            float dx = a.x - b.x;
            float dy = a.y - b.y;
            float dz = a.z - b.z;
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        public static Vector3 add(Vector3 a, Vector3 b) {
            return new Vector3(a.x + b.x, a.y + b.y, a.z + b.z);
        }
    }

    /**
     * Matriz 4x4 em formato column-major.
     */
    public static class Matrix4 {
        public final float[] m = new float[16];

        public Matrix4() {
            identity();
        }

        public void identity() {
            int i;
            for (i = 0; i < 16; i++) {
                m[i] = 0.0f;
            }
            m[0] = 1.0f;
            m[5] = 1.0f;
            m[10] = 1.0f;
            m[15] = 1.0f;
        }

        public void translate(float x, float y, float z) {
            identity();
            m[12] = x;
            m[13] = y;
            m[14] = z;
        }

        public void rotateX(float angle) {
            identity();
            float c = (float) Math.cos(angle);
            float s = (float) Math.sin(angle);
            m[5] = c;
            m[6] = s;
            m[9] = -s;
            m[10] = c;
        }

        public void rotateY(float angle) {
            identity();
            float c = (float) Math.cos(angle);
            float s = (float) Math.sin(angle);
            m[0] = c;
            m[2] = -s;
            m[8] = s;
            m[10] = c;
        }

        public void rotateZ(float angle) {
            identity();
            float c = (float) Math.cos(angle);
            float s = (float) Math.sin(angle);
            m[0] = c;
            m[1] = s;
            m[4] = -s;
            m[5] = c;
        }

        public Vector3 transform(Vector3 v) {
            float x = m[0] * v.x + m[4] * v.y
                    + m[8] * v.z + m[12];
            float y = m[1] * v.x + m[5] * v.y
                    + m[9] * v.z + m[13];
            float z = m[2] * v.x + m[6] * v.y
                    + m[10] * v.z + m[14];
            return new Vector3(x, y, z);
        }

        public static Matrix4 multiply(Matrix4 a, Matrix4 b) {
            Matrix4 result = new Matrix4();
            int row;
            int col;
            int k;

            for (row = 0; row < 4; row++) {
                for (col = 0; col < 4; col++) {
                    float sum = 0.0f;
                    for (k = 0; k < 4; k++) {
                        sum += a.m[k * 4 + row] * b.m[col * 4 + k];
                    }
                    result.m[col * 4 + row] = sum;
                }
            }
            return result;
        }
    }

    public static Vector3 project(Vector3 vertex, float focalLength) {
        float z = vertex.z;
        if (z < 0.01f) {
            z = 0.01f;
        }
        return new Vector3(
                vertex.x * focalLength / z,
                vertex.y * focalLength / z,
                z
        );
    }

    // -------------------- FISICA E COLISOES --------------------

    public static boolean sphereCollision(
            Vector3 a, float radiusA, Vector3 b, float radiusB) {
        float radius = radiusA + radiusB;
        return Vector3.distance(a, b) <= radius;
    }

    public static float calculateKineticEnergy(
            float mass, Vector3 velocity) {
        float speed = velocity.length();
        return 0.5f * mass * speed * speed;
    }

    // -------------------- BENCHMARK --------------------

    public interface BenchmarkListener {
        void onProgress(int percent, double polygonsPerSecond, double estimatedFPS);
        void onFinished(BenchmarkResult result);
        void onError(String message);
    }

    public static class BenchmarkResult {
        public long elapsedMillis;
        public long totalPolygons;
        public double polygonsPerSecond;
        public double kiloPolygonsPerSecond;
        public double estimatedFPS;
        public long memoryUsed;
        public int cpuCores;

        @Override
        public String toString() {
            return "Fantasy PC Benchmark\n"
                    + "Tempo: " + elapsedMillis + " ms\n"
                    + "Poligonos: " + totalPolygons + "\n"
                    + "K Polygons/s: " + kiloPolygonsPerSecond + "\n"
                    + "FPS estimado: " + estimatedFPS + "\n"
                    + "Memoria: " + (memoryUsed / 1048576L) + " MB\n"
                    + "Nucleos: " + cpuCores;
        }
    }

    /**
     * Executa benchmark de geometria por ate 8 segundos.
     * O FPS e uma estimativa matematica, nao FPS de tela real.
     */
    public void runBenchmark(final BenchmarkListener listener) {
        boolean accepted = submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final long duration = 8000L;
                    final long start = System.nanoTime();

                    long polygons = 0L;
                    int batch = 250;
                    float angle = 0.0f;

                    Vector3[] vertices = new Vector3[3];
                    vertices[0] = new Vector3(1.0f, 0.0f, 4.0f);
                    vertices[1] = new Vector3(0.0f, 1.0f, 4.0f);
                    vertices[2] = new Vector3(-1.0f, 0.0f, 4.0f);

                    Matrix4 rotation = new Matrix4();

                    while (true) {
                        long elapsed = (System.nanoTime() - start) / 1000000L;
                        if (elapsed >= duration) {
                            break;
                        }

                        rotation.rotateY(angle);
                        angle += 0.01f;

                        int i;
                        int v;
                        for (i = 0; i < batch; i++) {
                            for (v = 0; v < 3; v++) {
                                Vector3 transformed = rotation.transform(vertices[v]);
                                Vector3 projected = project(transformed, 320.0f);

                                if (projected.x > 1000000.0f) {
                                    angle += 0.000001f;
                                }
                            }
                            polygons++;
                        }

                        elapsed = (System.nanoTime() - start) / 1000000L;
                        double seconds = Math.max(0.001, elapsed / 1000.0);
                        double pps = polygons / seconds;
                        int percent = (int) Math.min(
                                100L, (elapsed * 100L) / duration
                        );

                        if (listener != null) {
                            listener.onProgress(percent, pps, pps / 2000.0);
                        }

                        batch = Math.min(batch * 2, 4000);
                        Thread.yield();
                    }

                    long elapsedMillis =
                            (System.nanoTime() - start) / 1000000L;
                    double seconds = Math.max(0.001, elapsedMillis / 1000.0);

                    BenchmarkResult result = new BenchmarkResult();
                    result.elapsedMillis = elapsedMillis;
                    result.totalPolygons = polygons;
                    result.polygonsPerSecond = polygons / seconds;
                    result.kiloPolygonsPerSecond =
                            result.polygonsPerSecond / 1000.0;
                    result.estimatedFPS =
                            result.polygonsPerSecond / 2000.0;

                    Runtime runtime = Runtime.getRuntime();
                    result.memoryUsed =
                            runtime.totalMemory() - runtime.freeMemory();
                    result.cpuCores = cpuCores;

                    if (listener != null) {
                        listener.onFinished(result);
                    }
                } catch (Throwable error) {
                    if (listener != null) {
                        listener.onError(error.toString());
                    }
                    android.util.Log.e(
                            "FantasyCompute",
                            "Falha no benchmark",
                            error
                    );
                }
            }
        });

        if (!accepted && listener != null) {
            listener.onError("Fila de processamento cheia ou motor encerrado.");
        }
    }

    // -------------------- TELEMETRIA --------------------

    public String getTelemetry() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();

        return "=== FANTASY COMPUTE ENGINE ===\n"
                + "CPU cores: " + cpuCores + "\n"
                + "Workers: " + workerCount + "\n"
                + "Fila: " + pendingTasks.get() + "\n"
                + "Concluidas: " + completedTasks.get() + "\n"
                + "Memoria usada: " + (used / 1048576L) + " MB\n"
                + "Memoria maxima: " + (max / 1048576L) + " MB\n"
                + "Motor ativo: " + running.get();
    }

    public long getCompletedTasks() {
        return completedTasks.get();
    }

    public int getPendingTasks() {
        return pendingTasks.get();
    }

    public int getWorkerCount() {
        return workerCount;
    }

    // -------------------- ENCERRAMENTO --------------------

    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (dispatcherThread != null) {
            dispatcherThread.interrupt();
        }

        workerPool.shutdown();

        try {
            if (!workerPool.awaitTermination(2L, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException error) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        taskQueue.clear();
        pendingTasks.set(0);
    }
}
