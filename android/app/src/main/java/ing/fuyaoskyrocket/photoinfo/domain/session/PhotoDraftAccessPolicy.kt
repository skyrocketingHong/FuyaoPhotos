package ing.fuyaoskyrocket.photoinfo.domain.session

/** Source IDs are unique private copies. Once discarded, a late callback cannot reopen one. */
class PhotoDraftAccessPolicy {
    private val invalidated = mutableSetOf<String>()
    private var sourceIsCurrent: (String, String) -> Boolean = { _, _ -> false }

    fun updateSourcePolicy(policy: (id: String, path: String) -> Boolean) { sourceIsCurrent = policy }
    fun allows(id: String, path: String): Boolean = id !in invalidated && sourceIsCurrent(id, path)
    fun isInvalidated(id: String): Boolean = id in invalidated
    fun invalidate(ids: Set<String>) { invalidated += ids }
}
