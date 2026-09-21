package ing.fuyaoskyrocket.photoinfo.ui.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R

object FuyaoSpacing {
    val xs=4.dp;val small=8.dp;val compact=12.dp;val content=16.dp;val large=24.dp;val extraLarge=32.dp
}
object FuyaoLayout {
    val appBar=48.dp;val appBarIcon=28.dp;val readable=840.dp;val editor=1040.dp;val inspector=360.dp
}
object FuyaoMotion {
    const val resetMillis=200
    val standard=CubicBezierEasing(.2f,0f,0f,1f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuyaoScaffold(title:String,modifier:Modifier=Modifier,onBack:(()->Unit)?=null,
    actions:@Composable RowScope.()->Unit={},snackbarHost:@Composable ()->Unit={},content:@Composable (PaddingValues)->Unit) {
    Scaffold(modifier=modifier.fillMaxSize(),containerColor=MaterialTheme.colorScheme.surface,
        contentWindowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar={ TopAppBar(
            title={ Text(title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold) },
            navigationIcon={ if(onBack!=null)FuyaoAppBarAction(R.drawable.ic_back,stringResource(R.string.back),onBack) },
            actions=actions,expandedHeight=FuyaoLayout.appBar,
            windowInsets=WindowInsets.statusBars.only(WindowInsetsSides.Top).union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.surface,actionIconContentColor=MaterialTheme.colorScheme.onSurfaceVariant)) },
        snackbarHost=snackbarHost,content=content)
}

/** ColorPicker's compact toolbar geometry, without changing buttons in photo content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuyaoAppBarAction(icon:Int,label:String,onClick:()->Unit,enabled:Boolean=true) {
    TooltipBox(
        positionProvider=TooltipDefaults.rememberTooltipPositionProvider(positioning=TooltipAnchorPosition.Above),
        tooltip={ PlainTooltip { Text(label) } },
        state=rememberTooltipState(),
    ) {
        IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp)) {
            Icon(painterResource(icon),label,Modifier.size(FuyaoLayout.appBarIcon))
        }
    }
}

@Composable
fun FuyaoIconButton(icon:Int,label:String,onClick:()->Unit,enabled:Boolean=true) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp)) {
        Icon(painterResource(icon),label,Modifier.size(24.dp))
    }
}

@Composable
fun FuyaoFormPage(padding:PaddingValues,content:@Composable ColumnScope.()->Unit) {
    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),contentAlignment=Alignment.TopCenter) {
        Column(Modifier.widthIn(max=FuyaoLayout.readable).fillMaxWidth().verticalScroll(rememberScrollState()).padding(FuyaoSpacing.content),
            verticalArrangement=Arrangement.spacedBy(FuyaoSpacing.content)) {
            content()
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
fun SectionHeading(text:String,description:String?=null) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(text,Modifier.semantics { heading() },color=MaterialTheme.colorScheme.onSurfaceVariant,
            style=MaterialTheme.typography.titleSmall)
        if(description!=null)Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
