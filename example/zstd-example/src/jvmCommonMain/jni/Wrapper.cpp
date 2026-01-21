#include <zstd.h>
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jstring JNICALL
Java_com_ensody_nativebuilds_example_zstd_ZstdWrapper_getZstdVersion(
        JNIEnv *env,
        jobject type
) {
    return env->NewStringUTF(ZSTD_versionString());
}

#ifdef __cplusplus
}
#endif
