package ing.fuyaoskyrocket.photoinfo.domain.motion

class PhotoReassemblyLifecycle {
    data class Request(val generation: Long, val feature: String, val targetDeadline: Long, val deadline: Long)
    data class Target(val owner: Any, val bounds: Any, val appearance: Any) {
        fun matches(other: Target): Boolean = owner === other.owner && bounds == other.bounds && appearance == other.appearance
    }
    enum class Phase { WAITING_TARGET, ANIMATING, BRIDGE, NATIVE_READY }
    enum class Expiry { TARGET_WAIT, LIFETIME }

    private var generation = 0L
    var current: Request? = null
        private set
    var phase: Phase? = null
        private set
    private var target: Target? = null

    fun begin(feature: String, now: Long): Request {
        target = null
        phase = Phase.WAITING_TARGET
        return Request(++generation, feature,
            now + PhotoMotionTokens.reassemblyTargetWaitMillis,
            now + PhotoMotionTokens.reassemblyLifetimeMillis).also { current = it }
    }

    fun acceptsTarget(generation: Long, feature: String, target: Target, now: Long): Boolean {
        val request = validRequest(generation, feature, now) ?: return false
        if (phase != Phase.WAITING_TARGET || now >= request.targetDeadline) return false
        this.target = target
        phase = Phase.ANIMATING
        return true
    }

    fun acceptsReady(generation: Long, feature: String, now: Long): Boolean =
        validRequest(generation, feature, now) != null && phase == Phase.ANIMATING

    fun terminalPresented(generation: Long, feature: String, target: Target, now: Long): Boolean =
        advance(generation, feature, target, now, Phase.ANIMATING, Phase.BRIDGE)

    fun bridgeDrawn(generation: Long, feature: String, target: Target, now: Long): Boolean =
        advance(generation, feature, target, now, Phase.BRIDGE, Phase.NATIVE_READY)

    fun nativeRecorded(generation: Long, feature: String, target: Target, now: Long): Boolean {
        if (validRequest(generation, feature, now) == null || phase != Phase.NATIVE_READY ||
            this.target?.matches(target) != true) return false
        cancel()
        return true
    }

    private fun advance(generation: Long, feature: String, target: Target, now: Long, from: Phase, to: Phase): Boolean {
        if (validRequest(generation, feature, now) == null || phase != from || this.target?.matches(target) != true) return false
        phase = to
        return true
    }

    private fun validRequest(generation: Long, feature: String, now: Long): Request? = current?.takeIf {
        it.generation == generation && it.feature == feature && now < it.deadline
    }

    fun expiry(now: Long): Expiry? {
        val request = current ?: return null
        return when {
            now >= request.deadline -> Expiry.LIFETIME
            phase == Phase.WAITING_TARGET && now >= request.targetDeadline -> Expiry.TARGET_WAIT
            else -> null
        }
    }

    fun cancel() {
        generation++
        current = null
        phase = null
        target = null
    }
}
