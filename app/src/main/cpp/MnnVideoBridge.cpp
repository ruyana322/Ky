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
static int               g_lastW   = 0;
static int               g_lastH   = 0;

// ─── Bitmap → RGBA bytes [0-255] ─────────────────────────────────────────────
static bool bitmapToRGBA(JNIEnv* env, jobject bitmap,
                          std::vector<uint8_t>& out, int& w, int& h) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return false;
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return false;
    w = (int)info.width;
    h = (int)info.height;
    out.resize((size_t)(w * h * 4));
    memcpy(out.data(), pixels, out.size());
    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

// ─── RGBA bytes [0-255] → Bitmap ─────────────────────────────────────────────
static jobject rgbaToBitmap(JNIEnv* env, const uint8_t* rgba, int w, int h) {
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jclass configClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID fid       = env->GetStaticFieldID(configClass, "ARGB_8888",
                           "Landroid/graphics/Bitmap$Config;");
    jobject cfg        = env->GetStaticObjectField(configClass, fid);
    jmethodID create   = env->GetStaticMethodID(bitmapClass, "createBitmap",
                           "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject bmp = env->CallStaticObjectMethod(bitmapClass, create, w, h, cfg);
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bmp, &pixels) < 0) return nullptr;
    memcpy(pixels, rgba, (size_t)(w * h * 4));
    AndroidBitmap_unlockPixels(env, bmp);
    return bmp;
}

// ─── JNI: loadModel ───────────────────────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_loadModel(
        JNIEnv* env, jobject, jstring modelPath, jint gpuMode) {

    const char* p = env->GetStringUTFChars(modelPath, nullptr);
    std::string path(p);
    env->ReleaseStringUTFChars(modelPath, p);

    if (g_net && g_session && g_modelPath == path) {
        LOGI("Model already loaded");
        return JNI_TRUE;
    }

    // Cleanup
    if (g_net && g_session) { g_net->releaseSession(g_session); g_session = nullptr; }
    if (g_net) { MNN::Interpreter::destroy(g_net); g_net = nullptr; }
    g_lastW = g_lastH = 0;

    LOGI("Loading model: %s", path.c_str());
    g_net = MNN::Interpreter::createFromFile(path.c_str());
    if (!g_net) { LOGE("Failed to load model"); return JNI_FALSE; }

    MNN::ScheduleConfig config;
    MNN::BackendConfig backendCfg;

    if (gpuMode == 0) {
        backendCfg.precision = MNN::BackendConfig::Precision_Low;
        backendCfg.power     = MNN::BackendConfig::Power_High;
        config.type          = MNN_FORWARD_OPENCL;
        config.backendConfig = &backendCfg;
        LOGI("Backend: GPU OpenCL");
    } else {
        config.type      = MNN_FORWARD_CPU;
        config.numThread = 4;
        LOGI("Backend: CPU x4");
    }

    g_session = g_net->createSession(config);
    if (!g_session) {
        LOGE("GPU failed, fallback CPU");
        config.type          = MNN_FORWARD_CPU;
        config.numThread     = 4;
        config.backendConfig = nullptr;
        g_session            = g_net->createSession(config);
    }
    if (!g_session) {
        MNN::Interpreter::destroy(g_net); g_net = nullptr;
        return JNI_FALSE;
    }

    g_modelPath = path;
    LOGI("Model loaded OK ✅");
    return JNI_TRUE;
}

// ─── JNI: enhanceFrame ────────────────────────────────────────────────────────
extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_enhanceFrame(
        JNIEnv* env, jobject, jobject inputBitmap) {

    if (!g_net || !g_session) {
        LOGE("Model not loaded!");
        return nullptr;
    }

    // Bitmap → RGBA bytes
    std::vector<uint8_t> inputRGBA;
    int inW = 0, inH = 0;
    if (!bitmapToRGBA(env, inputBitmap, inputRGBA, inW, inH)) return nullptr;

    // Resize tensor hanya kalau ukuran berubah
    if (inW != g_lastW || inH != g_lastH) {
        auto* inputTensor = g_net->getSessionInput(g_session, nullptr);
        g_net->resizeTensor(inputTensor, {1, 3, inH, inW});
        g_net->resizeSession(g_session);
        g_lastW = inW; g_lastH = inH;
        LOGI("Resized tensor: %dx%d", inW, inH);
    }

    int n = inW * inH;

    // RGBA [0-255] → float NCHW [0.0-1.0]
    std::vector<float> inputFloat(3 * n);
    for (int i = 0; i < n; i++) {
        inputFloat[0 * n + i] = inputRGBA[i*4+0] / 255.0f; // R
        inputFloat[1 * n + i] = inputRGBA[i*4+1] / 255.0f; // G
        inputFloat[2 * n + i] = inputRGBA[i*4+2] / 255.0f; // B
    }

    // Copy ke input tensor
    auto* inputTensor = g_net->getSessionInput(g_session, nullptr);
    auto* hostIn = MNN::Tensor::create<float>(
        {1, 3, inH, inW}, inputFloat.data(), MNN::Tensor::CAFFE
    );
    inputTensor->copyFromHostTensor(hostIn);
    delete hostIn;

    // Run inference
    g_net->runSession(g_session);

    // Ambil output
    auto* outTensor = g_net->getSessionOutput(g_session, nullptr);
    auto shape = outTensor->shape();
    if (shape.size() < 4) { LOGE("Bad output shape"); return nullptr; }

    int outH = shape[2], outW = shape[3], outN = outH * outW;
    LOGI("Enhanced: %dx%d → %dx%d", inW, inH, outW, outH);

    // Copy output ke host
    std::vector<float> outFloat(3 * outN);
    auto* hostOut = MNN::Tensor::create<float>(
        {1, 3, outH, outW}, outFloat.data(), MNN::Tensor::CAFFE
    );
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
    g_modelPath.clear(); g_lastW = g_lastH = 0;
    LOGI("Released");
}
