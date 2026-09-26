// Placeholder for ABIs the vendored x265 does not ship for. The library loads,
// reports itself unavailable, and the Kotlin layer falls back to the platform
// encoder without touching any encoder entry point.
#include <jni.h>

extern "C" JNIEXPORT jboolean JNICALL
Java_ing_fuyaoskyrocket_photoinfo_platform_HevcX265_nativeAvailable(JNIEnv*, jobject) {
    return JNI_FALSE;
}
