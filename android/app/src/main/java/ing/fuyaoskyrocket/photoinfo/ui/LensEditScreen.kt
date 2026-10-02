package ing.fuyaoskyrocket.photoinfo.ui

import ing.fuyaoskyrocket.photoinfo.ui.components.rememberConfirmedBack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ing.fuyaoskyrocket.photoinfo.R
import ing.fuyaoskyrocket.photoinfo.domain.lens.LensProfile
import ing.fuyaoskyrocket.photoinfo.ui.designsystem.*

@Composable
fun LensEditScreen(lens:LensProfile,onBack:()->Unit,onSave:(LensProfile)->Unit,currentExifModel:String="") {
    fun number(value:Double?)=value?.takeIf { it>0 }?.toString().orEmpty()
    val scope = rememberCoroutineScope()
    var readingProduct by remember { mutableStateOf(false) }
    var device by rememberSaveable(lens.id) { mutableStateOf(lens.device) }
    var exifModel by rememberSaveable(lens.id) { mutableStateOf(lens.exifModel) }
    var digitalMax by rememberSaveable(lens.id) { mutableStateOf(number(lens.digitalZoomMax)) }
    var name by rememberSaveable(lens.id) { mutableStateOf(lens.name) }
    var stylePrefix by rememberSaveable(lens.id) { mutableStateOf(lens.stylePrefix) }
    var originalMegapixels by rememberSaveable(lens.id) { mutableStateOf(number(lens.originalMegapixels)) }
    var min by rememberSaveable(lens.id) { mutableStateOf(number(lens.equivalentMin)) }
    var max by rememberSaveable(lens.id) { mutableStateOf(number(lens.equivalentMax)) }
    var zoomMin by rememberSaveable(lens.id) { mutableStateOf(number(lens.zoomMin)) }
    var zoomMax by rememberSaveable(lens.id) { mutableStateOf(number(lens.zoomMax)) }
    var physicalMin by rememberSaveable(lens.id) { mutableStateOf(number(lens.physicalMin)) }
    var physicalMax by rememberSaveable(lens.id) { mutableStateOf(number(lens.physicalMax)) }
    val draft=lens.copy(device=device.trim(),name=name.trim(),equivalentMin=min.toDoubleOrNull() ?: 0.0,equivalentMax=max.toDoubleOrNull() ?: 0.0,
        zoomMin=zoomMin.toDoubleOrNull(),zoomMax=zoomMax.toDoubleOrNull(),physicalMin=physicalMin.toDoubleOrNull(),physicalMax=physicalMax.toDoubleOrNull(),
        exifModel=exifModel.trim(),digitalZoomMax=digitalMax.toDoubleOrNull(),stylePrefix=stylePrefix.trim(),originalMegapixels=originalMegapixels.toDoubleOrNull(),
        hardwareDevice=if(lens.valid() && !lens.acceptsExif(exifModel)) "" else lens.hardwareDevice,
        hardwareModel=if(lens.valid() && !lens.acceptsExif(exifModel)) "" else lens.hardwareModel)
    val numeric=listOf(zoomMin,zoomMax,physicalMin,physicalMax,digitalMax,originalMegapixels).all { it.isBlank() || it.toDoubleOrNull()?.isFinite()==true }
    val valid=draft.valid()&&numeric
    val changed = ing.fuyaoskyrocket.photoinfo.domain.session.EditChanges.form(
        listOf(lens.device, lens.name, number(lens.equivalentMin), number(lens.equivalentMax), number(lens.zoomMin), number(lens.zoomMax), number(lens.physicalMin), number(lens.physicalMax), lens.exifModel, number(lens.digitalZoomMax), lens.stylePrefix, number(lens.originalMegapixels)),
        listOf(device, name, min, max, zoomMin, zoomMax, physicalMin, physicalMax, exifModel, digitalMax, stylePrefix, originalMegapixels), (2..7).toSet() + setOf(9, 11))
    val identityContent: @Composable ColumnScope.() -> Unit = {
            Text(stringResource(R.string.apply_lens_hint), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            SectionHeading(stringResource(R.string.lens_identity),stringResource(R.string.profile_match_hint))
            OutlinedTextField(device,{ if(it.length<=256)device=it },Modifier.fillMaxWidth(),enabled=!readingProduct,label={ Text(stringResource(R.string.profile_device)) },singleLine=true)
            TextButton(enabled=!readingProduct,onClick={ scope.launch {
                readingProduct=true
                try { device=ing.fuyaoskyrocket.photoinfo.data.camera.LocalCameraDevice.productName() }
                finally { readingProduct=false }
            } }) { Text(stringResource(R.string.read_product_name)) }
            OutlinedTextField(exifModel,{ if(it.length<=256)exifModel=it },Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.profile_exif_model)) },
                supportingText={ Text(stringResource(R.string.profile_exif_hint)) },singleLine=true)
            TextButton(onClick={ exifModel=currentExifModel },enabled=currentExifModel.isNotBlank()) { Text(stringResource(R.string.read_photo_model)) }
            if(lens.cameraId.isNotBlank()) Text(stringResource(
                if(lens.hardwareDevice == ing.fuyaoskyrocket.photoinfo.data.camera.LocalCameraDevice.hardwareKey) R.string.bound_camera_id else R.string.lens_hardware_id_hint,
                lens.cameraId),style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(name,{ if(it.length<=256)name=it },Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.profile_name)) },maxLines=3)
            val megapixelsInvalid = originalMegapixels.isNotBlank() && originalMegapixels.toDoubleOrNull()?.let { it.isFinite() && it > 0 && it <= 1000 } != true
            OutlinedTextField(originalMegapixels,{ if(it.length<=32)originalMegapixels=it },Modifier.fillMaxWidth(),
                label={ Text(stringResource(R.string.lens_original_megapixels)) },singleLine=true,
                suffix={ Text("MP") },isError=megapixelsInvalid,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                supportingText={ Text(stringResource(if(megapixelsInvalid) R.string.lens_original_megapixels_invalid else R.string.lens_original_megapixels_hint)) })
            val prefixInvalid = stylePrefix.length > 64 || stylePrefix.any(Char::isISOControl)
            OutlinedTextField(stylePrefix,{ if(it.length<=64)stylePrefix=it },Modifier.fillMaxWidth(),
                label={ Text(stringResource(R.string.lens_style_prefix)) },singleLine=true,
                isError=prefixInvalid,
                supportingText={ Text(stringResource(if(prefixInvalid) R.string.lens_style_prefix_invalid else R.string.lens_style_prefix_hint)) })
    }
    val rangeContent: @Composable ColumnScope.() -> Unit = {
            SectionHeading(stringResource(R.string.equivalent_range),stringResource(R.string.fixed_range_hint))
            RangeFields(min,max,R.string.equivalent_min,R.string.equivalent_max,{ min=it },{ max=it },2000.0)
            SectionHeading(stringResource(R.string.zoom_range),stringResource(R.string.optional_range_hint))
            RangeFields(zoomMin,zoomMax,R.string.zoom_min,R.string.zoom_max,{ zoomMin=it },{ zoomMax=it },200.0)
            if(draft.equivalentMin==draft.equivalentMax && draft.zoomMin!=null && draft.zoomMax!=null && draft.zoomMin!=draft.zoomMax)
                Text(stringResource(R.string.fixed_zoom_error),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            SectionHeading(stringResource(R.string.digital_range),stringResource(R.string.digital_range_hint))
            val digitalInvalid=digitalMax.isNotBlank()&&(digitalMax.toDoubleOrNull()?.let { it.isFinite()&&it>0&&it<=200&&draft.zoomMax!=null&&it>=draft.zoomMax } != true)
            OutlinedTextField(digitalMax,{ if(it.length<=32)digitalMax=it },Modifier.fillMaxWidth(),
                label={ Text(stringResource(R.string.digital_zoom_max)) },singleLine=true,isError=digitalInvalid,
                supportingText=if(digitalInvalid) { { Text(stringResource(R.string.digital_zoom_error)) } } else null,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
            SectionHeading(stringResource(R.string.physical_range),stringResource(R.string.physical_range_hint))
            RangeFields(physicalMin,physicalMax,R.string.physical_min,R.string.physical_max,{ physicalMin=it },{ physicalMax=it },1000.0,last=true)
            if(!valid)Text(stringResource(R.string.lens_validation_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val requestBack = rememberConfirmedBack(onBack, hasChanges = changed)
    FuyaoScaffold(stringResource(R.string.configure_lens),onBack=requestBack,actions={
        TextButton(onClick={ onSave(draft) },enabled=valid&&!readingProduct) { Text(stringResource(R.string.apply_lens)) }
    }) { padding ->
        FuyaoAdaptivePage(padding,
            single = { modifier ->
                Box(modifier,contentAlignment=Alignment.TopCenter) {
                    FuyaoPageColumn(Modifier.widthIn(max=FuyaoLayout.readable).fillMaxSize()) {
                        identityContent()
                        HorizontalDivider()
                        rangeContent()
                    }
                }
            },
            leading = { modifier ->
                FuyaoPageColumn(modifier,content=identityContent)
            },
            trailing = { modifier ->
                FuyaoPageColumn(modifier) {
                    rangeContent()
                }
            })
    }
}

@Composable
private fun RangeFields(min:String,max:String,minLabel:Int,maxLabel:Int,onMin:(String)->Unit,onMax:(String)->Unit,limit:Double,last:Boolean=false) {
    val low=min.toDoubleOrNull();val high=max.toDoubleOrNull()
    val inverted=low!=null&&high!=null&&low>high
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    @Composable fun field(value:String,label:Int,onChange:(String)->Unit,modifier:Modifier,done:Boolean=false) {
        val parsed=value.toDoubleOrNull()
        val invalid=value.isNotBlank()&&(parsed==null||!parsed.isFinite()||parsed<=0||parsed>limit)||inverted
        OutlinedTextField(value,{ if(it.length<=32)onChange(it) },modifier,label={ Text(stringResource(label)) },singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=if(done)ImeAction.Done else ImeAction.Next),
            keyboardActions=KeyboardActions(onNext={ focus.moveFocus(FocusDirection.Next) },onDone={ focus.clearFocus();keyboard?.hide() }),
            isError=invalid,supportingText=if(invalid) { { Text(stringResource(if(inverted)R.string.range_order_error else R.string.range_number_error,limit.toInt())) } } else null)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth>=360.dp&&LocalDensity.current.fontScale<=1.3f)Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            field(min,minLabel,onMin,Modifier.weight(1f));field(max,maxLabel,onMax,Modifier.weight(1f),last)
        } else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            field(min,minLabel,onMin,Modifier.fillMaxWidth());field(max,maxLabel,onMax,Modifier.fillMaxWidth(),last)
        }
    }
}
