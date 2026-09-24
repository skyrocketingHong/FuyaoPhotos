package ing.fuyaoskyrocket.photoinfo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ing.fuyaoskyrocket.photoinfo.presentation.PhotoFailureMessages
import ing.fuyaoskyrocket.photoinfo.presentation.PhotoOperation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PhotoFailureMessagesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun platformExceptionDetailsAreNotPresentedAsRecoveryInstructions() {
        val message=PhotoFailureMessages.describe(context,NullPointerException("Object.getClass()"),PhotoOperation.SAVE)
        assertEquals(context.getString(R.string.error_export),message)
        assertFalse(message.contains("getClass"))
    }
    @Test fun preservationErrorsRetainTheirSpecificExplanation() {
        val expected=context.getString(R.string.hdr_not_decoded)
        assertEquals(expected,PhotoFailureMessages.describe(context,IOException(expected),PhotoOperation.SAVE))
    }
    @Test fun permissionAndMemoryFailuresHaveActionableMessages() {
        assertEquals(context.getString(R.string.error_permission),PhotoFailureMessages.describe(context,SecurityException(),PhotoOperation.OPEN))
        assertEquals(context.getString(R.string.error_memory),PhotoFailureMessages.describe(context,OutOfMemoryError(),PhotoOperation.PREVIEW))
    }
}
