package ing.fuyaoskyrocket.photoinfo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors=lightColorScheme(
    primary=Color(0xFF6750A4),onPrimary=Color.White,secondary=Color(0xFF625B71),tertiary=Color(0xFF7D5260),
    surface=Color(0xFFFEF7FF),onSurface=Color(0xFF1D1B20),onSurfaceVariant=Color(0xFF49454F),surfaceContainerLow=Color(0xFFF7F2FA))
private val DarkColors=darkColorScheme(
    primary=Color(0xFFD0BCFF),onPrimary=Color(0xFF381E72),secondary=Color(0xFFCCC2DC),tertiary=Color(0xFFEFB8C8),
    surface=Color(0xFF141218),onSurface=Color(0xFFE6E0E9),onSurfaceVariant=Color(0xFFCAC4D0),surfaceContainerLow=Color(0xFF1D1B20))
private val FuyaoTypography = Typography()
private val FuyaoShapes=Shapes(RoundedCornerShape(4.dp),RoundedCornerShape(8.dp),RoundedCornerShape(12.dp),RoundedCornerShape(16.dp),RoundedCornerShape(28.dp))

@Composable
fun PhotoInfoTheme(content:@Composable ()->Unit) {
    val dark=isSystemInDarkTheme();val context=LocalContext.current
    val colors=if(Build.VERSION.SDK_INT>=31) { if(dark)dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
        else if(dark)DarkColors else LightColors
    MaterialTheme(colorScheme=colors,typography=FuyaoTypography,shapes=FuyaoShapes,content=content)
}
