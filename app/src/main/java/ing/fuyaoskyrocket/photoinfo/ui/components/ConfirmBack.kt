package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import ing.fuyaoskyrocket.photoinfo.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/** Toolbar and committed back gestures share one confirmation; cancellation changes nothing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberConfirmedBack(onConfirmed: () -> Unit, enabled: Boolean = true,
    title: Int = R.string.discard_title, message: Int = R.string.discard_message,
    confirmLabel: Int = R.string.discard_return): () -> Unit {
    var show by rememberSaveable { mutableStateOf(false) }
    val currentConfirm by rememberUpdatedState(onConfirmed)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    PredictiveBackHandler(enabled = enabled && !show && !WindowInsets.isImeVisible && lifecycle == Lifecycle.State.RESUMED) { events ->
        try { events.collect { }; show = true }
        catch (_: CancellationException) { /* Stay on the same destination and retain all edits. */ }
    }
    if (show) AlertDialog(onDismissRequest = { show = false },
        title = { Text(stringResource(title)) }, text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = { if (show) { show = false; currentConfirm() } }) { Text(stringResource(confirmLabel)) } },
        dismissButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.continue_editing)) } })
    return { if (enabled) show = true }
}
