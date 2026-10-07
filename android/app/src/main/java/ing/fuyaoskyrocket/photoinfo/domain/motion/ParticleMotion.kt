package ing.fuyaoskyrocket.photoinfo.domain.motion

object ParticleMotion {
    fun progress(completed: Int, total: Int): Float =
        if (total > 0) completed.coerceIn(0, total).toFloat() / total else 0f
}
