// JNI bridge to the vendored x265 for single-frame still encoding. The
// parameter discipline follows the device-verified reference converter:
// one frame in, parameter sets plus one IDR out (repeat-headers=0, keyint=1,
// no B-frames or lookahead), so the container side can lift VPS/SPS/PPS into
// the hvcC property and store the slice as the length-prefixed payload.
#include <jni.h>
#include <android/log.h>
#include <x265.h>
#include <cstdio>
#include <cstring>

#define FUYAO_LOG(...) __android_log_print(ANDROID_LOG_ERROR, "FuyaoX265", __VA_ARGS__)

namespace {

void setParam(x265_param* p, const char* name, const char* value) {
    if (x265_param_parse(p, name, value) != 0) {
        // Deliberately noisy: an unknown option means this x265 revision
        // diverges from the vendored one and must be surfaced, not ignored.
        FUYAO_LOG("param rejected: %s=%s", name, value);
    }
}

// colr codes as written by the container layer, mapped to x265 VUI names.
const char* primariesName(int code) {
    switch (code) {
        case 1: return "bt709";
        case 9: return "bt2020";
        case 12: return "smpte432";
        default: return "bt709";
    }
}
const char* transferName(int code) {
    switch (code) {
        case 16: return "smpte2084";
        case 18: return "arib-std-b67";
        case 1: return "bt709";
        default: return "iec61966-2-1"; // 13 and everything SDR
    }
}
const char* matrixName(int code) {
    switch (code) {
        case 6: return "smpte170m";
        case 1: return "bt709";
        default: return "bt2020nc"; // 9 and everything wide
    }
}

struct Encoded {
    x265_nal* nals = nullptr;
    uint32_t count = 0;
    bool owned = false; // true when nals is a heap copy the caller must free
};

void release(Encoded* encoded) {
    if (encoded->owned) { delete[] encoded->nals; encoded->nals = nullptr; encoded->owned = false; }
}

// Feeds one frame and drains the (B-frame-free) pipeline completely. The NAL
// payloads stay owned by the encoder until close, so copy out before closing.
bool drain(x265_encoder* encoder, x265_picture* pic, Encoded* out) {
    x265_nal* head = nullptr;
    uint32_t headCount = 0;
    if (x265_encoder_encode(encoder, &head, &headCount, pic, nullptr) < 0) return false;
    x265_nal* tail = nullptr;
    uint32_t tailCount = 0;
    x265_encoder_encode(encoder, &tail, &tailCount, nullptr, nullptr);
    if (headCount == 0 && tailCount == 0) return false;
    if (tailCount == 0) { out->nals = head; out->count = headCount; return true; }
    if (headCount == 0) { out->nals = tail; out->count = tailCount; return true; }
    x265_nal* joined = new x265_nal[headCount + tailCount];
    for (uint32_t i = 0; i < headCount; i++) joined[i] = head[i];
    for (uint32_t i = 0; i < tailCount; i++) joined[headCount + i] = tail[i];
    out->nals = joined;
    out->count = headCount + tailCount;
    out->owned = true;
    return true;
}

jbyteArray toJava(JNIEnv* env, const Encoded& encoded) {
    size_t total = 0;
    for (uint32_t i = 0; i < encoded.count; i++) total += encoded.nals[i].sizeBytes;
    jbyteArray result = env->NewByteArray(static_cast<jsize>(total));
    if (result == nullptr) return nullptr;
    size_t at = 0;
    for (uint32_t i = 0; i < encoded.count; i++) {
        env->SetByteArrayRegion(result, static_cast<jsize>(at),
            static_cast<jsize>(encoded.nals[i].sizeBytes),
            reinterpret_cast<const jbyte*>(encoded.nals[i].payload));
        at += encoded.nals[i].sizeBytes;
    }
    return result;
}

void fail(JNIEnv* env, const char* message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) env->ThrowNew(type, message);
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_ing_fuyaoskyrocket_photoinfo_platform_HevcX265_nativeAvailable(JNIEnv*, jclass) {
    return JNI_TRUE;
}

// One 8-bit monochrome frame: i400, ultrafast CRF18, Rext monochrome profile -
// the depth/matte/gain-map shape Apple's readers accept.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_ing_fuyaoskyrocket_photoinfo_platform_HevcX265_nativeEncodeMono(
        JNIEnv* env, jclass, jint width, jint height, jbyteArray luma) {
    x265_param* param = x265_param_alloc();
    x265_picture* pic = x265_picture_alloc();
    if (param == nullptr || pic == nullptr) {
        if (param != nullptr) x265_param_free(param);
        if (pic != nullptr) x265_picture_free(pic);
        fail(env, "x265 allocation failed");
        return nullptr;
    }
    jbyte* plane = env->GetByteArrayElements(luma, nullptr);
    jbyteArray result = nullptr;
    if (x265_param_default_preset(param, "ultrafast", nullptr) == 0) {
        setParam(param, "input-csp", "i400");
        param->sourceWidth = width;
        param->sourceHeight = height;
        param->internalBitDepth = 8;
        param->totalFrames = 1;
        setParam(param, "fps", "1");
        setParam(param, "crf", "18");
        setParam(param, "range", "full");
        setParam(param, "repeat-headers", "0");
        setParam(param, "keyint", "1");
        setParam(param, "bframes", "0");
        setParam(param, "rc-lookahead", "0");
        setParam(param, "pools", "none");
        setParam(param, "frame-threads", "1");
        x265_param_apply_profile(param, "main444-8");
        x265_encoder* encoder = x265_encoder_open(param);
        if (encoder != nullptr) {
            x265_picture_init(param, pic);
            pic->planes[0] = plane;
            pic->stride[0] = width;
            pic->pts = 0;
            Encoded encoded;
            if (drain(encoder, pic, &encoded)) result = toJava(env, encoded);
            release(&encoded);
            x265_encoder_close(encoder);
        } else {
            fail(env, "x265 mono encoder open failed");
        }
    } else {
        fail(env, "x265 preset failed");
    }
    env->ReleaseByteArrayElements(luma, plane, JNI_ABORT);
    x265_picture_free(pic);
    x265_param_free(param);
    return result;
}

// One 10-bit 4:2:0 frame from a tight P010 buffer (Y plane then interleaved UV
// pairs, 16-bit little-endian samples): the U and V planes alias the pair
// stream through byte offsets, so no deinterleave copy is needed.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_ing_fuyaoskyrocket_photoinfo_platform_HevcX265_nativeEncodeColor10(
        JNIEnv* env, jclass, jint width, jint height, jbyteArray p010, jint crf,
        jint colorprim, jint transfer, jint colormatrix) {
    x265_param* param = x265_param_alloc();
    x265_picture* pic = x265_picture_alloc();
    if (param == nullptr || pic == nullptr) {
        if (param != nullptr) x265_param_free(param);
        if (pic != nullptr) x265_picture_free(pic);
        fail(env, "x265 allocation failed");
        return nullptr;
    }
    jbyte* data = env->GetByteArrayElements(p010, nullptr);
    jbyteArray result = nullptr;
    if (x265_param_default_preset(param, "fast", nullptr) == 0) {
        setParam(param, "input-csp", "i420");
        setParam(param, "input-depth", "10");
        param->sourceWidth = width;
        param->sourceHeight = height;
        param->internalBitDepth = 10;
        param->totalFrames = 1;
        setParam(param, "fps", "1");
        char crfText[8];
        snprintf(crfText, sizeof(crfText), "%d", crf);
        setParam(param, "crf", crfText);
        setParam(param, "range", "full");
        setParam(param, "repeat-headers", "0");
        setParam(param, "keyint", "1");
        setParam(param, "bframes", "0");
        setParam(param, "rc-lookahead", "0");
        setParam(param, "no-open-gop", "0");
        setParam(param, "psy-rd", "0");
        setParam(param, "aq-mode", "1");
        setParam(param, "colorprim", primariesName(colorprim));
        setParam(param, "transfer", transferName(transfer));
        setParam(param, "colormatrix", matrixName(colormatrix));
        x265_param_apply_profile(param, "main10");
        x265_encoder* encoder = x265_encoder_open(param);
        if (encoder != nullptr) {
            x265_picture_init(param, pic);
            const size_t chromaOffset = static_cast<size_t>(width) * height * 2;
            pic->planes[0] = data;
            pic->planes[1] = data + chromaOffset;
            pic->planes[2] = data + chromaOffset + 2;
            pic->stride[0] = width * 2;
            pic->stride[1] = width * 2;
            pic->stride[2] = width * 2;
            pic->pts = 0;
            Encoded encoded;
            if (drain(encoder, pic, &encoded)) result = toJava(env, encoded);
            release(&encoded);
            x265_encoder_close(encoder);
        } else {
            fail(env, "x265 main10 encoder open failed");
        }
    } else {
        fail(env, "x265 preset failed");
    }
    env->ReleaseByteArrayElements(p010, data, JNI_ABORT);
    x265_picture_free(pic);
    x265_param_free(param);
    return result;
}
