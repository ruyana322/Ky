 #include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "KytheraAI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// Returns status string to MainActivity
JNIEXPORT jstring JNICALL
Java_com_kythera_video_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Mesin AI Kythera Siap!";
    LOGI("JNI init OK: %s", hello.c_str());
    return env->NewStringUTF(hello.c_str());
}

// Stub: upscale pipeline entry point (FFmpeg + NCNN Vulkan)
JNIEXPORT jint JNICALL
Java_com_kythera_video_MainActivity_startUpscale(
        JNIEnv* env,
        jobject /* this */,
        jstring inputPath,
        jstring outputPath,
        jint scaleFactor) {

    const char* inPath  = env->GetStringUTFChars(inputPath,  nullptr);
    const char* outPath = env->GetStringUTFChars(outputPath, nullptr);

    LOGI("Upscale request: %s -> %s (x%d)", inPath, outPath, scaleFactor);

    // TODO: wire FFmpegKit decode → NCNN Real-ESRGAN Vulkan → FFmpeg encode
    // Return 0 = success, -1 = error
    int result = 0;

    env->ReleaseStringUTFChars(inputPath,  inPath);
    env->ReleaseStringUTFChars(outputPath, outPath);
    return result;
}

} // extern "C"
