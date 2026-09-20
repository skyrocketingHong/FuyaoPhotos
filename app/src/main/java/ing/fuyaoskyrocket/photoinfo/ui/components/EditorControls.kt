package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.model.*
import ing.fuyaoskyrocket.photoinfo.presentation.*
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*
import kotlin.math.roundToInt

@Composable
fun EditorControls(state:EditorState,onField:(FieldId,String)->Unit,onStyle:(CardStyle)->Unit,onResetFields:()->Unit,
    onImportFont:()->Unit,onResetFont:()->Unit,onResolveLocation:()->Unit,modifier:Modifier=Modifier) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val infoScroll=rememberScrollState();val styleScroll=rememberScrollState()
    val enabled=!state.busy
    Column(modifier) {
        PrimaryTabRow(selectedTabIndex=selected,containerColor=MaterialTheme.colorScheme.surfaceContainerLow) {
            listOf(R.string.tab_info,R.string.tab_style).forEachIndexed { index,title ->
                Tab(selected=selected==index,onClick={ selected=index },text={ Text(stringResource(title)) })
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val paired=maxWidth>=340.dp && LocalDensity.current.fontScale<=1.3f
            Column(Modifier.fillMaxSize().verticalScroll(if(selected==0)infoScroll else styleScroll).padding(FuyaoSpacing.content),
                verticalArrangement=Arrangement.spacedBy(FuyaoSpacing.compact)) {
                if(selected==0) {
                    SectionHeading(stringResource(R.string.section_credit),stringResource(R.string.field_hint))
                    for(field in listOf(FieldId.DEVICE,FieldId.AUTHOR,FieldId.LOCATION))InfoField(field,state,onField)
                    LocationFeedback(state,onResolveLocation)
                    Spacer(Modifier.height(4.dp))
                    SectionHeading(stringResource(R.string.section_capture))
                    InfoField(FieldId.CAMERA,state,onField)
                    InfoField(FieldId.FOCAL_LENGTH,state,onField)
                    val rows=listOf(listOf(FieldId.IMAGE_SIZE,FieldId.EXPOSURE),listOf(FieldId.APERTURE,FieldId.ISO))
                    rows.forEach { fields ->
                        if(paired)Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            fields.forEach { InfoField(it,state,onField,Modifier.weight(1f)) }
                        } else fields.forEach { InfoField(it,state,onField) }
                    }
                    TextButton(onClick=onResetFields,enabled=enabled) { Text(stringResource(R.string.restore_metadata)) }
                } else {
                    val s=state.style
                    SectionHeading(stringResource(R.string.section_layout))
                    StyleSlider(stringResource(R.string.card_scale),"${(s.scale*100).roundToInt()}%",s.scale,.6f..2f,enabled) { onStyle(s.copy(scale=it)) }
                    StyleSlider(stringResource(R.string.text_scale),"${(s.textScale*100).roundToInt()}%",s.textScale,.8f..1.8f,enabled) { onStyle(s.copy(textScale=it)) }
                    Text(stringResource(R.string.text_scale_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    SectionHeading(stringResource(R.string.section_backdrop))
                    StyleSlider(stringResource(R.string.opacity),"${(s.opacity*100).roundToInt()}%",s.opacity,0f..1f,enabled) { onStyle(s.copy(opacity=it)) }
                    StyleSlider(stringResource(R.string.blur),"${s.blur.roundToInt()} px",s.blur,0f..50f,enabled) { onStyle(s.copy(blur=it)) }
                    StyleSlider(stringResource(R.string.radius),"${s.cornerRadius.roundToInt()} px",s.cornerRadius,0f..40f,enabled) { onStyle(s.copy(cornerRadius=it)) }
                    SectionHeading(stringResource(R.string.section_position),stringResource(R.string.style_hint))
                    StyleSlider(stringResource(R.string.right_inset),"${s.rightInset.roundToInt()} px",s.rightInset,0f..250f,enabled) { onStyle(s.copy(rightInset=it)) }
                    StyleSlider(stringResource(R.string.bottom_inset),"${s.bottomInset.roundToInt()} px",s.bottomInset,0f..250f,enabled) { onStyle(s.copy(bottomInset=it)) }
                    TextButton(onClick={ onStyle(CardStyle()) },enabled=enabled) { Text(stringResource(R.string.reset_style)) }
                    HorizontalDivider()
                    SectionHeading(stringResource(R.string.font),state.fontName ?: stringResource(R.string.system_mono))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick=onImportFont,enabled=enabled) { Text(stringResource(R.string.import_font)) }
                        TextButton(onClick=onResetFont,enabled=enabled&&state.hasCustomFont) { Text(stringResource(R.string.reset)) }
                    }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun InfoField(field:FieldId,state:EditorState,onField:(FieldId,String)->Unit,modifier:Modifier=Modifier) {
    val multiline=field in setOf(FieldId.DEVICE,FieldId.AUTHOR,FieldId.LOCATION,FieldId.CAMERA)
    OutlinedTextField(state.info[field],{ onField(field,it) },modifier.fillMaxWidth(),enabled=!state.busy,
        label={ Text(stringResource(fieldLabel(field))) },singleLine=!multiline,maxLines=if(multiline)3 else 1,
        keyboardOptions=KeyboardOptions(keyboardType=when(field) { FieldId.ISO->KeyboardType.Number;FieldId.APERTURE->KeyboardType.Decimal;else->KeyboardType.Text }),
        shape=MaterialTheme.shapes.medium)
}

@Composable
private fun LocationFeedback(state:EditorState,retry:()->Unit) {
    val message=when(state.locationStatus) {
        LocationStatus.RESOLVING->R.string.location_resolving;LocationStatus.RESOLVED->R.string.location_resolved
        LocationStatus.UNAVAILABLE->R.string.location_unavailable;LocationStatus.NO_GPS->R.string.location_no_gps
        LocationStatus.DISABLED->R.string.location_disabled;LocationStatus.IDLE->R.string.location_hint
    }
    Text(stringResource(message),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(state.hasPhotoGps && state.settings.resolvePhotoLocation)TextButton(onClick=retry,enabled=!state.busy&&state.locationStatus!=LocationStatus.RESOLVING) {
        Text(stringResource(R.string.location_retry))
    }
}

@Composable
private fun StyleSlider(label:String,display:String,value:Float,range:ClosedFloatingPointRange<Float>,enabled:Boolean,onChange:(Float)->Unit) {
    Column {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
            Text(display,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value,onChange,Modifier.semantics { contentDescription=label },enabled=enabled,valueRange=range)
    }
}
private fun fieldLabel(field:FieldId)=when(field) {
    FieldId.DEVICE->R.string.field_device;FieldId.AUTHOR->R.string.field_author;FieldId.LOCATION->R.string.field_location
    FieldId.CAMERA->R.string.field_camera;FieldId.IMAGE_SIZE->R.string.field_size;FieldId.FOCAL_LENGTH->R.string.field_focal
    FieldId.EXPOSURE->R.string.field_exposure;FieldId.APERTURE->R.string.field_aperture;FieldId.ISO->R.string.field_iso
}
