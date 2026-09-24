package ing.fuyaoskyrocket.photoinfo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.model.*
import ing.fuyaoskyrocket.photoinfo.presentation.*
import kotlin.math.roundToInt

@Composable
fun EditorControls(state:EditorState,onField:(FieldId,String)->Unit,onStyle:(CardStyle)->Unit,onResetField:(FieldId)->Unit,
    onImportFont:()->Unit,onResetFont:()->Unit,onResolveLocation:()->Unit,modifier:Modifier=Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var fieldIndex by rememberSaveable { mutableIntStateOf(0) }
    var styleIndex by rememberSaveable { mutableIntStateOf(0) }
    val focus=LocalFocusManager.current
    val field=FieldId.entries[fieldIndex]
    val styleLabels=listOf(R.string.card_scale,R.string.text_scale,R.string.opacity,R.string.blur,R.string.radius,R.string.right_inset,R.string.bottom_inset,R.string.font)
    val labels=(if(tab==0)FieldId.entries.map { if(it==FieldId.FOCAL_LENGTH)R.string.wheel_focal else fieldLabel(it) } else styleLabels).map { stringResource(it) }
    Column(modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))) {
        PrimaryTabRow(selectedTabIndex=tab,divider={}) {
            listOf(R.string.tab_info,R.string.tab_style).forEachIndexed { index,title ->
                Tab(selected=tab==index,onClick={ focus.clearFocus();tab=index },text={ Text(stringResource(title)) })
            }
        }
        Row(Modifier.fillMaxSize().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.Top) {
            key(tab) {
            val pickerTab=tab
            CyclicItemSelector(labels,if(tab==0)fieldIndex else styleIndex,!state.busy,Modifier.weight(.4f).fillMaxHeight()) {
                if(tab==pickerTab && it in labels.indices) {
                    focus.clearFocus()
                    if(pickerTab==0)fieldIndex=it else styleIndex=it
                }
            }
            }
            Column(Modifier.weight(.6f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top=24.dp,bottom=16.dp),
                verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Text(labels[if(tab==0)fieldIndex else styleIndex],style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
                if(tab==0) {
                    Text(stringResource(fieldHint(field)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(state.info[field],{ onField(field,it) },Modifier.fillMaxWidth(),enabled=!state.busy,
                        label={ Text(stringResource(fieldLabel(field))) },minLines=1,maxLines=4,
                        keyboardOptions=KeyboardOptions(keyboardType=when(field) {
                            FieldId.ISO -> KeyboardType.Number; FieldId.APERTURE -> KeyboardType.Decimal; else -> KeyboardType.Text
                        }))
                    if(field==FieldId.LOCATION && state.hasPhotoGps && state.settings.resolvePhotoLocation) {
                        if(state.locationStatus==LocationStatus.RESOLVING)LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(onClick=onResolveLocation,enabled=!state.busy && state.locationStatus!=LocationStatus.RESOLVING) {
                            Text(stringResource(R.string.location_retry))
                        }
                    }
                    TextButton(onClick={ onResetField(field) },enabled=!state.busy) { Text(stringResource(R.string.restore_selected)) }
                } else if(styleIndex==7) {
                    Text(state.fontName ?: stringResource(R.string.system_mono),style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick=onImportFont,enabled=!state.busy) { Text(stringResource(R.string.import_font)) }
                    TextButton(onClick=onResetFont,enabled=!state.busy&&state.hasCustomFont) { Text(stringResource(R.string.reset)) }
                } else {
                    val s=state.style
                    val values=listOf(s.scale,s.textScale,s.opacity,s.blur,s.cornerRadius,s.rightInset,s.bottomInset)
                    val ranges=listOf(.6f..2f,.8f..1.8f,0f..1f,0f..50f,0f..40f,0f..250f,0f..250f)
                    val hints=listOf(R.string.card_scale_hint,R.string.text_scale_hint,R.string.opacity_hint,R.string.blur_hint,R.string.radius_hint,R.string.inset_hint,R.string.inset_hint)
                    Text(stringResource(hints[styleIndex]),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(if(styleIndex<3)R.string.value_percent else R.string.value_pixels,
                        (values[styleIndex]*(if(styleIndex<3)100 else 1)).roundToInt()),style=MaterialTheme.typography.labelLarge)
                    Slider(values[styleIndex],{ value ->
                        onStyle(when(styleIndex) {
                            0->s.copy(scale=value);1->s.copy(textScale=value);2->s.copy(opacity=value);3->s.copy(blur=value)
                            4->s.copy(cornerRadius=value);5->s.copy(rightInset=value);else->s.copy(bottomInset=value)
                        })
                    },enabled=!state.busy,valueRange=ranges[styleIndex])
                    TextButton(onClick={
                        val defaults=CardStyle()
                        onStyle(when(styleIndex) {
                            0->s.copy(scale=defaults.scale);1->s.copy(textScale=defaults.textScale);2->s.copy(opacity=defaults.opacity)
                            3->s.copy(blur=defaults.blur);4->s.copy(cornerRadius=defaults.cornerRadius)
                            5->s.copy(rightInset=defaults.rightInset);else->s.copy(bottomInset=defaults.bottomInset)
                        })
                    },enabled=!state.busy) { Text(stringResource(R.string.restore_selected)) }
                }
            }
        }
    }
}
private fun fieldLabel(field:FieldId)=when(field) {
    FieldId.DEVICE->R.string.field_device;FieldId.AUTHOR->R.string.field_author;FieldId.LOCATION->R.string.field_location
    FieldId.CAMERA->R.string.field_camera;FieldId.IMAGE_SIZE->R.string.field_size;FieldId.FOCAL_LENGTH->R.string.field_focal
    FieldId.EXPOSURE->R.string.field_exposure;FieldId.APERTURE->R.string.field_aperture;FieldId.ISO->R.string.field_iso
}
private fun fieldHint(field:FieldId)=when(field) {
    FieldId.DEVICE->R.string.card_device_hint;FieldId.AUTHOR->R.string.card_author_hint;FieldId.LOCATION->R.string.location_hint
    FieldId.CAMERA->R.string.card_lens_hint;FieldId.IMAGE_SIZE->R.string.card_pixels_hint;FieldId.FOCAL_LENGTH->R.string.card_focal_hint
    FieldId.EXPOSURE->R.string.card_exposure_hint;FieldId.APERTURE->R.string.card_aperture_hint;FieldId.ISO->R.string.card_iso_hint
}
