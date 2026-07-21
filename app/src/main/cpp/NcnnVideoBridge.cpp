#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>

// Header NCNN
#include "net.h"
#include "gpu.h"

#define TAG "KytheraNCNN"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Variabel global mesin AI
static ncnn::Net* g_net = nullptr;
static bool g_has_gpu = false;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_NcnnVideoBridge_initEngine(JNIEnv *env, jclass clazz, jobject assetManager) {
    if (g_net != nullptr) {
        delete g_net;
        g_net = nullptr;
    }

    // Inisialisasi Vulkan GPU
    ncnn::create_gpu_instance();
    g_has_gpu = ncnn::get_gpu_count() > 0;

    g_net = new ncnn::Net();
    g_net->opt.use_vulkan_compute = g_has_gpu;

    // Ambil AssetManager dari Kotlin
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    
    // Load model x2 dari folder assets
    int ret_param = g_net->load_param(mgr, "realsr/models/realesr-animevideov3-x2.param");
    int ret_bin   = g_net->load_model(mgr, "realsr/models/realesr-animevideov3-x2.bin");

    if (ret_param != 0 || ret_bin != 0) {
        LOGE("Gagal load model NCNN dari assets! Cek path file-nya.");
        return JNI_FALSE;
    }

    LOGI("Model NCNN Real-ESRGAN berhasil di-load! GPU Vulkan: %s", g_has_gpu ? "AKTIF" : "OFF");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_service_NcnnVideoBridge_destroyEngine(JNIEnv *env, jclass clazz) {
    if (g_net != nullptr) {
        delete g_net;
        g_net = nullptr;
    }
    ncnn::destroy_gpu_instance();
    LOGI("Mesin NCNN dimatikan.");
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_NcnnVideoBridge_processFrame(JNIEnv *env, jclass clazz, jobject bitmap, jboolean useGpu) {
    if (g_net == nullptr) {
        LOGE("Mesin NCNN belum nyala!");
        return nullptr;
    }

    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Gagal mengunci pixels dari Bitmap Kotlin.");
        return nullptr;
    }

    // 1. Konversi Bitmap Android (RGBA) ke format matriks AI NCNN (RGB)
    ncnn::Mat in = ncnn::Mat::from_pixels((const unsigned char*)pixels, ncnn::Mat::PIXEL_RGBA2RGB, info.width, info.height);
    
    // Lepas kunci gambar asli di sini (Cukup 1x aja!)
    AndroidBitmap_unlockPixels(env, bitmap); 

    // 2. EKSEKUSI AI UPSCALE
    ncnn::Extractor ex = g_net->create_extractor();
    
    // Pintu masuknya pakai "data"
    ex.input("data", in); 
    
    ncnn::Mat out;
    // Pintu keluarnya pakai "output"
    ex.extract("output", out); 

    // Keamanan biar nggak force close kalau gagal
    if (out.empty()) {
        LOGE("WADUH! Gambar AI kosong. Ekstraksi gagal!");
        // Baris unlock dihapus dari sini biar nggak crash
        return nullptr;
    }

    // 3. Buat "Kanvas" Bitmap BARU di Kotlin untuk menampung gambar HD yang udah membesar
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID fid = env->GetStaticFieldID(configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888 = env->GetStaticObjectField(configClass, fid);
    jmethodID create = env->GetStaticMethodID(bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    
    // out.w dan out.h sekarang udah berukuran 2x lipat (karena model x2)
    jobject resultBitmap = env->CallStaticObjectMethod(bitmapClass, create, out.w, out.h, argb8888);

    // 4. Tuangkan hasil AI (RGB) ke dalam Kanvas Bitmap Baru (RGBA)
    void* resultPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, resultBitmap, &resultPixels) >= 0) {
        out.to_pixels((unsigned char*)resultPixels, ncnn::Mat::PIXEL_RGB2RGBA);
        AndroidBitmap_unlockPixels(env, resultBitmap);
    }

    return resultBitmap; // Kirim balik gambar HD ke Kotlin!
}
