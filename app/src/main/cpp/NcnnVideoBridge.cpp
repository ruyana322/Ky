#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <time.h>

// NCNN
#include "net.h"
#include "gpu.h"

#define TAG "KytheraRE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Two nets, PixelHD style: preload both x2 & x4 at launch
static ncnn::Net* g_net_x2  = nullptr;
static ncnn::Net* g_net_x4  = nullptr;
static bool       g_has_gpu = false;
static bool       g_loaded  = false;

// ─── Helpers ──────────────────────────────────────────────────────────────────
static ncnn::Mat bitmapToMat(JNIEnv* env, jobject bitmap) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return ncnn::Mat();
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return ncnn::Mat();
    ncnn::Mat mat = ncnn::Mat::from_pixels(
        (const unsigned char*)pixels, ncnn::Mat::PIXEL_RGBA2RGB, info.width, info.height);
    AndroidBitmap_unlockPixels(env, bitmap);
    return mat;
}

static jobject matToBitmap(JNIEnv* env, const ncnn::Mat& mat) {
    if (mat.empty()) return nullptr;
    jclass bitmapClass  = env->FindClass("android/graphics/Bitmap");
    jclass configClass  = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID fid        = env->GetStaticFieldID(configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888    = env->GetStaticObjectField(configClass, fid);
    jmethodID createMid = env->GetStaticMethodID(bitmapClass, "createBitmap",
                          "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject result = env->CallStaticObjectMethod(bitmapClass, createMid, mat.w, mat.h, argb8888);
    if (result == nullptr) return nullptr;
    void* rp = nullptr;
    if (AndroidBitmap_lockPixels(env, result, &rp) >= 0) {
        mat.to_pixels((unsigned char*)rp, ncnn::Mat::PIXEL_RGB2RGBA);
        AndroidBitmap_unlockPixels(env, result);
    }
    return result;
}

static void configNet(ncnn::Net* net, bool gpu) {
    net->opt.use_vulkan_compute   = gpu;
    net->opt.use_fp16_packed      = gpu;
    net->opt.use_fp16_storage     = gpu;
    net->opt.use_fp16_arithmetic  = gpu;
}

// ─── RealEsrganBridge.loadModelNative ─────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_superresolution_RealEsrganBridge_loadModelNative(
        JNIEnv* env, jobject thiz, jobject assetManager) {

    if (g_loaded) { LOGI("Already loaded."); return JNI_TRUE; }

    ncnn::create_gpu_instance();
    g_has_gpu = ncnn::get_gpu_count() > 0;
    LOGI("Vulkan GPU: %s", g_has_gpu ? "YES" : "NO");

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    // x2
    g_net_x2 = new ncnn::Net(); configNet(g_net_x2, g_has_gpu);
    if (g_net_x2->load_param(mgr, "realsr/models/realesr-animevideov3-x2.param") != 0 ||
        g_net_x2->load_model(mgr, "realsr/models/realesr-animevideov3-x2.bin")   != 0) {
        LOGE("FATAL: x2 model load failed!");
        delete g_net_x2; g_net_x2 = nullptr;
        ncnn::destroy_gpu_instance();
        return JNI_FALSE;
    }
    LOGI("x2 model OK.");

    // x4 (non-fatal if missing)
    g_net_x4 = new ncnn::Net(); configNet(g_net_x4, g_has_gpu);
    if (g_net_x4->load_param(mgr, "realsr/models/x4.param") != 0 ||
        g_net_x4->load_model(mgr, "realsr/models/x4.bin")   != 0) {
        LOGE("x4 model failed (non-fatal).");
        delete g_net_x4; g_net_x4 = nullptr;
    } else { LOGI("x4 model OK."); }

    g_loaded = true;
    LOGI("Engine ready. GPU=%s", g_has_gpu ? "Vulkan" : "CPU");
    return JNI_TRUE;
}

// ─── RealEsrganBridge.processImage ────────────────────────────────────────────
extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_superresolution_RealEsrganBridge_processImage(
        JNIEnv* env, jobject thiz,
        jobject bitmap, jint scale, jstring modelName, jboolean useFaceRestore) {

    struct timespec t0, t1;
    clock_gettime(CLOCK_MONOTONIC, &t0);

    ncnn::Net* net = (scale == 4 && g_net_x4) ? g_net_x4 : g_net_x2;
    if (!net) { LOGE("processImage: engine not loaded!"); return nullptr; }

    ncnn::Mat in = bitmapToMat(env, bitmap);
    if (in.empty()) { LOGE("processImage: bitmap to mat failed"); return nullptr; }

    // Normalize [0,255] -> [0,1]
    const float norm[3] = { 1/255.f, 1/255.f, 1/255.f };
    in.substract_mean_normalize(0, norm);

    ncnn::Extractor ex = net->create_extractor();
    ex.input("data", in);
    ncnn::Mat out;
    ex.extract("output", out);

    if (out.empty()) { LOGE("processImage: empty output!"); return nullptr; }

    // Denormalize [0,1] -> [0,255] and clamp
    float* d = (float*)out.data;
    int total = out.w * out.h * out.c;
    for (int i = 0; i < total; i++) {
        d[i] *= 255.f;
        if (d[i] < 0.f) d[i] = 0.f; else if (d[i] > 255.f) d[i] = 255.f;
    }

    jobject result = matToBitmap(env, out);

    clock_gettime(CLOCK_MONOTONIC, &t1);
    long long ms = ((long long)t1.tv_sec*1000 + t1.tv_nsec/1000000)
                 - ((long long)t0.tv_sec*1000 + t0.tv_nsec/1000000);
    LOGI("processImage x%d: %lld ms [%s]", (int)scale, ms, g_has_gpu?"Vulkan":"CPU");
    return result;
}

// ─── RealEsrganBridge.releaseNative ───────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_superresolution_RealEsrganBridge_releaseNative(JNIEnv* env, jobject thiz) {
    if (g_net_x2) { delete g_net_x2; g_net_x2 = nullptr; }
    if (g_net_x4) { delete g_net_x4; g_net_x4 = nullptr; }
    ncnn::destroy_gpu_instance();
    g_loaded = false;
    LOGI("Engine released.");
}

// ─── FaceRestoreBridge stubs (same .so) ──────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_superresolution_FaceRestoreBridge_loadModelNative(
        JNIEnv* env, jobject thiz, jobject assetManager) {
    LOGI("FaceRestoreBridge.loadModelNative: stub (GFPGAN not integrated)");
    return JNI_FALSE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_superresolution_FaceRestoreBridge_processImageNative(
        JNIEnv* env, jobject thiz, jobject bitmap, jboolean enhance) {
    return bitmap; // passthrough
}

extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_superresolution_FaceRestoreBridge_releaseNative(JNIEnv* env, jobject thiz) {}

// ─── Legacy NcnnVideoBridge stubs (backward compat, called by old code) ───────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_NcnnVideoBridge_initEngine(
        JNIEnv* env, jclass clazz, jobject assetManager) {
    return Java_com_d4nzxml_kythera_superresolution_RealEsrganBridge_loadModelNative(
        env, nullptr, assetManager);
}

extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_service_NcnnVideoBridge_destroyEngine(JNIEnv* env, jclass clazz) {
    Java_com_d4nzxml_kythera_superresolution_RealEsrganBridge_releaseNative(env, nullptr);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_NcnnVideoBridge_processFrame(
        JNIEnv* env, jclass clazz, jobject bitmap, jboolean useGpu) {
    return Java_com_d4nzxml_kythera_superresolution_RealEsrganBridge_processImage(
        env, nullptr, bitmap, 2, nullptr, JNI_FALSE);
}
