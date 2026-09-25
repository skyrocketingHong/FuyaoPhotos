package ing.fuyaoskyrocket.photoinfo.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.view.SurfaceHolder
import java.io.FileInputStream

/** One playback session, released on completion, focus loss, navigation, or lifecycle pause. */
class MotionClipPlayer(
    context: Context,
    private val onSize: (Int, Int) -> Unit,
    private val onReady: () -> Unit,
    private val onFinished: () -> Unit,
    private val onError: () -> Unit,
) {
    private val audio=context.getSystemService(AudioManager::class.java)
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()
    private var player: MediaPlayer?=null
    private var released=false
    private var prepared=false
    private var hasFocus=false
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
            if(change<0 && !released) finish()
        }.build()

    fun prepare(source: MotionClipSource, holder: SurfaceHolder) {
        if(released || player!=null)return
        try {
            source.validate()
            val media=MediaPlayer().also { player=it }
            media.setAudioAttributes(attributes)
            media.setDisplay(holder)
            media.setOnVideoSizeChangedListener { _,width,height -> if(!released && width>0 && height>0)onSize(width,height) }
            media.setOnPreparedListener {
                if(!released) {
                    try {
                        prepared=true
                        hasFocus=audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                        if(hasFocus) { it.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);it.start();onReady() } else fail()
                    } catch (_: RuntimeException) { fail() }
                }
            }
            media.setOnCompletionListener { if(!released)finish() }
            media.setOnErrorListener { _,_,_ -> if(!released)fail();true }
            FileInputStream(source.file).use { media.setDataSource(it.fd,source.offset,source.length) }
            media.prepareAsync()
        } catch (_: Exception) { fail() }
    }
    private fun finish() { release();onFinished() }
    private fun fail() { release();onError() }
    fun release() {
        if(released)return
        released=true
        prepared=false
        player?.apply {
            setOnPreparedListener(null);setOnCompletionListener(null);setOnErrorListener(null);setOnVideoSizeChangedListener(null)
            release()
        }
        player=null
        if(hasFocus)audio.abandonAudioFocusRequest(focus)
        hasFocus=false
    }
}
