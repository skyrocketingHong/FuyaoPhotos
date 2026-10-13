package ing.fuyaoskyrocket.photoinfo.platform.graphics

import android.annotation.SuppressLint
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
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoMotionTokens
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoReassemblyGeometry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

// AndroidView supplies the transition snapshots; this view is not inflated from XML.
@SuppressLint("ViewConstructor")
internal class PhotoReassemblyView(
    context: Context,
    private val source: Bitmap,
    private val sourceBounds: RectF,
    private val target: Bitmap,
    private val targetBounds: RectF,
    private val density: Float,
    private val onReady: () -> Unit,
    private val onComplete: () -> Unit,
    private val onFailure: (String) -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var worker: ReassemblyWorker? = null
    private var surfaceWidth = 0
    private var ready = false
    private var released = false
    private var completed = false
    private val choreographer = Choreographer.getInstance()
    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val active = worker ?: return
            if (released || !active.running.get()) return
            active.frame()
            if (!active.hasTerminalFrame()) choreographer.postFrameCallback(this)
        }
    }

    init {
        alpha = 0f
        isOpaque = false
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (released) return
        surfaceWidth = width
        try {
            val next = ReassemblyWorker(context, surface, width, height, source, sourceBounds, target, targetBounds, density, ::fail)
            worker = next
            next.start()
            choreographer.postFrameCallback(callback)
        } catch (error: RuntimeException) {
            Log.w("PhotoReassembly", "stage=worker_start type=${error.javaClass.simpleName}")
            fail("worker_start")
        } catch (error: OutOfMemoryError) {
            Log.w("PhotoReassembly", "stage=worker_start type=${error.javaClass.simpleName}")
            fail("worker_start")
        }
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        if (released || completed) return
        val active = worker ?: return
        val timestamp = try { surface.timestamp } catch (_: RuntimeException) { fail("surface_timestamp"); return }
        if (!ready) {
            val first = active.firstPresentation.get()
            if (first <= 0L || timestamp < first) return
            ready = true
            onReady()
        }
        if (active.hasPresentedTerminalFrame(timestamp)) {
            completed = true
            choreographer.removeFrameCallback(callback)
            onComplete()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (released || completed) return
        if (width != surfaceWidth) { fail("viewport_width_changed"); return }
        worker?.resize(width, height)
        choreographer.removeFrameCallback(callback)
        choreographer.postFrameCallback(callback)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (!released && !completed) fail("surface_destroyed") else release()
        if (worker == null) surface.release()
        return false
    }

    private fun fail(stage: String) {
        if (released || completed) return
        release()
        onFailure(stage)
    }

    fun release() {
        if (released) return
        released = true
        choreographer.removeFrameCallback(callback)
        worker?.stop()
    }

    fun handOffToCompose() {
        alpha = 0f
        release()
    }

    fun showPresentedFrame() {
        if (ready && !released) alpha = 1f
    }

    override fun onDetachedFromWindow() {
        if (!released && !completed) fail("view_detached") else release()
        super.onDetachedFromWindow()
    }
}

private class ReassemblyWorker(
    context: Context,
    private val texture: SurfaceTexture,
    private var width: Int,
    private var height: Int,
    private val source: Bitmap,
    private val sourceBounds: RectF,
    private val target: Bitmap,
    private val targetBounds: RectF,
    private val density: Float,
    private val failure: (String) -> Unit,
) {
    val running = AtomicBoolean(true)
    val firstPresentation = AtomicLong(0L)
    private data class TerminalFrame(val timestamp: Long, val viewportGeneration: Long)
    private val terminalFrame = AtomicReference<TerminalFrame?>(null)
    private val viewportGeneration = AtomicLong(0L)
    private var appliedViewportGeneration = 0L
    private val queued = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("PhotoReassembly")
    private lateinit var handler: Handler
    private val resources = context.resources
    private val grid = PhotoReassemblyGeometry.grid(sourceBounds.width(), sourceBounds.height(), targetBounds.width(), targetBounds.height(), density)
    private val radius = PhotoReassemblyGeometry.scatterRadius(sourceBounds.width(), sourceBounds.height(), density)
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var window: Surface? = null
    private var program = 0
    private val textures = IntArray(2)
    private val arrays = IntArray(1)
    private val uniforms = mutableMapOf<String, Int>()
    private var initialized = false
    private var cleaned = false
    private var firstFrameNanos = 0L
    private var stage = "initialization"

    fun start() {
        thread.start()
        handler = Handler(thread.looper)
        handler.post {
            if (!running.get()) { clean(); return@post }
            try { initialize(); initialized = true }
            catch (error: RuntimeException) { fail(error) }
            catch (error: OutOfMemoryError) { fail(error) }
        }
    }

    fun frame() {
        if (!running.get() || hasTerminalFrame() || !queued.compareAndSet(false, true)) return
        handler.post {
            try { if (running.get() && initialized && !hasTerminalFrame()) draw() }
            catch (error: RuntimeException) { fail(error) }
            catch (error: OutOfMemoryError) { fail(error) }
            finally { queued.set(false) }
        }
    }

    fun resize(newWidth: Int, newHeight: Int) {
        val generation = viewportGeneration.incrementAndGet()
        terminalFrame.set(null)
        handler.post {
            if (!running.get()) return@post
            width = newWidth
            height = newHeight
            appliedViewportGeneration = generation
            if (initialized) GL.glViewport(0, 0, width, height)
        }
    }

    fun hasTerminalFrame(): Boolean = terminalFrame.get()?.viewportGeneration == viewportGeneration.get()

    fun hasPresentedTerminalFrame(timestamp: Long): Boolean {
        val terminal = terminalFrame.get() ?: return false
        return terminal.viewportGeneration == viewportGeneration.get() && timestamp >= terminal.timestamp
    }

    fun stop() {
        running.set(false)
        if (::handler.isInitialized) handler.post { clean() }
        else {
            texture.release()
            thread.quitSafely()
        }
    }

    private fun initialize() {
        stage = "egl"
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
        stage = "shader"
        val vertex = compile(GL.GL_VERTEX_SHADER, R.raw.fuyao_photo_reassembly_vertex)
        val fragment = try { compile(GL.GL_FRAGMENT_SHADER, R.raw.fuyao_photo_reassembly_fragment) }
        catch (error: RuntimeException) { GL.glDeleteShader(vertex); throw error }
        try {
            program = GL.glCreateProgram()
            GL.glAttachShader(program, vertex)
            GL.glAttachShader(program, fragment)
            GL.glLinkProgram(program)
            val status = IntArray(1)
            GL.glGetProgramiv(program, GL.GL_LINK_STATUS, status, 0)
            check(status[0] == GL.GL_TRUE)
        } finally { GL.glDeleteShader(vertex); GL.glDeleteShader(fragment) }
        listOf("viewportSize", "sourceRect", "targetRect", "gridSize", "travel", "scatter", "scatterRadius", "cellScale",
            "sourceTexture", "targetTexture", "colorProgress").forEach { uniforms[it] = GL.glGetUniformLocation(program, it) }
        GL.glGenVertexArrays(1, arrays, 0)
        GL.glBindVertexArray(arrays[0])
        stage = "textures"
        val maximumTexture = IntArray(1)
        GL.glGetIntegerv(GL.GL_MAX_TEXTURE_SIZE, maximumTexture, 0)
        check(listOf(source.width, source.height, target.width, target.height).all { it <= maximumTexture[0] })
        GL.glGenTextures(2, textures, 0)
        listOf(source, target).forEachIndexed { index, bitmap ->
            GL.glBindTexture(GL.GL_TEXTURE_2D, textures[index])
            GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR)
            GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR)
            GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE)
            GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GL.GL_TEXTURE_2D, 0, bitmap, 0)
        }
        GL.glViewport(0, 0, width, height)
        GL.glEnable(GL.GL_BLEND)
        GL.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA)
        GL.glClearColor(0f, 0f, 0f, 0f)
        check(GL.glGetError() == GL.GL_NO_ERROR)
    }

    private fun compile(type: Int, resource: Int): Int {
        val shader = GL.glCreateShader(type)
        GL.glShaderSource(shader, resources.openRawResource(resource).bufferedReader().use { it.readText() })
        GL.glCompileShader(shader)
        val status = IntArray(1)
        GL.glGetShaderiv(shader, GL.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GL.GL_TRUE) { GL.glDeleteShader(shader); error("shader_compile") }
        return shader
    }

    private fun location(name: String) = uniforms.getValue(name)
    private fun uniform(name: String, value: Float) = GL.glUniform1f(location(name), value)

    private fun draw() {
        stage = "draw"
        val nanos = System.nanoTime()
        if (firstFrameNanos == 0L) firstFrameNanos = nanos
        val t = ((nanos - firstFrameNanos) / (PhotoMotionTokens.reassemblyMillis * 1_000_000f)).coerceIn(0f, 1f)
        val progress = PhotoReassemblyGeometry.progress(t)
        GL.glClear(GL.GL_COLOR_BUFFER_BIT)
        GL.glUseProgram(program)
        GL.glBindVertexArray(arrays[0])
        GL.glUniform2f(location("viewportSize"), width.toFloat(), height.toFloat())
        GL.glUniform4f(location("sourceRect"), sourceBounds.left, sourceBounds.top, sourceBounds.width(), sourceBounds.height())
        GL.glUniform4f(location("targetRect"), targetBounds.left, targetBounds.top, targetBounds.width(), targetBounds.height())
        GL.glUniform2f(location("gridSize"), grid.columns.toFloat(), grid.rows.toFloat())
        uniform("travel", progress.travel)
        uniform("scatter", progress.scatter)
        uniform("scatterRadius", radius)
        uniform("cellScale", progress.cellScale)
        uniform("colorProgress", progress.color)
        GL.glActiveTexture(GL.GL_TEXTURE0)
        GL.glBindTexture(GL.GL_TEXTURE_2D, textures[0])
        GL.glUniform1i(location("sourceTexture"), 0)
        GL.glActiveTexture(GL.GL_TEXTURE1)
        GL.glBindTexture(GL.GL_TEXTURE_2D, textures[1])
        GL.glUniform1i(location("targetTexture"), 1)
        GL.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, grid.count)
        check(GL.glGetError() == GL.GL_NO_ERROR)
        stage = "presentation"
        check(EGLExt.eglPresentationTimeANDROID(display, eglSurface, nanos))
        firstPresentation.compareAndSet(0L, nanos)
        if (t == 1f) terminalFrame.set(TerminalFrame(nanos, appliedViewportGeneration))
        check(EGL14.eglSwapBuffers(display, eglSurface))
    }

    private fun fail(error: Throwable) {
        val failedStage = stage
        Log.w("PhotoReassembly", "stage=$failedStage type=${error.javaClass.simpleName}")
        clean()
        main.post { failure(failedStage) }
    }

    private fun clean() {
        if (cleaned) return
        cleaned = true
        running.set(false)
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (eglContext != EGL14.EGL_NO_CONTEXT && eglSurface != EGL14.EGL_NO_SURFACE) {
                GL.glDeleteTextures(2, textures, 0)
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
        thread.quitSafely()
    }
}
