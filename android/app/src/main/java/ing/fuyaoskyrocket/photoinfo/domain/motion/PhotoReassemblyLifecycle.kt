package ing.fuyaoskyrocket.photoinfo.domain.motion

class PhotoReassemblyLifecycle {
    data class Request(val generation: Long, val feature: String, val targetDeadline: Long, val deadline: Long)
    enum class Expiry { TARGET_WAIT, LIFETIME }

    private var generation = 0L
    var current: Request? = null
        private set
    private var targetAccepted = false

    fun begin(feature: String, now: Long): Request {
        targetAccepted = false
        return Request(++generation, feature,
            now + PhotoMotionTokens.reassemblyTargetWaitMillis,
            now + PhotoMotionTokens.reassemblyLifetimeMillis).also { current = it }
    }

    fun acceptsTarget(generation: Long, feature: String, now: Long): Boolean {
        val request = current ?: return false
        if (request.generation != generation || request.feature != feature || targetAccepted ||
            now >= request.targetDeadline || now >= request.deadline) return false
        targetAccepted = true
        return true
    }

    fun expiry(now: Long): Expiry? {
        val request = current ?: return null
        return when {
            now >= request.deadline -> Expiry.LIFETIME
            !targetAccepted && now >= request.targetDeadline -> Expiry.TARGET_WAIT
            else -> null
        }
    }

    fun finish(generation: Long): Boolean {
        if (current?.generation != generation) return false
        cancel()
        return true
    }

    fun cancel() {
        generation++
        current = null
        targetAccepted = false
    }
}
