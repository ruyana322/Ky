#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>

// Header NCNN
#include "net.h"
#include "gpu.h"

// OpenCV Headers
#include "opencv2/core.hpp"
#include "opencv2/videoio.hpp"
#include "opencv2/imgproc.hpp"

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

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_NcnnVideoBridge_processVideoNative(JNIEnv *env, jclass clazz, jstring inPath, jstring outPath, jint rotation, jobject callback) {
    if (g_net == nullptr) {
        LOGE("Mesin NCNN belum nyala!");
        return JNI_FALSE;
    }

    const char* inStr = env->GetStringUTFChars(inPath, nullptr);
    const char* outStr = env->GetStringUTFChars(outPath, nullptr);
    std::string inputPath(inStr);
    std::string outputPath(outStr);
    env->ReleaseStringUTFChars(inPath, inStr);
    env->ReleaseStringUTFChars(outPath, outStr);

    cv::VideoCapture cap;
    cap.open(inputPath);
    if (!cap.isOpened()) {
        LOGE("Gagal buka input video: %s", inputPath.c_str());
        return JNI_FALSE;
    }

    int totalFrames = (int)cap.get(cv::CAP_PROP_FRAME_COUNT);
    double fps = cap.get(cv::CAP_PROP_FPS);
    int width = (int)cap.get(cv::CAP_PROP_FRAME_WIDTH);
    int height = (int)cap.get(cv::CAP_PROP_FRAME_HEIGHT);

    if (fps <= 0 || fps > 120) fps = 30.0;

    int outW = (rotation == 90 || rotation == 270) ? height : width;
    int outH = (rotation == 90 || rotation == 270) ? width : height;
    
    // Karena model x2, ukuran output jadi 2x lipat
    outW *= 2;
    outH *= 2;

    cv::VideoWriter writer;
    int fourcc = cv::VideoWriter::fourcc('H','2','6','4');
    writer.open(outputPath, fourcc, fps, cv::Size(outW, outH));
    if (!writer.isOpened()) {
        fourcc = cv::VideoWriter::fourcc('a','v','c','1');
        writer.open(outputPath, fourcc, fps, cv::Size(outW, outH));
    }
    if (!writer.isOpened()) {
        LOGE("Gagal buka output video writer!");
        cap.release();
        return JNI_FALSE;
    }

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onProgress = env->GetMethodID(cbClass, "onProgress", "(IF)V");

    int frameIdx = 0;
    cv::Mat frame;
    
    // Ambil waktu mulai
    double t_start = (double)cv::getTickCount();

    ncnn::Extractor ex = g_net->create_extractor();

    while (cap.read(frame)) {
        if (frame.empty()) break;
        
        cv::Mat rotated;
        switch (rotation) {
            case 90:  cv::rotate(frame, rotated, cv::ROTATE_90_CLOCKWISE); break;
            case 180: cv::rotate(frame, rotated, cv::ROTATE_180); break;
            case 270: cv::rotate(frame, rotated, cv::ROTATE_90_COUNTERCLOCKWISE); break;
            default:  rotated = frame; break;
        }

        // BGR (OpenCV) -> RGB (NCNN)
        ncnn::Mat in = ncnn::Mat::from_pixels(rotated.data, ncnn::Mat::PIXEL_BGR2RGB, rotated.cols, rotated.rows);

        ex.input("data", in);
        ncnn::Mat out;
        ex.extract("output", out);

        if (out.empty()) {
            LOGE("Frame AI kosong, skip.");
            continue;
        }

        // NCNN (RGB) -> OpenCV (BGR)
        cv::Mat outMat(out.h, out.w, CV_8UC3);
        out.to_pixels(outMat.data, ncnn::Mat::PIXEL_RGB2BGR);

        writer.write(outMat);

        frameIdx++;

        if (frameIdx % 2 == 0) {
            double elapsed = ((double)cv::getTickCount() - t_start) / cv::getTickFrequency();
            float processFps = (elapsed > 0) ? (float)(frameIdx / elapsed) : 0.0f;
            env->CallVoidMethod(callback, onProgress, frameIdx, processFps);
        }
    }

    cap.release();
    writer.release();
    
    return JNI_TRUE;
}
