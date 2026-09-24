package ing.fuyaoskyrocket.photoinfo.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield

internal data class BatchFailure(val index: Int, val message: String)
internal data class BatchProgress(val completed: Int, val total: Int, val failed: Int)
internal data class BatchResult<T>(val saved: List<T>, val failures: List<BatchFailure>)

/** One full-resolution export at a time. A failed item does not discard other successful exports. */
internal suspend fun <T, R> runBatch(items: List<T>, describe: (Throwable) -> String,
    onProgress: (BatchProgress) -> Unit, export: suspend (Int, T) -> R): BatchResult<R> {
    val saved = mutableListOf<R>()
    val failures = mutableListOf<BatchFailure>()
    onProgress(BatchProgress(0, items.size, 0))
    items.forEachIndexed { index, item ->
        yield()
        try { saved += export(index, item) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { failures += BatchFailure(index, describe(failure)) }
        catch (failure: OutOfMemoryError) { failures += BatchFailure(index, describe(failure)) }
        onProgress(BatchProgress(index + 1, items.size, failures.size))
    }
    return BatchResult(saved.toList(), failures.toList())
}
