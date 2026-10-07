package ing.fuyaoskyrocket.photoinfo.platform.graphics

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES30 as GL
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.motion.TelegramDustGrid
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

// Ported from NextAlone/Nagram ThanosEffect, b8db62a65e1e4dee34d92bff412548ef628ddb06 (GPL-3.0).
internal class TelegramDustView(
    context: Context,
    private val bitmap: Bitmap,
    private val bounds: RectF,
    private val photo: Boolean,
    private val onReady: () -> Unit,
    private val onFinished: () -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var worker: DustWorker? = null
    private var ready = false
    private var released = false
    private val choreographer = Choreographer.getInstance()
    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val active = worker ?: return
            if (!active.running.get()) return
            active.frame()
            choreographer.postFrameCallback(this)
        }
    }

    init {
        isOpaque = false
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (released) return
        worker = DustWorker(context, surface, width, height, bitmap, bounds, photo) {
            choreographer.removeFrameCallback(callback)
            if (!released) onFinished()
        }.also { it.start() }
        choreographer.postFrameCallback(callback)
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        if (!ready && !released) { ready = true; onReady() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        release()
        onFinished()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (worker == null) surface.release() else release()
        return false
    }

    fun release() {
        if (released) return
        released = true
        choreographer.removeFrameCallback(callback)
        worker?.stop()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}

private class DustWorker(
    context: Context,
    private val texture: SurfaceTexture,
    private val width: Int,
    private val height: Int,
    private val bitmap: Bitmap,
    private val bounds: RectF,
    private val photo: Boolean,
    private val finished: () -> Unit,
) {
    val running = AtomicBoolean(true)
    private val queued = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("PhotoDust")
    private lateinit var handler: Handler
    private val density = context.resources.displayMetrics.density
    private val lowMemory = context.getSystemService(ActivityManager::class.java).isLowRamDevice
    private val resources = context.resources
    private val grid = TelegramDustGrid.calculate(bounds.width().toInt().coerceAtLeast(1), bounds.height().toInt().coerceAtLeast(1),
        density, (if (lowMemory) 30_000 else if (Runtime.getRuntime().availableProcessors() <= 4) 60_000 else 120_000) / if (photo) 2 else 1)
    private val seed = Random.nextFloat() * 2f
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var window: Surface? = null
    private var program = 0
    private val buffers = IntArray(2)
    private val textures = IntArray(1)
    private val arrays = IntArray(1)
    private val uniforms = mutableMapOf<String, Int>()
    private var currentBuffer = 0
    private var first = true
    private var initialized = false
    private var cleaned = false
    private var previousNanos = 0L
    private var time = if (photo) -.1f else 0f
    private val longevity = if (photo) 4f else 1.5f

    fun start() {
        thread.start()
        handler = Handler(thread.looper)
        handler.post {
            if (!running.get()) { clean(); return@post }
            try { initialize(); initialized = true }
            catch (failure: RuntimeException) { fail(failure) }
        }
    }

    fun frame() {
        if (!running.get() || !queued.compareAndSet(false, true)) return
        handler.post {
            try {
                if (running.get() && initialized) draw()
            } catch (failure: RuntimeException) { fail(failure) }
            finally { queued.set(false) }
        }
    }

    fun stop() {
        running.set(false)
        handler.post { clean() }
    }

    private fun initialize() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1))
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attributes = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR, EGL14.EGL_NONE)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0)
        val config = requireNotNull(configs[0])
        eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT)
        window = Surface(texture)
        eglSurface = EGL14.eglCreateWindowSurface(display, config, window, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext))
        val vertex = compile(GL.GL_VERTEX_SHADER, R.raw.telegram_thanos_vertex)
        val fragment = try { compile(GL.GL_FRAGMENT_SHADER, R.raw.telegram_thanos_fragment) }
        catch (failure: RuntimeException) { GL.glDeleteShader(vertex); throw failure }
        try {
            program = GL.glCreateProgram()
            GL.glAttachShader(program, vertex)
            GL.glAttachShader(program, fragment)
            GL.glTransformFeedbackVaryings(program, arrayOf("outUV", "outPosition", "outVelocity", "outTime"), GL.GL_INTERLEAVED_ATTRIBS)
            GL.glLinkProgram(program)
            val status = IntArray(1)
            GL.glGetProgramiv(program, GL.GL_LINK_STATUS, status, 0)
            check(status[0] == GL.GL_TRUE) { GL.glGetProgramInfoLog(program) }
        } finally { GL.glDeleteShader(vertex); GL.glDeleteShader(fragment) }
        listOf("matrix", "reset", "time", "deltaTime", "particlesCount", "size", "gridSize", "rectSize", "seed",
            "tex", "dp", "longevity", "offset", "scale", "uvOffset").forEach { uniforms[it] = GL.glGetUniformLocation(program, it) }
        GL.glGenVertexArrays(1, arrays, 0)
        GL.glBindVertexArray(arrays[0])
        GL.glGenBuffers(2, buffers, 0)
        buffers.forEach {
            GL.glBindBuffer(GL.GL_ARRAY_BUFFER, it)
            GL.glBufferData(GL.GL_ARRAY_BUFFER, grid.count * 28, null, GL.GL_DYNAMIC_DRAW)
        }
        GL.glGenTextures(1, textures, 0)
        GL.glBindTexture(GL.GL_TEXTURE_2D, textures[0])
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR)
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR)
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE)
        GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GL.GL_TEXTURE_2D, 0, bitmap, 0)
        GL.glViewport(0, 0, width, height)
        GL.glDisable(GL.GL_BLEND)
        GL.glClearColor(0f, 0f, 0f, 0f)
        GL.glUseProgram(program)
        GL.glUniform2f(location("size"), width.toFloat(), height.toFloat())
    }

    private fun compile(type: Int, resource: Int): Int {
        val shader = GL.glCreateShader(type)
        GL.glShaderSource(shader, resources.openRawResource(resource).bufferedReader().use { it.readText() })
        GL.glCompileShader(shader)
        val status = IntArray(1)
        GL.glGetShaderiv(shader, GL.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GL.GL_TRUE) {
            val error = GL.glGetShaderInfoLog(shader)
            GL.glDeleteShader(shader)
            error(error)
        }
        return shader
    }

    private fun location(name: String) = uniforms.getValue(name)
    private fun uniform(name: String, value: Float) = GL.glUniform1f(location(name), value)

    private fun draw() {
        val nanos = System.nanoTime()
        val delta = if (previousNanos == 0L) 0f else (nanos - previousNanos) / 1_000_000_000f
        previousNanos = nanos
        time += delta * 1.15f
        if (time > longevity + if (photo) 2f else .9f) { clean(); return }
        GL.glClear(GL.GL_COLOR_BUFFER_BIT)
        GL.glUseProgram(program)
        GL.glBindVertexArray(arrays[0])
        val matrix = floatArrayOf(bounds.width(), 0f, 0f, 0f, bounds.height(), 0f, bounds.left, bounds.top, 1f)
        GL.glUniformMatrix3fv(location("matrix"), 1, false, matrix, 0)
        uniform("reset", if (first) 1f else 0f)
        uniform("time", time)
        uniform("deltaTime", delta * 1.15f)
        uniform("particlesCount", grid.count.toFloat())
        GL.glUniform3f(location("gridSize"), grid.columns.toFloat(), grid.rows.toFloat(), grid.size)
        GL.glUniform2f(location("rectSize"), bounds.width(), bounds.height())
        GL.glUniform2f(location("offset"), 0f, 0f)
        uniform("seed", seed)
        uniform("dp", density)
        uniform("longevity", longevity)
        uniform("scale", if (photo) .8f else 1f)
        uniform("uvOffset", if (photo) 1f else .6f)
        GL.glActiveTexture(GL.GL_TEXTURE0)
        GL.glBindTexture(GL.GL_TEXTURE_2D, textures[0])
        GL.glUniform1i(location("tex"), 0)
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER, buffers[currentBuffer])
        for ((attribute, count) in listOf(2, 2, 2, 1).withIndex()) {
            GL.glVertexAttribPointer(attribute, count, GL.GL_FLOAT, false, 28, attribute * 8)
            GL.glEnableVertexAttribArray(attribute)
        }
        GL.glBindBufferBase(GL.GL_TRANSFORM_FEEDBACK_BUFFER, 0, buffers[1 - currentBuffer])
        GL.glBeginTransformFeedback(GL.GL_POINTS)
        GL.glDrawArrays(GL.GL_POINTS, 0, grid.count)
        GL.glEndTransformFeedback()
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER, 0)
        GL.glBindBuffer(GL.GL_TRANSFORM_FEEDBACK_BUFFER, 0)
        if (first) check(GL.glGetError() == GL.GL_NO_ERROR) { "GPU dust first frame failed" }
        check(EGL14.eglSwapBuffers(display, eglSurface))
        first = false
        currentBuffer = 1 - currentBuffer
    }

    private fun fail(failure: RuntimeException) {
        Log.w("PhotoDust", "GPU dust unavailable", failure)
        clean()
    }

    private fun clean() {
        if (cleaned) return
        cleaned = true
        running.set(false)
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (eglContext != EGL14.EGL_NO_CONTEXT && eglSurface != EGL14.EGL_NO_SURFACE) {
                GL.glDeleteBuffers(2, buffers, 0)
                GL.glDeleteTextures(1, textures, 0)
                GL.glDeleteVertexArrays(1, arrays, 0)
                if (program != 0) GL.glDeleteProgram(program)
            }
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
            EGL14.eglReleaseThread()
        }
        window?.release()
        texture.release()
        main.post(finished)
        thread.quitSafely()
    }
}
