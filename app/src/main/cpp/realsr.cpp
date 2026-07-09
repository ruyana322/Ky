#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>

#include "ncnn/net.h"
#include "ncnn/gpu.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "Kythera_AI", __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "Kythera_AI", __VA_ARGS__)

static ncnn::Net* net = nullptr;
static bool is_gpu_ready = false;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_RealSrEngine_initModel(JNIEnv* env, jobject thiz, jstring paramPath, jstring binPath) {
    if (!is_gpu_ready) {
        ncnn::create_gpu_instance();
        is_gpu_ready = true;
    }

    const char* param_path = env->GetStringUTFChars(paramPath, 0);
    const char* bin_path = env->GetStringUTFChars(binPath, 0);

    if (net == nullptr) {
        net = new ncnn::Net();
    }
    
    // =================================================================
    // OBAT MALI GPU:
    // Biarin Vulkan nyala, tapi JANGAN PAKSA FP16 secara manual!
    // NCNN bakal mikir sendiri mode apa yang paling aman buat GPU lu.
    // =================================================================
    net->opt.use_vulkan_compute = true; 

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
    if (net == nullptr) return nullptr;

    // BACA GAMBAR + BUANG ALPHA-NYA (Android RGBA -> AI RGB)
    ncnn::Mat in = ncnn::Mat::from_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_RGBA2RGB);
    if (in.empty()) return nullptr;

    // RUMUS NORMALISASI UDAH DIBUANG TOTAL KE TONG SAMPAH!

    ncnn::Extractor ex = net->create_extractor();
    ncnn::Mat out;
    
    ex.input("data", in);
    int ret = ex.extract("output", out);

    if (ret != 0 || out.empty()) {
        ex.input("in0", in);
        ret = ex.extract("out0", out);
    }

    if (ret != 0 || out.empty()) return nullptr;

    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888FieldID = env->GetStaticFieldID(bitmapConfigClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888Obj = env->GetStaticObjectField(bitmapConfigClass, argb8888FieldID);

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethodID = env->GetStaticMethodID(bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject newBitmap = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodID, out.w, out.h, argb8888Obj);

    // BALIKIN KE ANDROID + KASIH ALPHA SOLID 255 (AI RGB -> Android RGBA)
    out.to_android_bitmap(env, newBitmap, ncnn::Mat::PIXEL_RGB2RGBA);

    return newBitmap;
}
