#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <memory>

// The Prefab LAME AAR exports the stable C API from libmp3lame.so but does not
// ship lame.h in its include directory. Declare only the ABI-stable functions
// used here; the library itself remains the implementation and source of symbols.
struct lame_global_struct;
using lame_t = lame_global_struct*;

extern "C" {
lame_t lame_init(void);
int lame_close(lame_t);
int lame_set_num_channels(lame_t, int);
int lame_set_in_samplerate(lame_t, int);
int lame_set_out_samplerate(lame_t, int);
int lame_set_brate(lame_t, int);
int lame_set_quality(lame_t, int);
int lame_init_params(lame_t);
int lame_encode_buffer_interleaved(
    lame_t, short int[], int, unsigned char[], int
);
int lame_encode_flush(lame_t, unsigned char[], int);
}

namespace {
struct EncoderState {
    lame_t lame = nullptr;
    int channels = 0;
};

void throwIllegalState(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) env->ThrowNew(cls, message);
}

EncoderState* asState(jlong handle) {
    return reinterpret_cast<EncoderState*>(static_cast<intptr_t>(handle));
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_dsann_music_util_NativeMp3Encoder_nativeCreate(
    JNIEnv* env, jobject /* thiz */, jint sampleRate, jint channels, jint bitrateKbps
) {
    if (sampleRate < 8000 || sampleRate > 48000 || (channels != 1 && channels != 2) ||
        bitrateKbps < 32 || bitrateKbps > 320) {
        throwIllegalState(env, "Unsupported MP3 encoder settings");
        return 0;
    }

    std::unique_ptr<EncoderState> state(new EncoderState());
    state->lame = lame_init();
    state->channels = channels;
    if (state->lame == nullptr) {
        throwIllegalState(env, "LAME could not allocate an encoder");
        return 0;
    }

    lame_set_num_channels(state->lame, channels);
    lame_set_in_samplerate(state->lame, sampleRate);
    lame_set_out_samplerate(state->lame, sampleRate);
    lame_set_brate(state->lame, bitrateKbps);
    lame_set_quality(state->lame, 2);

    if (lame_init_params(state->lame) < 0) {
        lame_close(state->lame);
        state->lame = nullptr;
        throwIllegalState(env, "LAME rejected the PCM audio format");
        return 0;
    }

    return static_cast<jlong>(reinterpret_cast<intptr_t>(state.release()));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_dsann_music_util_NativeMp3Encoder_nativeEncode(
    JNIEnv* env, jobject /* thiz */, jlong handle, jshortArray pcm
) {
    EncoderState* state = asState(handle);
    if (state == nullptr || state->lame == nullptr || pcm == nullptr) {
        throwIllegalState(env, "Invalid MP3 encoder state");
        return nullptr;
    }

    const jsize valueCount = env->GetArrayLength(pcm);
    if (state->channels < 1 || state->channels > 2 ||
        valueCount <= 0 || valueCount % state->channels != 0) {
        throwIllegalState(env, "PCM buffer does not match the encoder channel count");
        return nullptr;
    }

    jshort* samples = env->GetShortArrayElements(pcm, nullptr);
    if (samples == nullptr) return nullptr;

    const int samplesPerChannel = valueCount / state->channels;
    const int capacity = std::max(8192, static_cast<int>(samplesPerChannel * 1.35) + 7200);
    std::unique_ptr<unsigned char[]> encoded(new unsigned char[capacity]);

    const int written = lame_encode_buffer_interleaved(
        state->lame,
        reinterpret_cast<short int*>(samples),
        samplesPerChannel,
        encoded.get(),
        capacity
    );

    env->ReleaseShortArrayElements(pcm, samples, JNI_ABORT);
    if (written < 0) {
        throwIllegalState(env, "LAME failed while encoding an audio frame");
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(written);
    if (result != nullptr && written > 0) {
        env->SetByteArrayRegion(result, 0, written, reinterpret_cast<const jbyte*>(encoded.get()));
    }
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_dsann_music_util_NativeMp3Encoder_nativeFlush(
    JNIEnv* env, jobject /* thiz */, jlong handle
) {
    EncoderState* state = asState(handle);
    if (state == nullptr || state->lame == nullptr) {
        throwIllegalState(env, "Invalid MP3 encoder state");
        return nullptr;
    }

    constexpr int capacity = 16384;
    std::unique_ptr<unsigned char[]> encoded(new unsigned char[capacity]);
    const int written = lame_encode_flush(state->lame, encoded.get(), capacity);
    if (written < 0) {
        throwIllegalState(env, "LAME failed to flush the final MP3 frames");
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(written);
    if (result != nullptr && written > 0) {
        env->SetByteArrayRegion(result, 0, written, reinterpret_cast<const jbyte*>(encoded.get()));
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_dsann_music_util_NativeMp3Encoder_nativeClose(
    JNIEnv* /* env */, jobject /* thiz */, jlong handle
) {
    EncoderState* state = asState(handle);
    if (state == nullptr) return;
    if (state->lame != nullptr) lame_close(state->lame);
    delete state;
}
