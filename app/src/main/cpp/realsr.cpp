#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <algorithm>

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
    const char* bin_path   = env->GetStringUTFChars(binPath, 0);

    if (net == nullptr) net = new ncnn::Net();

    net->opt.use_vulkan_compute  = true;
    net->opt.use_fp16_packed     = true;
    net->opt.use_fp16_storage    = true;
    net->opt.use_fp16_arithmetic = false; // WAJIB FALSE untuk anime model

    int ret_param = net->load_param(param_path);
    int ret_bin   = net->load_model(bin_path);

    env->ReleaseStringUTFChars(paramPath, param_path);
    env->ReleaseStringUTFChars(binPath, bin_path);

    if (ret_param != 0 || ret_bin != 0) {
        LOGE("Gagal load model!");
        return JNI_FALSE;
    }
    LOGD("Model loaded OK");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_RealSrEngine_processBitmap(JNIEnv* env, jobject thiz, jobject bitmap) {
    if (net == nullptr) return nullptr;

    // Step 1: Baca bitmap → RGB float Mat (nilai 0..255)
    ncnn::Mat in = ncnn::Mat::from_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_RGBA2RGB);
    if (in.empty()) return nullptr;

    // Step 2: Normalize [0,255] → [0,1]
    // Sama persis cara official realesrgan-ncnn-vulkan
    static const float norm[3] = { 1/255.f, 1/255.f, 1/255.f };
    in.substract_mean_normalize(nullptr, norm);

    // Step 3: Inferensi
    ncnn::Extractor ex = net->create_extractor();
    ex.input("data", in);

    ncnn::Mat out;
    int ret = ex.extract("output", out);

    if (ret != 0 || out.empty()) {
        LOGE("Inferensi gagal ret=%d", ret);
        return nullptr;
    }

    LOGD("Output: %dx%d ch=%d", out.w, out.h, out.c);

    // Step 4: Denormalize [0,1] → [0,255] + clamp manual
    int total = out.w * out.h;
    for (int c = 0; c < out.c; c++) {
        float* ptr = out.channel(c);
        for (int i = 0; i < total; i++) {
            ptr[i] = std::min(std::max(ptr[i] * 255.f, 0.f), 255.f);
        }
    }

    // Step 5: Buat output bitmap
    jclass   bmpCfgClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argbID      = env->GetStaticFieldID(bmpCfgClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject  argbObj     = env->GetStaticObjectField(bmpCfgClass, argbID);

    jclass    bmpClass  = env->FindClass("android/graphics/Bitmap");
    jmethodID createID  = env->GetStaticMethodID(bmpClass, "createBitmap",
                          "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject newBmp = env->CallStaticObjectMethod(bmpClass, createID, out.w, out.h, argbObj);

    // Step 6: Tulis RGB → RGBA ke bitmap Android
    out.to_android_bitmap(env, newBmp, ncnn::Mat::PIXEL_RGB2RGBA);

    return newBmp;
}
