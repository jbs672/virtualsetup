package com.example.desktopenv;

import android.content.Context;
import android.app.ActivityManager;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.view.View;
import android.os.Handler;
import android.os.Looper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Renderizador 3D independente do monitor/CPU virtual original.
 * OpenGL ES 2.0, shaders GLSL, depth buffer, iluminacao Phong e camera orbital.
 * Java 7 / Android nativo, sem dependencias externas.
 */
public final class Fantasy3DView extends GLSurfaceView {

    public interface StatusListener {
        void onRenderError(String message);
    }

    private final SceneRenderer sceneRenderer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile StatusListener statusListener;
    private float previousX;
    private float previousY;
    private volatile int framesDrawn;
    private volatile boolean reported;
    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (framesDrawn <= 0) {
                reportError("a GPU nao desenhou nenhum quadro");
            }
        }
    };

    public static boolean supportsOpenGLES2(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return false;
            ConfigurationInfo info = manager.getDeviceConfigurationInfo();
            return info != null && info.reqGlEsVersion >= 0x00020000;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public Fantasy3DView(Context context) {
        super(context);
        // RGB565 + depth16 e a combinacao mais compativel com GPUs Android antigas.
        // Evita exigir framebuffer RGBA8888/stencil, que pode nao existir em alguns drivers.
        setEGLContextClientVersion(2);
        setEGLConfigChooser(new SafeConfigChooser());
        // Recriar o contexto ao voltar e mais seguro que preservar contextos instaveis.
        setPreserveEGLContextOnPause(false);
        setFocusable(true);
        sceneRenderer = new SceneRenderer(this);
        setRenderer(sceneRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    previousX = event.getX();
                    previousY = event.getY();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getX() - previousX;
                    float dy = event.getY() - previousY;
                    previousX = event.getX();
                    previousY = event.getY();
                    sceneRenderer.orbit(dx * 0.45f, dy * 0.35f);
                    return true;
                }
                return event.getAction() == MotionEvent.ACTION_UP;
            }
        });
    }

    public void resetCamera() {
        sceneRenderer.resetCamera();
        requestRender();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Plano B: se nada for desenhado em 3 s, avisa para trocar de renderizador.
        mainHandler.removeCallbacks(watchdog);
        // Zera a contagem em cada retomada: quadros de uma sessao anterior
        // nao podem mascarar uma falha na recriacao do contexto EGL.
        framesDrawn = 0;
        reported = false;
        mainHandler.postDelayed(watchdog, 3000L);
    }

    @Override
    public void onPause() {
        mainHandler.removeCallbacks(watchdog);
        super.onPause();
    }

    public void setStatusListener(StatusListener listener) {
        statusListener = listener;
    }

    private void reportError(final String message) {
        if (reported) return;
        reported = true;
        mainHandler.post(new Runnable() {
            @Override public void run() {
                StatusListener listener = statusListener;
                if (listener != null) listener.onRenderError(message);
            }
        });
    }

    /** Escolhe um EGLConfig com varias tentativas; nunca lanca excecao por falta de config exata. */
    private static final class SafeConfigChooser implements GLSurfaceView.EGLConfigChooser {
        private static final int EGL_RENDERABLE_TYPE = 0x3040;
        private static final int EGL_OPENGL_ES2_BIT = 0x0004;

        @Override
        public javax.microedition.khronos.egl.EGLConfig chooseConfig(
                javax.microedition.khronos.egl.EGL10 egl,
                javax.microedition.khronos.egl.EGLDisplay display) {
            int[][] tries = {
                    {8, 8, 8, 24}, {8, 8, 8, 16}, {5, 6, 5, 16},
                    {8, 8, 8, 0}, {5, 6, 5, 0}, {0, 0, 0, 0}
            };
            for (int i = 0; i < tries.length; i++) {
                int[] t = tries[i];
                int[] attrs = {
                        javax.microedition.khronos.egl.EGL10.EGL_RED_SIZE, t[0],
                        javax.microedition.khronos.egl.EGL10.EGL_GREEN_SIZE, t[1],
                        javax.microedition.khronos.egl.EGL10.EGL_BLUE_SIZE, t[2],
                        javax.microedition.khronos.egl.EGL10.EGL_DEPTH_SIZE, t[3],
                        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                        javax.microedition.khronos.egl.EGL10.EGL_NONE
                };
                int[] count = new int[1];
                if (!egl.eglChooseConfig(display, attrs, null, 0, count) || count[0] <= 0) continue;
                javax.microedition.khronos.egl.EGLConfig[] configs =
                        new javax.microedition.khronos.egl.EGLConfig[count[0]];
                if (!egl.eglChooseConfig(display, attrs, configs, count[0], count)) continue;
                for (int k = 0; k < count[0]; k++) {
                    if (configs[k] != null && depthBits(egl, display, configs[k]) > 0) return configs[k];
                }
            }
            throw new IllegalArgumentException("Nenhum EGLConfig OpenGL ES 2.0 com depth buffer");
        }

        private int depthBits(javax.microedition.khronos.egl.EGL10 egl,
                              javax.microedition.khronos.egl.EGLDisplay d,
                              javax.microedition.khronos.egl.EGLConfig c) {
            int[] v = new int[1];
            return egl.eglGetConfigAttrib(d, c, javax.microedition.khronos.egl.EGL10.EGL_DEPTH_SIZE, v)
                    ? v[0] : 0;
        }
    }

    private static final class SceneRenderer implements GLSurfaceView.Renderer {
        // GLSL ES 1.00 estrito: sem mat3(mat4), sem uniform compartilhado entre
        // vertex e fragment (precisoes diferentes causam falha de link).
        private static final float[] STRUCTURE_X = {-4.45f, -3.15f, -1.85f, 1.85f, 3.15f, 4.45f};
        private static final float[] STRUCTURE_Z = {-4.35f, 4.35f};

        private static final String VERTEX_SHADER =
                "precision highp float;" +
                "uniform mat4 uMvp; uniform mat4 uModel;" +
                "attribute vec3 aPosition; attribute vec3 aNormal;" +
                "varying vec3 vNormal; varying vec3 vWorld;" +
                "void main(){ vec4 world=uModel*vec4(aPosition,1.0);" +
                "vWorld=world.xyz;" +
                "vNormal=mat3(uModel[0].xyz,uModel[1].xyz,uModel[2].xyz)*aNormal;" +
                "gl_Position=uMvp*vec4(aPosition,1.0); }";

        private static final String FRAGMENT_SHADER =
                "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
                "precision highp float;\n" +
                "#else\n" +
                "precision mediump float;\n" +
                "#endif\n" +
                "uniform vec4 uColor; uniform vec3 uEye;" +
                "varying vec3 vNormal; varying vec3 vWorld;" +
                "void main(){ vec3 n=normalize(vNormal);" +
                "vec3 l=normalize(vec3(-0.45,0.85,0.55));" +
                "vec3 toEye=uEye-vWorld;" +
                "float dist=length(toEye);" +
                "vec3 v=toEye/max(dist,0.0001); vec3 h=normalize(l+v);" +
                "float diff=max(dot(n,l),0.0);" +
                "float spec=pow(max(dot(n,h),0.0),38.0);" +
                "float rim=pow(1.0-max(dot(n,v),0.0),3.0);" +
                "vec3 base=uColor.rgb;" +
                "vec3 lit=base*(0.20+0.82*diff)+vec3(0.72)*spec*0.72;" +
                "lit+=vec3(0.08,0.32,0.55)*rim*0.42;" +
                "float fog=clamp((dist-9.0)/18.0,0.0,0.68);" +
                "vec3 fogColor=vec3(0.025,0.045,0.085);" +
                "gl_FragColor=vec4(mix(lit,fogColor,fog),uColor.a); }";

        private final float[] projection = new float[16];
        private final float[] view = new float[16];
        private final float[] model = new float[16];
        private final float[] mvp = new float[16];
        private final float[] temp = new float[16];
        private int program;
        private int positionHandle;
        private int normalHandle;
        private int mvpHandle;
        private int modelHandle;
        private int colorHandle;
        private int eyeHandle;
        private FloatBuffer cubeBuffer;
        private FloatBuffer sphereBuffer;
        private FloatBuffer gridBuffer;
        private int sphereVertexCount;
        private int gridVertexCount;
        private float angle;
        private volatile float cameraYaw = 24.0f;
        private volatile float cameraPitch = 17.0f;
        private long lastFrame;
        private final Fantasy3DView owner;
        private volatile boolean failed;
        private String failureMessage;

        SceneRenderer(Fantasy3DView view) {
            owner = view;
            cubeBuffer = makeBuffer(createCube());
            sphereBuffer = makeBuffer(createSphere(28, 36));
            sphereVertexCount = sphereBuffer.limit() / 6;
            gridBuffer = makeBuffer(createGrid());
            gridVertexCount = gridBuffer.limit() / 6;
        }

        void orbit(float dx, float dy) {
            cameraYaw += dx;
            cameraPitch = Math.max(-8.0f, Math.min(55.0f, cameraPitch + dy));
        }

        void resetCamera() {
            cameraYaw = 24.0f;
            cameraPitch = 17.0f;
        }

        @Override
        public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,
                                      javax.microedition.khronos.egl.EGLConfig config) {
            try {
                failed = false;
                failureMessage = null;
                GLES20.glClearColor(0.025f, 0.045f, 0.085f, 1.0f);
                GLES20.glEnable(GLES20.GL_DEPTH_TEST);
                GLES20.glDepthFunc(GLES20.GL_LEQUAL);
                GLES20.glEnable(GLES20.GL_CULL_FACE);
                GLES20.glCullFace(GLES20.GL_BACK);
                program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
                if (program == 0) throw new RuntimeException("O driver retornou um programa OpenGL vazio.");
                positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
                normalHandle = GLES20.glGetAttribLocation(program, "aNormal");
                mvpHandle = GLES20.glGetUniformLocation(program, "uMvp");
                modelHandle = GLES20.glGetUniformLocation(program, "uModel");
                colorHandle = GLES20.glGetUniformLocation(program, "uColor");
                eyeHandle = GLES20.glGetUniformLocation(program, "uEye");
                if (positionHandle < 0 || normalHandle < 0 || mvpHandle < 0 ||
                        modelHandle < 0 || colorHandle < 0 || eyeHandle < 0) {
                    throw new RuntimeException("O driver nao disponibilizou todos os atributos/uniformes do shader.");
                }
                lastFrame = System.nanoTime();
            } catch (Throwable error) {
                fail("Falha ao iniciar OpenGL ES 2.0: " + safeMessage(error));
            }
        }

        @Override
        public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,
                                     int width, int height) {
            try {
                if (width <= 0 || height <= 0) return;
                GLES20.glViewport(0, 0, width, height);
                float ratio = (float) width / (float) Math.max(1, height);
                Matrix.perspectiveM(projection, 0, 52.0f, ratio, 0.1f, 80.0f);
            } catch (Throwable error) {
                fail("Falha ao configurar a superficie 3D: " + safeMessage(error));
            }
        }

        @Override
        public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
            if (failed || program == 0) {
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                return;
            }
            try {
            long now = System.nanoTime();
            float delta = Math.min(0.05f, (now - lastFrame) / 1000000000.0f);
            lastFrame = now;
            angle += delta * 25.0f;

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glUseProgram(program);

            float yaw = (float) Math.toRadians(cameraYaw);
            float pitch = (float) Math.toRadians(cameraPitch);
            float radius = 10.5f;
            float eyeX = (float) (Math.sin(yaw) * Math.cos(pitch) * radius);
            float eyeY = (float) (Math.sin(pitch) * radius + 2.2f);
            float eyeZ = (float) (Math.cos(yaw) * Math.cos(pitch) * radius);
            Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ,
                    0.0f, 1.2f, 0.0f, 0.0f, 1.0f, 0.0f);
            GLES20.glUniform3f(eyeHandle, eyeX, eyeY, eyeZ);

            // Plataforma principal e piso modular.
            drawBox(0.0f, -0.22f, 0.0f, 12.0f, 0.35f, 12.0f,
                    0.075f, 0.105f, 0.16f, 1.0f, 0.0f);
            drawGrid();

            // Pilares e painéis de cenário dão escala e profundidade à cena.
            drawBox(-4.0f, 1.0f, -2.7f, 0.42f, 2.4f, 0.42f,
                    0.12f, 0.30f, 0.48f, 1.0f, 0.0f);
            drawBox(4.0f, 1.35f, -3.2f, 0.55f, 3.1f, 0.55f,
                    0.12f, 0.24f, 0.38f, 1.0f, 0.0f);
            drawBox(-3.0f, 0.35f, 3.2f, 1.3f, 0.8f, 0.7f,
                    0.16f, 0.20f, 0.29f, 1.0f, 0.0f);
            drawBox(3.4f, 0.45f, 2.7f, 1.0f, 1.0f, 0.8f,
                    0.13f, 0.18f, 0.27f, 1.0f, 0.0f);

            // Objeto central: androide mecânico articulado.
            drawBox(0.0f, 1.55f, 0.0f, 1.35f, 1.55f, 0.78f,
                    0.19f, 0.34f, 0.48f, 1.0f, angle);
            drawBox(0.0f, 2.75f, 0.0f, 0.92f, 0.78f, 0.76f,
                    0.28f, 0.42f, 0.55f, 1.0f, -angle * 0.35f);
            drawBox(-0.24f, 2.82f, 0.395f, 0.16f, 0.10f, 0.035f,
                    0.05f, 0.85f, 1.0f, 1.0f, 0.0f);
            drawBox(0.24f, 2.82f, 0.395f, 0.16f, 0.10f, 0.035f,
                    0.05f, 0.85f, 1.0f, 1.0f, 0.0f);
            drawBox(0.0f, 2.48f, 0.405f, 0.30f, 0.055f, 0.035f,
                    0.95f, 0.34f, 0.12f, 1.0f, 0.0f);
            drawBox(-0.91f, 1.60f, 0.0f, 0.42f, 1.35f, 0.48f,
                    0.22f, 0.29f, 0.38f, 1.0f, (float)Math.sin(angle * 0.035f) * 12.0f);
            drawBox(0.91f, 1.60f, 0.0f, 0.42f, 1.35f, 0.48f,
                    0.22f, 0.29f, 0.38f, 1.0f, -(float)Math.sin(angle * 0.035f) * 12.0f);
            drawBox(-0.38f, 0.52f, 0.0f, 0.48f, 0.95f, 0.55f,
                    0.12f, 0.20f, 0.29f, 1.0f, 0.0f);
            drawBox(0.38f, 0.52f, 0.0f, 0.48f, 0.95f, 0.55f,
                    0.12f, 0.20f, 0.29f, 1.0f, 0.0f);
            drawBox(0.0f, 1.62f, 0.43f, 0.58f, 0.48f, 0.08f,
                    0.06f, 0.75f, 0.92f, 1.0f, 0.0f);

            // Estruturas perifericas: modulos, torres e paineis de uma
            // pequena instalacao sci-fi. Geometria reutilizada, sem alocacao
            // de memoria por quadro.
            for (int row = 0; row < STRUCTURE_Z.length; row++) {
                for (int col = 0; col < STRUCTURE_X.length; col++) {
                    float height = 0.65f + (col % 3) * 0.28f;
                    float pulse = 0.5f + 0.5f * (float)Math.sin(angle * 0.035f + col);
                    drawBox(STRUCTURE_X[col], height, STRUCTURE_Z[row], 0.34f, height, 0.34f,
                            0.075f + pulse * 0.035f, 0.19f + pulse * 0.08f,
                            0.29f + pulse * 0.10f, 1.0f, (row == 0 ? 1 : -1) * angle * 0.12f);
                    drawBox(STRUCTURE_X[col], height * 1.75f, STRUCTURE_Z[row], 0.42f, 0.055f, 0.42f,
                            0.04f, 0.48f + pulse * 0.25f, 0.72f + pulse * 0.20f,
                            1.0f, 0.0f);
                }
            }

            // Esfera metalica animada e quatro nucleos orbitais.
            drawSphere(-2.15f, 1.25f + (float)Math.sin(angle * 0.025f) * 0.18f,
                    0.2f, 0.72f, 0.18f, 0.55f, 0.82f);
            drawSphere(4.0f, 3.2f, -3.2f, 0.32f, 0.28f, 0.65f, 0.95f);
            for (int i = 0; i < 4; i++) {
                float orbit = angle * 0.65f + i * 1.5707963f;
                float ox = (float)Math.cos(orbit) * 2.15f;
                float oz = (float)Math.sin(orbit) * 2.15f;
                float oy = 1.75f + (float)Math.sin(orbit * 1.7f) * 0.32f;
                drawSphere(ox, oy, oz, 0.13f, 0.12f, 0.62f, 0.92f);
            }

            // Captura erros GL apos todas as chamadas; um erro de driver nao
            // deve ser confundido com um quadro renderizado com sucesso.
            int glError = GLES20.glGetError();
            if (glError == GLES20.GL_NO_ERROR) {
                owner.framesDrawn++;
            } else {
                fail("Erro OpenGL durante o desenho (0x" + Integer.toHexString(glError) + ")");
            }
            } catch (Throwable error) {
                fail("Erro durante a renderizacao 3D: " + safeMessage(error));
            }
        }

        private void fail(String message) {
            if (failed) return;
            failed = true;
            failureMessage = message;
            if (program != 0) {
                try { GLES20.glDeleteProgram(program); } catch (Throwable ignored) { }
                program = 0;
            }
            owner.reportError(message);
        }

        private static String safeMessage(Throwable error) {
            String message = error == null ? null : error.getMessage();
            if (message == null || message.length() == 0) {
                return error == null ? "erro desconhecido" : error.getClass().getSimpleName();
            }
            return message;
        }

        private void drawBox(float x, float y, float z, float sx, float sy, float sz,
                             float r, float g, float b, float alpha, float rotationY) {
            Matrix.setIdentityM(model, 0);
            Matrix.translateM(model, 0, x, y, z);
            if (rotationY != 0.0f) Matrix.rotateM(model, 0, rotationY, 0, 1, 0);
            Matrix.scaleM(model, 0, sx, sy, sz);
            drawMesh(cubeBuffer, 36, r, g, b, alpha);
        }

        private void drawSphere(float x, float y, float z, float scale,
                                float r, float g, float b) {
            Matrix.setIdentityM(model, 0);
            Matrix.translateM(model, 0, x, y, z);
            Matrix.rotateM(model, 0, angle * 0.7f, 0.3f, 1.0f, 0.2f);
            Matrix.scaleM(model, 0, scale, scale, scale);
            drawMesh(sphereBuffer, sphereVertexCount, r, g, b, 1.0f);
        }

        private void drawMesh(FloatBuffer buffer, int count,
                              float r, float g, float b, float a) {
            Matrix.multiplyMM(temp, 0, view, 0, model, 0);
            Matrix.multiplyMM(mvp, 0, projection, 0, temp, 0);
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0);
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0);
            GLES20.glUniform4f(colorHandle, r, g, b, a);
            buffer.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glEnableVertexAttribArray(normalHandle);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT,
                    false, 24, buffer);
            buffer.position(3);
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT,
                    false, 24, buffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);
        }

        private void drawGrid() {
            Matrix.setIdentityM(model, 0);
            Matrix.multiplyMM(temp, 0, view, 0, model, 0);
            Matrix.multiplyMM(mvp, 0, projection, 0, temp, 0);
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0);
            GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0);
            GLES20.glUniform4f(colorHandle, 0.08f, 0.22f, 0.34f, 1.0f);
            gridBuffer.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glEnableVertexAttribArray(normalHandle);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT,
                    false, 24, gridBuffer);
            gridBuffer.position(3);
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT,
                    false, 24, gridBuffer);
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, gridVertexCount);
        }

        private static int createProgram(String vertex, String fragment) {
            int vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex);
            int fs;
            try {
                fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment);
            } catch (RuntimeException error) {
                GLES20.glDeleteShader(vs);
                throw error;
            }
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, vs);
            GLES20.glAttachShader(p, fs);
            GLES20.glLinkProgram(p);
            int[] status = new int[1];
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0);
            if (status[0] == 0) {
                String error = GLES20.glGetProgramInfoLog(p);
                GLES20.glDeleteProgram(p);
                throw new RuntimeException("Falha ao vincular shaders 3D: " + error);
            }
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);
            return p;
        }

        private static int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] status = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                String error = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new RuntimeException("Falha ao compilar shader 3D: " + error);
            }
            return shader;
        }

        private static FloatBuffer makeBuffer(float[] data) {
            ByteBuffer bytes = ByteBuffer.allocateDirect(data.length * 4);
            bytes.order(ByteOrder.nativeOrder());
            FloatBuffer buffer = bytes.asFloatBuffer();
            buffer.put(data);
            buffer.position(0);
            return buffer;
        }

        private static float[] createCube() {
            float[] out = new float[36 * 6];
            float[][] faces = {
                {-1,-1, 1, 1,-1, 1, 1, 1, 1, -1, 1, 1, 0,0,1},
                { 1,-1,-1,-1,-1,-1,-1, 1,-1, 1, 1,-1, 0,0,-1},
                {-1, 1, 1, 1, 1, 1, 1, 1,-1,-1, 1,-1, 0,1,0},
                {-1,-1,-1, 1,-1,-1, 1,-1, 1,-1,-1, 1, 0,-1,0},
                { 1,-1, 1, 1,-1,-1, 1, 1,-1, 1, 1, 1, 1,0,0},
                {-1,-1,-1,-1,-1, 1,-1, 1, 1,-1, 1,-1,-1,0,0}
            };
            int offset = 0;
            for (int f = 0; f < faces.length; f++) {
                float[] a = faces[f];
                float[] p = {a[0],a[1],a[2], a[3],a[4],a[5],
                             a[6],a[7],a[8], a[0],a[1],a[2],
                             a[6],a[7],a[8], a[9],a[10],a[11]};
                for (int v = 0; v < 6; v++) {
                    out[offset++] = p[v*3];
                    out[offset++] = p[v*3+1];
                    out[offset++] = p[v*3+2];
                    out[offset++] = a[12];
                    out[offset++] = a[13];
                    out[offset++] = a[14];
                }
            }
            return out;
        }

        private static float[] createSphere(int rings, int sectors) {
            int triangles = rings * sectors * 2;
            float[] out = new float[triangles * 3 * 6];
            int offset = 0;
            for (int r = 0; r < rings; r++) {
                float v0 = (float) Math.PI * (-0.5f + (float) r / rings);
                float v1 = (float) Math.PI * (-0.5f + (float) (r + 1) / rings);
                for (int s = 0; s < sectors; s++) {
                    float h0 = (float) (2.0 * Math.PI * s / sectors);
                    float h1 = (float) (2.0 * Math.PI * (s + 1) / sectors);
                    float[] a = spherePoint(v0, h0);
                    float[] b = spherePoint(v1, h0);
                    float[] c = spherePoint(v1, h1);
                    float[] d = spherePoint(v0, h1);
                    offset = putVertex(out, offset, a);
                    offset = putVertex(out, offset, b);
                    offset = putVertex(out, offset, c);
                    offset = putVertex(out, offset, a);
                    offset = putVertex(out, offset, c);
                    offset = putVertex(out, offset, d);
                }
            }
            return out;
        }

        private static float[] spherePoint(float latitude, float longitude) {
            float x = (float) (Math.cos(latitude) * Math.cos(longitude));
            float y = (float) Math.sin(latitude);
            float z = (float) (Math.cos(latitude) * Math.sin(longitude));
            return new float[] {x, y, z, x, y, z};
        }

        private static int putVertex(float[] out, int offset, float[] p) {
            for (int i = 0; i < 6; i++) out[offset++] = p[i];
            return offset;
        }

        private static float[] createGrid() {
            float[] out = new float[22 * 2 * 6];
            int offset = 0;
            for (int i = -5; i <= 5; i++) {
                offset = putLine(out, offset, -5.5f, -0.035f, i, 5.5f, -0.035f, i);
                offset = putLine(out, offset, i, -0.035f, -5.5f, i, -0.035f, 5.5f);
            }
            return out;
        }

        private static int putLine(float[] out, int o,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2) {
            out[o++] = x1; out[o++] = y1; out[o++] = z1;
            out[o++] = 0; out[o++] = 1; out[o++] = 0;
            out[o++] = x2; out[o++] = y2; out[o++] = z2;
            out[o++] = 0; out[o++] = 1; out[o++] = 0;
            return o;
        }
    }
}
