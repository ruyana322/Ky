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

// ─── DEBUG MODE: bypass MNN, return input as-is ──────────────────────────────
// Set ke false kalau mau pakai MNN beneran
static bool DEBUG_BYPASS_MNN = true;

// ─── Bitmap → RGBA bytes ─────────────────────────────────────────────────────
static bool bitmapToRGBA(JNIEnv* env, jobject bitmap,
                          std::vector<uint8_t>& out, int& w, int& h) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return false;
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return false;
    w = (int)info.width;
    h = (int)info.height;
    out.resize(w * h * 4);
    memcpy(out.data(), pixels, w * h * 4);
    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

// ─── RGBA bytes → Bitmap ─────────────────────────────────────────────────────
static jobject rgbaToBitmap(JNIEnv* env, const uint8_t* rgba, int w, int h) {
    jclass bitmapClass  = env->FindClass("android/graphics/Bitmap");
    jclass configClass  = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888Id = env->GetStaticFieldID(configClass, "ARGB_8888",
                            "Landroid/graphics/Bitmap$Config;");
    jobject argb8888    = env->GetStaticObjectField(configClass, argb8888Id);
    jmethodID create    = env->GetStaticMethodID(bitmapClass, "createBitmap",
                            "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, create, w, h, argb8888);
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return nullptr;
    memcpy(pixels, rgba, (size_t)(w * h * 4));
    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}

// ─── JNI: loadModel ───────────────────────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_loadModel(
        JNIEnv* env, jobject, jstring modelPath, jint gpuMode) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    std::string pathStr(path);
    env->ReleaseStringUTFChars(modelPath, path);

    if (DEBUG_BYPASS_MNN) {
        LOGI("DEBUG: bypass mode ON — skip model load");
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

// ─── JNI: enhanceFrame ────────────────────────────────────────────────────────
extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_enhanceFrame(
        JNIEnv* env, jobject, jobject inputBitmap) {

    std::vector<uint8_t> inputRGBA;
    int inW = 0, inH = 0;
    if (!bitmapToRGBA(env, inputBitmap, inputRGBA, inW, inH)) return nullptr;

    // ── DEBUG: bypass MNN, return frame asli ─────────────────────────────────
    if (DEBUG_BYPASS_MNN) {
        LOGI("DEBUG: returning original frame %dx%d", inW, inH);
        return rgbaToBitmap(env, inputRGBA.data(), inW, inH);
    }

    // ── REAL MNN inference ────────────────────────────────────────────────────
    if (!g_net || !g_session) return nullptr;

    // Resize tensor
    auto* inputTensor = g_net->getSessionInput(g_session, nullptr);
    g_net->resizeTensor(inputTensor, {1, 3, inH, inW});
    g_net->resizeSession(g_session);

    // RGBA [0-255] → float NCHW [0-1]
    int n = inW * inH;
    std::vector<float> inputFloat(3 * n);
    for (int i = 0; i < n; i++) {
        inputFloat[0 * n + i] = inputRGBA[i*4+0] / 255.0f; // R
        inputFloat[1 * n + i] = inputRGBA[i*4+1] / 255.0f; // G
        inputFloat[2 * n + i] = inputRGBA[i*4+2] / 255.0f; // B
    }

    auto* hostIn = MNN::Tensor::create<float>({1,3,inH,inW}, inputFloat.data(), MNN::Tensor::CAFFE);
    inputTensor->copyFromHostTensor(hostIn);
    delete hostIn;

    g_net->runSession(g_session);

    auto* outTensor = g_net->getSessionOutput(g_session, nullptr);
    auto shape = outTensor->shape();
    if (shape.size() < 4) return nullptr;

    int outH = shape[2], outW = shape[3], outN = outH * outW;
    std::vector<float> outFloat(3 * outN);
    auto* hostOut = MNN::Tensor::create<float>({1,3,outH,outW}, outFloat.data(), MNN::Tensor::CAFFE);
    outTensor->copyToHostTensor(hostOut);
    delete hostOut;

    // float NCHW [0-1] → RGBA [0-255]
    std::vector<uint8_t> outRGBA((size_t)(outW * outH * 4));
    for (int i = 0; i < outN; i++) {
        outRGBA[i*4+0] = (uint8_t)(std::max(0.0f, std::min(1.0f, outFloat[0*outN+i])) * 255.0f + 0.5f);
        outRGBA[i*4+1] = (uint8_t)(std::max(0.0f, std::min(1.0f, outFloat[1*outN+i])) * 255.0f + 0.5f);
        outRGBA[i*4+2] = (uint8_t)(std::max(0.0f, std::min(1.0f, outFloat[2*outN+i])) * 255.0f + 0.5f);
        outRGBA[i*4+3] = 255;
    }

    return rgbaToBitmap(env, outRGBA.data(), outW, outH);
}

// ─── JNI: release ─────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_release(JNIEnv*, jobject) {
    if (g_net && g_session) { g_net->releaseSession(g_session); g_session = nullptr; }
    if (g_net) { MNN::Interpreter::destroy(g_net); g_net = nullptr; }
    g_modelPath.clear();
}
