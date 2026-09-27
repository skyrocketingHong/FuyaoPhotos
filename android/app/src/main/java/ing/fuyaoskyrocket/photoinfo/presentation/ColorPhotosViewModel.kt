package ing.fuyaoskyrocket.photoinfo.presentation

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import ing.fuyaoskyrocket.photoinfo.domain.session.PhotoSessionKind

class ColorPhotosViewModel(application: Application, saved: SavedStateHandle) :
    MetadataViewModel(application, saved, PhotoSessionKind.COLORS)
