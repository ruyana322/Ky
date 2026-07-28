#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <time.h>

// NCNN
#include "net.h"
#include "gpu.h"
#include "cpu.h"

#define TAG "KytheraRE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static ncnn::Net* g_net_x2  = nullptr;
static ncnn::Net* g_net_x4  = nullptr;
static bool       g_has_gpu = false;
static bool       g_loaded  = false;

// ─── Helper: Bitmap (RGBA) → ncnn::Mat (RGB) ─────────────────────────────────
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

// ─── Helper: ncnn::Mat (RGB float [0-255]) → new Android Bitmap ───────────────
static jobject matToBitmap(JNIEnv* env, const ncnn::Mat& mat) {
    if (mat.empty()) return nullptr;
    jclass bitmapClass  = env->FindClass("android/graphics/Bitmap");
    jclass configClass  = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID fid        = env->GetStaticFieldID(configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888    = env->GetStaticObjectField(configClass, fid);
    jmethodID createMid = env->GetStaticMethodID(bitmapClass, "createBitmap",
                          "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject result = env->CallStaticObjectMethod(bitmapClass, createMid, mat.w, mat.h, argb8888);
    if (!result) return nullptr;
    void* rp = nullptr;
    if (AndroidBitmap_lockPixels(env, result, &rp) >= 0) {
        mat.to_pixels((unsigned char*)rp, ncnn::Mat::PIXEL_RGB2RGBA);
        AndroidBitmap_unlockPixels(env, result);
    }
    return result;
}

static void configNet(ncnn::Net* net, bool gpu) {
    net->opt.use_vulkan_compute   = gpu;
    // DO NOT force FP16. Let NCNN auto-detect capabilities via set_vulkan_device.
    // Forcing it on unsupported GPUs causes Vulkan to fail and fallback to CPU!
    net->opt.num_threads          = ncnn::get_cpu_count();

    if (gpu) {
        net->set_vulkan_device(ncnn::get_gpu_device(0));
    }
}

// ─── loadModelNative ──────────────────────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_superresolution_RealEsrganBridge_loadModelNative(
        JNIEnv* env, jobject thiz, jobject assetManager, jboolean useGpu) {

    ncnn::create_gpu_instance();
    bool deviceHasGpu = ncnn::get_gpu_count() > 0;
    g_has_gpu = useGpu && deviceHasGpu;

    if (g_loaded) { 
        LOGI("Already loaded. GPU: %s (Device: %s)", g_has_gpu ? "YES" : "NO", deviceHasGpu ? "YES" : "NO"); 
        // Note: Currently we don't hot-reload if the user toggled the switch. We just use the first loaded state. 
        // For true hot-reloading, we'd need to recreate the net. For now, it respects the launch state.
        return JNI_TRUE; 
    }

    LOGI("Vulkan GPU enabled: %s", g_has_gpu ? "YES" : "NO");

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    g_net_x2 = new ncnn::Net(); configNet(g_net_x2, g_has_gpu);
    if (g_net_x2->load_param(mgr, "realsr/models/realesr-animevideov3-x2.param") != 0 ||
        g_net_x2->load_model(mgr, "realsr/models/realesr-animevideov3-x2.bin") != 0) {
        LOGE("FATAL: x2 model load failed!");
        delete g_net_x2; g_net_x2 = nullptr;
        ncnn::destroy_gpu_instance();
        return JNI_FALSE;
    }
    LOGI("x2 model OK.");

    g_net_x4 = new ncnn::Net(); configNet(g_net_x4, g_has_gpu);
    if (g_net_x4->load_param(mgr, "realsr/models/x4.param") != 0 ||
        g_net_x4->load_model(mgr, "realsr/models/x4.bin") != 0) {
        LOGE("x4 model failed (non-fatal).");
        delete g_net_x4; g_net_x4 = nullptr;
    } else { LOGI("x4 model OK."); }

    g_loaded = true;
    LOGI("Engine ready. GPU=%s", g_has_gpu ? "Vulkan" : "CPU");
    return JNI_TRUE;
}

// ─── processImage ─────────────────────────────────────────────────────────────
// Real-ESRGAN NCNN (nihui/xinntao) normalization:
//   INPUT : from_pixels gives [0,255] float → normalize to [0,1]
//   OUTPUT: model outputs [0,1] → denormalize to [0,255] → to_pixels clamps to uint8
//
// Reference: https://github.com/nihui/realsr-ncnn-vulkan/blob/master/src/realsr.cpp
extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_superresolution_RealEsrganBridge_processImage(
        JNIEnv* env, jobject thiz,
        jobject bitmap, jint scale, jstring modelName, jboolean useFaceRestore) {

    struct timespec t0, t1;
    clock_gettime(CLOCK_MONOTONIC, &t0);

    ncnn::Net* net = (scale == 4 && g_net_x4) ? g_net_x4 : g_net_x2;
    if (!net) { LOGE("processImage: engine not loaded!"); return nullptr; }

    // 1. Bitmap → ncnn::Mat (RGBA→RGB, float [0,255])
    ncnn::Mat in = bitmapToMat(env, bitmap);
    if (in.empty()) { LOGE("processImage: bitmap conversion failed"); return nullptr; }

    // 2. Normalize [0,255] → [0,1]  (model expects normalized input)
    const float norm_vals[3] = { 1/255.f, 1/255.f, 1/255.f };
    in.substract_mean_normalize(0, norm_vals);

    // 3. Run inference
    ncnn::Extractor ex = net->create_extractor();
    ex.input("data", in);
    ncnn::Mat out;
    int ret = ex.extract("output", out);

    if (ret != 0 || out.empty()) {
        LOGE("processImage: inference failed (ret=%d)", ret);
        return nullptr;
    }

    // 4. Denormalize [0,1] → [0,255]  (model outputs [0,1])
    const float denorm_vals[3] = { 255.f, 255.f, 255.f };
    out.substract_mean_normalize(0, denorm_vals);

    // 5. ncnn::Mat → Android Bitmap  (to_pixels auto-clamps float→uint8)
    jobject result = matToBitmap(env, out);

    clock_gettime(CLOCK_MONOTONIC, &t1);
    long long ms = ((long long)t1.tv_sec * 1000 + t1.tv_nsec / 1000000)
                 - ((long long)t0.tv_sec * 1000 + t0.tv_nsec / 1000000);
    LOGI("processImage x%d: %lldms [%s] %dx%d→%dx%d",
         (int)scale, ms, g_has_gpu ? "Vulkan" : "CPU",
         in.w, in.h, out.w, out.h);
    return result;
}

// ─── releaseNative ────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_superresolution_RealEsrganBridge_releaseNative(JNIEnv* env, jobject thiz) {
    if (g_net_x2) { delete g_net_x2; g_net_x2 = nullptr; }
    if (g_net_x4) { delete g_net_x4; g_net_x4 = nullptr; }
    ncnn::destroy_gpu_instance();
    g_loaded = false;
    LOGI("Engine released.");
}

// ─── FaceRestoreBridge stubs ─────────────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_superresolution_FaceRestoreBridge_loadModelNative(
        JNIEnv* env, jobject thiz, jobject assetManager) { return JNI_FALSE; }

extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_superresolution_FaceRestoreBridge_processImageNative(
        JNIEnv* env, jobject thiz, jobject bitmap, jboolean enhance) { return bitmap; }

extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_superresolution_FaceRestoreBridge_releaseNative(JNIEnv* env, jobject thiz) {}

// ─── Legacy NcnnVideoBridge stubs ────────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_NcnnVideoBridge_initEngine(
        JNIEnv* env, jclass clazz, jobject assetManager) {
    return Java_com_d4nzxml_kythera_superresolution_RealEsrganBridge_loadModelNative(env, nullptr, assetManager, JNI_TRUE);
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
