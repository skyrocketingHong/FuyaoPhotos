package ing.fuyaoskyrocket.photoinfo

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES30 as GL
import android.opengl.GLUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.geometry.Rect
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoMotionTokens
import ing.fuyaoskyrocket.photoinfo.domain.motion.PhotoReassemblyGeometry
import ing.fuyaoskyrocket.photoinfo.ui.components.PhotoDissolveController
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoReassemblyShaderTest {
    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun hardwareBitmapCaptureUsesAnIndependentSoftwareSnapshot() {
        val pixels = quadrants(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE)
        val bytes = ByteArrayOutputStream().use { output ->
            pixels.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        pixels.recycle()
        val hardware = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
        }
        try {
            assertEquals(Bitmap.Config.HARDWARE, hardware.config)
            var captured = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val controller = PhotoDissolveController()
                controller.register("metadata-photo", Any(), hardware, Rect(0f, 0f, 40f, 40f), null)
                controller.reassemble("metadata-photo")
                captured = controller.reassemblyRequest("metadata-photo") != null
                controller.clear()
            }
            assertTrue(captured)
            assertFalse(hardware.isRecycled)
        } finally { hardware.recycle() }
    }

    @Test fun endpointsKeepTheTextureOrientationPremultipliedAlphaAndExactRectangles() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        assumeTrue(EGL14.eglInitialize(display, version, 0, version, 1))
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        var program = 0
        val textures = IntArray(2)
        val arrays = IntArray(1)
        val bitmaps = listOf(
            quadrants(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE),
            quadrants(Color.argb(128, 0, 255, 255), Color.MAGENTA, Color.YELLOW, Color.BLACK),
        )
        try {
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val attributes = intArrayOf(EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR, EGL14.EGL_NONE)
            assumeTrue(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0)
            val config = requireNotNull(configs[0])
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            assertNotEquals(EGL14.EGL_NO_CONTEXT, context)
            surface = EGL14.eglCreatePbufferSurface(display, config,
                intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE), 0)
            assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
            val vertex = compile(GL.GL_VERTEX_SHADER, R.raw.fuyao_photo_reassembly_vertex)
            val fragment = compile(GL.GL_FRAGMENT_SHADER, R.raw.fuyao_photo_reassembly_fragment)
            program = GL.glCreateProgram()
            GL.glAttachShader(program, vertex)
            GL.glAttachShader(program, fragment)
            GL.glLinkProgram(program)
            GL.glDeleteShader(vertex)
            GL.glDeleteShader(fragment)
            val linked = IntArray(1)
            GL.glGetProgramiv(program, GL.GL_LINK_STATUS, linked, 0)
            assertEquals(GL.glGetProgramInfoLog(program), GL.GL_TRUE, linked[0])
            GL.glUseProgram(program)
            GL.glGenVertexArrays(1, arrays, 0)
            GL.glBindVertexArray(arrays[0])
            GL.glGenTextures(2, textures, 0)
            bitmaps.forEachIndexed { index, bitmap ->
                GL.glActiveTexture(GL.GL_TEXTURE0 + index)
                GL.glBindTexture(GL.GL_TEXTURE_2D, textures[index])
                GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR)
                GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR)
                GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE)
                GL.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GL.GL_TEXTURE_2D, 0, bitmap, 0)
            }
            fun location(name: String) = GL.glGetUniformLocation(program, name)
            GL.glUniform1i(location("sourceTexture"), 0)
            GL.glUniform1i(location("targetTexture"), 1)
            GL.glUniform2f(location("viewportSize"), 16f, 16f)
            GL.glUniform4f(location("sourceRect"), 0f, 0f, 8f, 8f)
            GL.glUniform4f(location("targetRect"), 8f, 8f, 8f, 8f)
            GL.glUniform2f(location("gridSize"), 2f, 2f)
            GL.glUniform1f(location("scatterRadius"), 16f)
            GL.glViewport(0, 0, 16, 16)
            GL.glEnable(GL.GL_BLEND)
            GL.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA)
            GL.glClearColor(0f, 0f, 0f, 0f)
            fun draw(t: Float) {
                val progress = PhotoReassemblyGeometry.progress(t)
                GL.glUniform1f(location("travel"), progress.travel)
                GL.glUniform1f(location("scatter"), progress.scatter)
                GL.glUniform1f(location("colorProgress"), progress.color)
                GL.glUniform1f(location("cellScale"), progress.cellScale)
                GL.glClear(GL.GL_COLOR_BUFFER_BIT)
                GL.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, 4)
                assertEquals(GL.GL_NO_ERROR, GL.glGetError())
            }
            draw(0f)
            assertPixel(2, 2, intArrayOf(255, 0, 0, 255))
            assertPixel(6, 2, intArrayOf(0, 255, 0, 255))
            assertPixel(2, 6, intArrayOf(0, 0, 255, 255))
            assertPixel(10, 10, intArrayOf(0, 0, 0, 0))
            for (phase in listOf(PhotoMotionTokens.reassemblySettleStart, .95f, 1f)) {
                draw(phase)
                assertPixel(2, 2, intArrayOf(0, 0, 0, 0))
                assertPixel(10, 10, intArrayOf(0, 128, 128, 128))
                assertPixel(14, 10, intArrayOf(255, 0, 255, 255))
                assertPixel(10, 14, intArrayOf(255, 255, 0, 255))
            }
        } finally {
            if (context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE) {
                GL.glDeleteTextures(2, textures, 0)
                GL.glDeleteVertexArrays(1, arrays, 0)
                if (program != 0) GL.glDeleteProgram(program)
            }
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            EGL14.eglReleaseThread()
            bitmaps.forEach(Bitmap::recycle)
        }
    }

    private fun compile(type: Int, resource: Int): Int {
        val shader = GL.glCreateShader(type)
        val source = InstrumentationRegistry.getInstrumentation().targetContext.resources.openRawResource(resource)
            .bufferedReader().use { it.readText() }
        GL.glShaderSource(shader, source)
        GL.glCompileShader(shader)
        val status = IntArray(1)
        GL.glGetShaderiv(shader, GL.GL_COMPILE_STATUS, status, 0)
        assertEquals(GL.glGetShaderInfoLog(shader), GL.GL_TRUE, status[0])
        return shader
    }

    private fun quadrants(topLeft: Int, topRight: Int, bottomLeft: Int, bottomRight: Int): Bitmap =
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            for (y in 0..3) for (x in 0..3) setPixel(x, y,
                if (y < 2) { if (x < 2) topLeft else topRight } else { if (x < 2) bottomLeft else bottomRight })
        }

    private fun assertPixel(x: Int, top: Int, expected: IntArray) {
        val pixel = ByteBuffer.allocateDirect(4)
        GL.glReadPixels(x, 15 - top, 1, 1, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixel)
        for (channel in 0..3) assertEquals(expected[channel].toDouble(), (pixel.get(channel).toInt() and 255).toDouble(), 1.0)
    }
}
