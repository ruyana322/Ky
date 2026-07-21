#include <jni.h>
#include <string>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

// Include library inti NCNN
#include "net.h"
#include "gpu.h"

// Setup untuk log di Logcat Android Studio
#define TAG "NcnnBridgeCPP"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Bikin instance mesin NCNN global biar gak di-load berulang kali
static ncnn::Net realEsrganNet;
static bool isGpuEnabled = false;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_NcnnVideoBridge_initEngine(JNIEnv *env, jobject thiz, jobject assetManager) {
    
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
    
    // 3. Load model anime v3 dari folder assets
    // (Jalur ini disesuaikan dengan struktur di repo GitHub lu)
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
JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_NcnnVideoBridge_processBitmap(JNIEnv *env, jobject thiz, jobject bitmapIn, jobject bitmapOut) {
    AndroidBitmapInfo infoIn;
    AndroidBitmapInfo infoOut;
    void* pixelsIn;
    void* pixelsOut;

    // 1. Baca info gambar sumber (Bitmap In)
    if (AndroidBitmap_getInfo(env, bitmapIn, &infoIn) < 0) {
        LOGE("Gagal baca info Bitmap Input");
        return JNI_FALSE;
    }

    // 2. Baca info gambar tujuan (Bitmap Out)
    if (AndroidBitmap_getInfo(env, bitmapOut, &infoOut) < 0) {
        LOGE("Gagal baca info Bitmap Output");
        return JNI_FALSE;
    }

    // Pastikan ukuran output 2x lebih besar dari input (karena model lu x2)
    if (infoOut.width != infoIn.width * 2 || infoOut.height != infoIn.height * 2) {
        LOGE("Ukuran Bitmap Output harus tepat 2x lipat dari Bitmap Input!");
        return JNI_FALSE;
    }

    // 3. Kunci pixel gambar biar bisa dibaca mesin C++
    AndroidBitmap_lockPixels(env, bitmapIn, &pixelsIn);
    AndroidBitmap_lockPixels(env, bitmapOut, &pixelsOut);

    // 4. Ubah pixel Android (RGBA) jadi format matriks NCNN (RGB)
    ncnn::Mat in = ncnn::Mat::from_pixels((const unsigned char*)pixelsIn, ncnn::Mat::PIXEL_RGBA2RGB, infoIn.width, infoIn.height);

    // 5. Mulai proses Upscale! (Ngunyah gambar)
    ncnn::Extractor ex = realEsrganNet.create_extractor();
    ex.set_light_mode(true); // Biar hemat RAM
    
    // Masukin gambar kotor ke mesin (Node input default biasanya "data")
    ex.input("data", in);

    // Tarik gambar bersih hasil upscale (Node output default biasanya "out")
    ncnn::Mat out;
    ex.extract("out", out);

    // 6. Ubah balik matriks NCNN (RGB) jadi pixel Android (RGBA) dan masukin ke Bitmap Out
    out.to_pixels((unsigned char*)pixelsOut, ncnn::Mat::PIXEL_RGB2RGBA);

    // 7. Buka kunci pixel (Wajib biar memory gak bocor)
    AndroidBitmap_unlockPixels(env, bitmapIn);
    AndroidBitmap_unlockPixels(env, bitmapOut);

    LOGD("Upscale frame berhasil dieksekusi!");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_service_NcnnVideoBridge_destroyEngine(JNIEnv *env, jobject thiz) {
    // Matiin mesin dan bersihin memori biar RAM aman
    realEsrganNet.clear();
    ncnn::destroy_gpu_instance();
    LOGD("Mesin NCNN dimatikan dan memori dibersihkan.");
}
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

// Ini pintu JNI yang dicari-cari sama Kotlin lu
extern "C"
JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_NcnnVideoBridge_processFrame(JNIEnv *env, jclass clazz, jobject bitmap, jboolean useGpu) {
    
    AndroidBitmapInfo info;
    void* pixels;
    
    // 1. Kunci Bitmap dari Kotlin biar bisa dibaca C++
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "NcnnBridge", "Gagal baca info bitmap!");
        return nullptr;
    }
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "NcnnBridge", "Gagal lock pixels!");
        return nullptr;
    }

    // ==========================================================
    // 2. DI SINI TEMPAT MESIN NCNN LU BEKERJA (UPSCALE)
    // ==========================================================
    // Logika Real-ESRGAN / AI Upscale lu taruh di sini, Kang.
    // Variabel 'useGpu' (true/false) dari UI bisa lu pakai buat 
    // ngaktifin Vulkan compute di NCNN.
    //
    // Contoh alurnya (harus disesuaikan sama model AI lu):
    // ncnn::Mat in = ncnn::Mat::from_pixels(...);
    // ncnn::Extractor ex = net.create_extractor();
    // ex.set_vulkan_compute(useGpu);
    // ex.input("in0", in);
    // ncnn::Mat out;
    // ex.extract("out0", out);
    // out.to_pixels((unsigned char*)pixels, ...);
    // ==========================================================

    // 3. Lepas kunci Bitmap setelah selesai diproses
    AndroidBitmap_unlockPixels(env, bitmap);

    // 4. Balikin gambar hasil AI ke Kotlin
    // (Sementara ini ngembaliin gambar aslinya biar aplikasi nggak force close)
    return bitmap;
}

