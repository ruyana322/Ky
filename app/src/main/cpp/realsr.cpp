#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>

#include "ncnn/net.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "Kythera_AI", __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "Kythera_AI", __VA_ARGS__)

static ncnn::Net* net = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_RealSrEngine_initModel(JNIEnv* env, jobject thiz, jstring paramPath, jstring binPath) {
    const char* param_path = env->GetStringUTFChars(paramPath, 0);
    const char* bin_path = env->GetStringUTFChars(binPath, 0);

    if (net == nullptr) {
        net = new ncnn::Net();
    }
    
    // =========================================================
    // PUSAT KENDALI GPU (VULKAN) ADA DI SINI
    // Ubah jadi "true" kalau nanti lu udah mau ngetes pakai GPU
    // =========================================================
    net->opt.use_vulkan_compute = false; 

    int ret_param = net->load_param(param_path);
    int ret_bin = net->load_model(bin_path);

    env->ReleaseStringUTFChars(paramPath, param_path);
    env->ReleaseStringUTFChars(binPath, bin_path);

    if (ret_param != 0 || ret_bin != 0) {
        LOGE("Gagal baca file model AI!");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_RealSrEngine_processBitmap(JNIEnv* env, jobject thiz, jobject bitmap) {
    if (net == nullptr) {
        LOGE("Mesin AI belum nyala!");
        return nullptr;
    }

    ncnn::Mat in = ncnn::Mat::from_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_RGB);
    if (in.empty()) {
        LOGE("Gagal ngebaca Poto!");
        return nullptr;
    }

    // Bikin alat ekstrak (Otomatis ngikutin settingan GPU dari 'net')
    ncnn::Extractor ex = net->create_extractor();
    
    ncnn::Mat out;
    
    // ===============================================
    // TES NAMA NODE: Kalau "data" gagal, coba "in0"
    // ===============================================
    ex.input("data", in);
    int ret = ex.extract("output", out);

    if (ret != 0 || out.empty()) {
        LOGD("Node 'data' gagal! Coba pakai nama 'in0' khas ChaiNNer...");
        ex.input("in0", in);
        ret = ex.extract("out0", out);
    }

    if (ret != 0 || out.empty()) {
        LOGE("AI gagal total! Model rusak atau Node tidak dikenali.");
        return nullptr;
    }

    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888FieldID = env->GetStaticFieldID(bitmapConfigClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888Obj = env->GetStaticObjectField(bitmapConfigClass, argb8888FieldID);

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethodID = env->GetStaticMethodID(bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject newBitmap = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodID, out.w, out.h, argb8888Obj);

    out.to_android_bitmap(env, newBitmap, ncnn::Mat::PIXEL_RGB);

    return newBitmap;
}
