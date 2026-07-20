#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>

#include "MNN/Interpreter.hpp"
#include "MNN/MNNDefine.h"
#include "MNN/Tensor.hpp"

#define TAG "KytheraEnhance"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static MNN::Interpreter* g_net     = nullptr;
static MNN::Session*     g_session = nullptr;
static std::string       g_modelPath;

static bool DEBUG_BYPASS_MNN = false;

// ─── Bitmap → RGBA bytes (Stride Safe) ───────────────────────────────────────
static bool bitmapToRGBA(JNIEnv* env, jobject bitmap, std::vector<uint8_t>& out, int& w, int& h) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return false;
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return false;
    
    w = (int)info.width;
    h = (int)info.height;
    out.resize(w * h * 4);
    
    uint8_t* src = (uint8_t*)pixels;
    uint8_t* dst = out.data();
    int rowBytes = w * 4;
    for (int y = 0; y < h; y++) {
        memcpy(dst + y * rowBytes, src + y * info.stride, rowBytes);
    }
    
    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

// ─── RGBA bytes → Bitmap (Stride Safe) ───────────────────────────────────────
static jobject rgbaToBitmap(JNIEnv* env, const uint8_t* rgba, int w, int h) {
    jclass bitmapClass  = env->FindClass("android/graphics/Bitmap");
    jclass configClass  = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888Id = env->GetStaticFieldID(configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888    = env->GetStaticObjectField(configClass, argb8888Id);
    jmethodID create    = env->GetStaticMethodID(bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    
    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, create, w, h, argb8888);
    if (!bitmap) return nullptr;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return nullptr;

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return nullptr;
    
    uint8_t* dst = (uint8_t*)pixels;
    const uint8_t* src = rgba;
    int rowBytes = w * 4;
    for (int y = 0; y < h; y++) {
        memcpy(dst + y * info.stride, src + y * rowBytes, rowBytes);
    }
    
    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}

// ─── JNI: loadModel ───────────────────────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_loadModel(JNIEnv* env, jobject, jstring modelPath, jint gpuMode) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    std::string pathStr(path);
    env->ReleaseStringUTFChars(modelPath, path);

    if (DEBUG_BYPASS_MNN) {
        g_modelPath = pathStr;
        return JNI_TRUE;
    }

    if (g_net && g_session && g_modelPath == pathStr) return JNI_TRUE;
    if (g_net && g_session) { g_net->releaseSession(g_session); g_session = nullptr; }
    if (g_net) { MNN::Interpreter::destroy(g_net); g_net = nullptr; }

    g_net = MNN::Interpreter::createFromFile(pathStr.c_str());
    if (!g_net) { LOGE("Failed to load model"); return JNI_FALSE; }

    MNN::ScheduleConfig config;
    MNN::BackendConfig backendCfg;
    if (gpuMode == 0) {
        backendCfg.precision = MNN::BackendConfig::Precision_Low;
        backendCfg.power     = MNN::BackendConfig::Power_High;
        config.type          = MNN_FORWARD_OPENCL;
        config.backendConfig = &backendCfg;
    } else {
        config.type      = MNN_FORWARD_CPU;
        config.numThread = 4;
    }

    g_session = g_net->createSession(config);
    if (!g_session) {
        config.type = MNN_FORWARD_CPU;
        config.backendConfig = nullptr;
        g_session = g_net->createSession(config);
    }
    if (!g_session) return JNI_FALSE;

    g_modelPath = pathStr;
    LOGI("Model loaded OK");
    return JNI_TRUE;
}

// ─── JNI: enhanceFrame ────────────────────────────────────────────────────────
extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_enhanceFrame(JNIEnv* env, jobject, jobject inputBitmap) {
    std::vector<uint8_t> inputRGBA;
    int inW = 0, inH = 0;
    if (!bitmapToRGBA(env, inputBitmap, inputRGBA, inW, inH)) return nullptr;

    if (DEBUG_BYPASS_MNN) return rgbaToBitmap(env, inputRGBA.data(), inW, inH);
    if (!g_net || !g_session) return nullptr;

    auto* inputTensor = g_net->getSessionInput(g_session, nullptr);
    
    // 1. Resize bentuk inputnya
    g_net->resizeTensor(inputTensor, {1, 3, inH, inW});
    g_net->resizeSession(g_session);

    // 🔥 FIX UTAMA: Pakai "new" biar RAM fisik beneran teralokasi! 
    // Format CAFFE = NCHW sesuai spesifikasi model
    auto* hostIn = new MNN::Tensor(inputTensor, MNN::Tensor::CAFFE);
    float* ptrIn = hostIn->host<float>();

    // 2. Mapping warna RGBA (Mentah) -> NCHW (0.0f - 1.0f)
    for (int y = 0; y < inH; y++) {
        for (int x = 0; x < inW; x++) {
            int idx = y * inW + x;
            ptrIn[0 * inH * inW + idx] = inputRGBA[idx * 4 + 0] / 255.0f; // R
            ptrIn[1 * inH * inW + idx] = inputRGBA[idx * 4 + 1] / 255.0f; // G
            ptrIn[2 * inH * inW + idx] = inputRGBA[idx * 4 + 2] / 255.0f; // B
        }
    }

    // 3. Masukin ke mesin AI & Eksekusi
    inputTensor->copyFromHostTensor(hostIn);
    delete hostIn; 
    
    g_net->runSession(g_session);

    // 4. Ambil Hasil
    auto* outTensor = g_net->getSessionOutput(g_session, nullptr);
    if (!outTensor) return nullptr;

    // 🔥 Bikin wadah output dengan memori fisik dan pastikan formatnya NCHW
    auto* hostOut = new MNN::Tensor(outTensor, MNN::Tensor::CAFFE);
    outTensor->copyToHostTensor(hostOut); 

    // Panggil helper bawaan MNN biar gak salah comot ukuran
    int outW = hostOut->width();
    int outH = hostOut->height();
    float* ptrOut = hostOut->host<float>();

    std::vector<uint8_t> finalRGBA(outW * outH * 4);
    
    // 5. Mapping balik NCHW -> RGBA
    for (int y = 0; y < outH; y++) {
        for (int x = 0; x < outW; x++) {
            int idx = y * outW + x;
            finalRGBA[idx * 4 + 0] = (uint8_t)std::max(0.0f, std::min(255.0f, ptrOut[0 * outH * outW + idx] * 255.0f));
            finalRGBA[idx * 4 + 1] = (uint8_t)std::max(0.0f, std::min(255.0f, ptrOut[1 * outH * outW + idx] * 255.0f));
            finalRGBA[idx * 4 + 2] = (uint8_t)std::max(0.0f, std::min(255.0f, ptrOut[2 * outH * outW + idx] * 255.0f));
            finalRGBA[idx * 4 + 3] = 255;
        }
    }

    delete hostOut; 
    return rgbaToBitmap(env, finalRGBA.data(), outW, outH);
}

extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_release(JNIEnv*, jobject) {
    if (g_net && g_session) { g_net->releaseSession(g_session); g_session = nullptr; }
    if (g_net) { MNN::Interpreter::destroy(g_net); g_net = nullptr; }
    g_modelPath.clear();
}
