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

// Saklar MNN udah nyala
static bool DEBUG_BYPASS_MNN = false;

// ─── Bitmap → RGBA bytes (Aman dari Stride Padding) ──────────────────────────
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

// ─── RGBA bytes → Bitmap (Aman dari Stride Padding) ──────────────────────────
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
    if (!g_session) {
        MNN::Interpreter::destroy(g_net); g_net = nullptr;
        return JNI_FALSE;
    }

    g_modelPath = pathStr;
    LOGI("Model loaded OK");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_enhanceFrame(JNIEnv* env, jobject, jobject inputBitmap) {
    std::vector<uint8_t> inputRGBA;
    int inW = 0, inH = 0;
    if (!bitmapToRGBA(env, inputBitmap, inputRGBA, inW, inH)) return nullptr;

    if (DEBUG_BYPASS_MNN) return rgbaToBitmap(env, inputRGBA.data(), inW, inH);
    if (!g_net || !g_session) return nullptr;

    auto* inputTensor = g_net->getSessionInput(g_session, nullptr);
    
    // 🔥 FIX 1: PAKSA FORMAT NCHW SESUAI ANALISIS (Bypass deteksi otomatis MNN)
    g_net->resizeTensor(inputTensor, {1, 3, inH, inW});
    g_net->resizeSession(g_session);

    // Bikin memori penampung Float32 dengan format NCHW (Caffe)
    auto* hostIn = MNN::Tensor::create<float>({1, 3, inH, inW}, NULL, MNN::Tensor::CAFFE);
    float* ptrIn = hostIn->host<float>();

    // 🔥 FIX 2: BACA RGB, BAGI 255.0f, SUSUN JADI NCHW (0.0 to 1.0)
    for (int y = 0; y < inH; y++) {
        for (int x = 0; x < inW; x++) {
            int idx = y * inW + x;
            ptrIn[0 * inH * inW + idx] = (float)inputRGBA[idx * 4 + 0] / 255.0f; // Red
            ptrIn[1 * inH * inW + idx] = (float)inputRGBA[idx * 4 + 1] / 255.0f; // Green
            ptrIn[2 * inH * inW + idx] = (float)inputRGBA[idx * 4 + 2] / 255.0f; // Blue
        }
    }

    inputTensor->copyFromHostTensor(hostIn);
    delete hostIn;

    // RUN AI ENGINE
    g_net->runSession(g_session);

    auto* outTensor = g_net->getSessionOutput(g_session, nullptr);
    if (!outTensor) return nullptr;

    auto shape = outTensor->shape();
    if (shape.size() < 4) return nullptr;

    // Output dari model juga NCHW
    int outH = shape[2]; 
    int outW = shape[3];

    // Bikin memori penampung Float32 buat narik hasil NCHW
    auto* hostOut = MNN::Tensor::create<float>(shape, NULL, MNN::Tensor::CAFFE);
    outTensor->copyToHostTensor(hostOut); 
    float* ptrOut = hostOut->host<float>();

    std::vector<uint8_t> finalRGBA(outW * outH * 4);
    
    // 🔥 FIX 3: KALI 255.0f, SUSUN BALIK DARI NCHW KE RGBA (Biar bisa jadi Bitmap)
    for (int y = 0; y < outH; y++) {
        for (int x = 0; x < outW; x++) {
            int idx = y * outW + x;
            // Clamp and scale
            finalRGBA[idx * 4 + 0] = (uint8_t)std::max(0.0f, std::min(255.0f, ptrOut[0 * outH * outW + idx] * 255.0f));
            finalRGBA[idx * 4 + 1] = (uint8_t)std::max(0.0f, std::min(255.0f, ptrOut[1 * outH * outW + idx] * 255.0f));
            finalRGBA[idx * 4 + 2] = (uint8_t)std::max(0.0f, std::min(255.0f, ptrOut[2 * outH * outW + idx] * 255.0f));
            finalRGBA[idx * 4 + 3] = 255; // Alpha
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
