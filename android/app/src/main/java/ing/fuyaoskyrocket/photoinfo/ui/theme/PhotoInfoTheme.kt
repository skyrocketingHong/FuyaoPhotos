package ing.fuyaoskyrocket.photoinfo.ui.theme

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import ing.fuyaoskyrocket.photoinfo.domain.model.AppAppearance
import ing.fuyaoskyrocket.photoinfo.domain.model.WorkspaceSettings

@Composable
fun PhotoInfoTheme(
    appearance: AppAppearance = AppAppearance.SYSTEM,
    workspace: WorkspaceSettings = WorkspaceSettings(appearance = appearance),
    content: @Composable () -> Unit,
) {
    val dark = workspace.appearance.isDark(isSystemInDarkTheme())
    val seed = if (workspace.dynamicTheme && Build.VERSION.SDK_INT >= 31) {
        colorResource(android.R.color.system_accent1_500).toArgb()
    } else workspace.themeSeed.toInt()
    val colors = remember(seed, dark, workspace.themePalette, workspace.themeContrast, workspace.themeColorSpec, workspace.pureBlackTheme) {
        photoColorScheme(seed, dark, workspace.themePalette, workspace.themeContrast, workspace.themeColorSpec, workspace.pureBlackTheme)
    }
    val window = LocalActivity.current?.window
    SideEffect {
        window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalPhotoMotionEnabled provides rememberSystemMotionEnabled()) {
        MaterialTheme(colorScheme = colors, typography = Typography(), shapes = Shapes(), content = content)
    }
}
