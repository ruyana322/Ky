#include <jni.h>
#include <string>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

// Include library NCNN
#include "net.h"
#include "gpu.h"

#define TAG "NcnnBridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Bikin instance mesin NCNN global
static ncnn::Net realEsrganNet;
static bool isGpuEnabled = false;

extern "C"
JNIEXPORT jboolean JNICALL
// PENTING: Ganti 'com_example_ky_NcnnVideoBridge' sesuai nama package Kotlin lu nanti!
Java_com_example_ky_NcnnVideoBridge_initEngine(JNIEnv *env, jobject thiz, jobject assetManager) {
    
    // 1. Nyalain Mesin GPU (Vulkan)
    ncnn::create_gpu_instance();
    int gpu_count = ncnn::get_gpu_count();
    
    if (gpu_count > 0) {
        LOGD("Mantap! Ditemukan %d GPU Vulkan. Mesin Turbo diaktifkan!", gpu_count);
        realEsrganNet.opt.use_vulkan_compute = true;
        isGpuEnabled = true;
    } else {
        LOGE("Waduh, GPU Vulkan gak ketemu. Terpaksa pakai CPU biasa.");
        realEsrganNet.opt.use_vulkan_compute = false;
        isGpuEnabled = false;
    }

    // 2. Siapin AssetManager buat narik file dari folder assets
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    
    // 3. Load model anime v3 dari folder assets lu
    // Sesuaikan jalurnya kalau letaknya beda dari assets/realsr/models/
    int ret_param = realEsrganNet.load_param(mgr, "realsr/models/realesr-animevideov3-x2.param");
    int ret_bin = realEsrganNet.load_model(mgr, "realsr/models/realesr-animevideov3-x2.bin");

    if (ret_param != 0 || ret_bin != 0) {
        LOGE("Gagal memuat model Real-ESRGAN! Cek lagi lokasi file bin dan param-nya.");
        return JNI_FALSE;
    }
    
    LOGD("Model Real-ESRGAN Anime V3 sukses dimuat ke mesin!");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_ky_NcnnVideoBridge_destroyEngine(JNIEnv *env, jobject thiz) {
    // Matiin mesin dan bersihin memori (Biar HP gak panas/RAM bocor)
    realEsrganNet.clear();
    ncnn::destroy_gpu_instance();
    LOGD("Mesin NCNN dimatikan dan memori dibersihkan.");
}
