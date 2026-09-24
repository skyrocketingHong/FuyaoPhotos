package ing.fuyaoskyrocket.photoinfo.ui.components

import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Observe a committed system exit without taking ownership of its predictive animation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemBackObserver(enabled: Boolean, onSystemBack: () -> Unit) {
    val activity = LocalActivity.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val currentAction by rememberUpdatedState(onSystemBack)
    val active = enabled && !WindowInsets.isImeVisible && lifecycle == Lifecycle.State.RESUMED
    DisposableEffect(activity, active) {
        if (Build.VERSION.SDK_INT >= 36 && activity != null && active) {
            val callback = OnBackInvokedCallback { currentAction() }
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM_NAVIGATION_OBSERVER, callback)
            onDispose { activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback) }
        } else onDispose { }
    }
}
