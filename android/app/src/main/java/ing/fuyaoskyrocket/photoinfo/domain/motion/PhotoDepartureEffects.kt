package ing.fuyaoskyrocket.photoinfo.domain.motion

/** A main-thread relay: a retained import operation dispatches to the currently attached UI. */
class PhotoDepartureEffects {
    private var receiverOwner: Any? = null
    private var receiver: ((String) -> Unit)? = null

    fun bind(owner: Any, receive: (String) -> Unit) {
        receiverOwner = owner
        receiver = receive
    }

    fun unbind(owner: Any) {
        if (receiverOwner !== owner) return
        receiverOwner = null
        receiver = null
    }

    fun dispatch(sourceId: String) { receiver?.invoke(sourceId) }
}
