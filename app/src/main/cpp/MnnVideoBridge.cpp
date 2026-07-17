#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <vector>

// MNN Headers — dari MNN source include/
#include "MNN/Interpreter.hpp"
#include "MNN/MNNDefine.h"
#include "MNN/Tensor.hpp"
#include "MNN/ImageProcess.hpp"

#define TAG "KytheraVideoMNN"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ─── Global state ─────────────────────────────────────────────────────────────
static MNN::Interpreter* g_net     = nullptr;
static MNN::Session*     g_session = nullptr;
static std::string       g_loadedModel;
static int               g_inW = 0, g_inH = 0;

// ─── Helper: Bitmap → float NCHW ──────────────────────────────────────────────
static bool bitmapToFloat(JNIEnv* env, jobject bitmap,
                           std::vector<float>& out, int& w, int& h) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("getInfo failed");
        return false;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap must be ARGB_8888");
        return false;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("lockPixels failed");
        return false;
    }

    w = (int)info.width;
    h = (int)info.height;
    int n = w * h;

    // NCHW: [1, 3, H, W]
    out.resize(3 * n);
    auto* src = (uint8_t*)pixels;

    for (int i = 0; i < n; i++) {
        // RGBA → RGB normalized [0,1]
        out[0 * n + i] = src[i * 4 + 0] / 255.0f; // R
        out[1 * n + i] = src[i * 4 + 1] / 255.0f; // G
        out[2 * n + i] = src[i * 4 + 2] / 255.0f; // B
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

// ─── Helper: float NCHW → Bitmap ──────────────────────────────────────────────
static jobject floatToBitmap(JNIEnv* env, const float* data, int w, int h) {
    // Buat Bitmap via Android API
    jclass bitmapClass  = env->FindClass("android/graphics/Bitmap");
    jclass configClass  = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888Id = env->GetStaticFieldID(configClass, "ARGB_8888",
                                                  "Landroid/graphics/Bitmap$Config;");
    jobject argb8888    = env->GetStaticObjectField(configClass, argb8888Id);

    jmethodID createBitmap = env->GetStaticMethodID(bitmapClass, "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject outBitmap = env->CallStaticObjectMethod(bitmapClass, createBitmap,
                                                     w, h, argb8888);

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, outBitmap, &pixels) < 0) {
        LOGE("lockPixels output failed");
        return nullptr;
    }

    int n = w * h;
    auto* dst = (uint8_t*)pixels;

    for (int i = 0; i < n; i++) {
        // Clamp [0,1] → [0,255]
        auto r = (uint8_t)(std::min(std::max(data[0 * n + i], 0.0f), 1.0f) * 255.0f);
        auto g = (uint8_t)(std::min(std::max(data[1 * n + i], 0.0f), 1.0f) * 255.0f);
        auto b = (uint8_t)(std::min(std::max(data[2 * n + i], 0.0f), 1.0f) * 255.0f);
        dst[i * 4 + 0] = r;
        dst[i * 4 + 1] = g;
        dst[i * 4 + 2] = b;
        dst[i * 4 + 3] = 255; // Alpha
    }

    AndroidBitmap_unlockPixels(env, outBitmap);
    return outBitmap;
}

// ─── JNI: loadModel ───────────────────────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_loadModel(
        JNIEnv* env, jobject /* this */,
        jstring modelPath,
        jint    gpuMode) {   // 0=GPU OpenCL, 1=CPU

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    std::string pathStr(path);
    env->ReleaseStringUTFChars(modelPath, path);

    // Jika model sama sudah loaded, skip
    if (g_net != nullptr && g_loadedModel == pathStr) {
        LOGI("Model already loaded: %s", pathStr.c_str());
        return JNI_TRUE;
    }

    // Cleanup session lama
    if (g_net && g_session) {
        g_net->releaseSession(g_session);
        g_session = nullptr;
    }
    if (g_net) {
        MNN::Interpreter::destroy(g_net);
        g_net = nullptr;
    }

    LOGI("Loading model: %s", pathStr.c_str());
    g_net = MNN::Interpreter::createFromFile(pathStr.c_str());
    if (!g_net) {
        LOGE("Failed to create Interpreter from: %s", pathStr.c_str());
        return JNI_FALSE;
    }

    // Config: GPU OpenCL atau CPU
    MNN::ScheduleConfig config;
    config.numThread = 4;

    if (gpuMode == 0) {
        // GPU OpenCL — sama kayak Speedkythera
        MNN::BackendConfig backendConfig;
        backendConfig.precision = MNN::BackendConfig::Precision_Low;
        backendConfig.power     = MNN::BackendConfig::Power_High;
        config.type             = MNN_FORWARD_OPENCL;
        config.backendConfig    = &backendConfig;
        LOGI("Backend: GPU OpenCL");
    } else {
        // CPU fallback
        config.type = MNN_FORWARD_CPU;
        LOGI("Backend: CPU");
    }

    g_session = g_net->createSession(config);
    if (!g_session) {
        // Fallback ke CPU kalau GPU gagal
        LOGE("GPU session failed, fallback CPU...");
        config.type = MNN_FORWARD_CPU;
        g_session   = g_net->createSession(config);
        if (!g_session) {
            LOGE("CPU session also failed");
            MNN::Interpreter::destroy(g_net);
            g_net = nullptr;
            return JNI_FALSE;
        }
    }

    g_loadedModel = pathStr;
    LOGI("Model loaded OK ✅");
    return JNI_TRUE;
}

// ─── JNI: enhanceFrame ────────────────────────────────────────────────────────
extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_enhanceFrame(
        JNIEnv* env, jobject /* this */,
        jobject inputBitmap) {

    if (!g_net || !g_session) {
        LOGE("Model not loaded!");
        return nullptr;
    }

    // Bitmap → float tensor
    std::vector<float> inputData;
    int inW = 0, inH = 0;
    if (!bitmapToFloat(env, inputBitmap, inputData, inW, inH)) {
        return nullptr;
    }

    // Resize input tensor kalau ukuran berubah
    if (inW != g_inW || inH != g_inH) {
        auto* inputTensor = g_net->getSessionInput(g_session, nullptr);
        g_net->resizeTensor(inputTensor, {1, 3, inH, inW});
        g_net->resizeSession(g_session);
        g_inW = inW;
        g_inH = inH;
        LOGI("Resized input to %dx%d", inW, inH);
    }

    // Copy input data ke tensor
    auto* inputTensor = g_net->getSessionInput(g_session, nullptr);
    auto* nchwTensor  = MNN::Tensor::create<float>(
        {1, 3, inH, inW}, inputData.data(), MNN::Tensor::CAFFE
    );
    inputTensor->copyFromHostTensor(nchwTensor);
    delete nchwTensor;

    // Run inference
    g_net->runSession(g_session);

    // Ambil output
    auto* outputTensor = g_net->getSessionOutput(g_session, nullptr);
    auto shape = outputTensor->shape(); // [1, 3, outH, outW]
    if (shape.size() < 4) {
        LOGE("Unexpected output shape");
        return nullptr;
    }

    int outH = shape[2];
    int outW = shape[3];
    int outN = outH * outW;

    std::vector<float> outputData(3 * outN);
    auto* hostOut = MNN::Tensor::create<float>(
        {1, 3, outH, outW}, outputData.data(), MNN::Tensor::CAFFE
    );
    outputTensor->copyToHostTensor(hostOut);
    delete hostOut;

    LOGI("Enhanced: %dx%d → %dx%d", inW, inH, outW, outH);

    // float → Bitmap
    return floatToBitmap(env, outputData.data(), outW, outH);
}

// ─── JNI: release ─────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_release(
        JNIEnv* /* env */, jobject /* this */) {

    if (g_net && g_session) {
        g_net->releaseSession(g_session);
        g_session = nullptr;
    }
    if (g_net) {
        MNN::Interpreter::destroy(g_net);
        g_net = nullptr;
    }
    g_loadedModel.clear();
    g_inW = g_inH = 0;
    LOGI("MNN resources released");
}
