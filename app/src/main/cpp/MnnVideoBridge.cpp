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

// ─── Model fixed input size: WAJIB 1024x1024 ─────────────────────────────────
static const int MODEL_SIZE = 1024;

static MNN::Interpreter* g_net      = nullptr;
static MNN::Session*     g_session  = nullptr;
static std::string       g_modelPath;
static int               g_scale    = 2; // 2 untuk d2u2, 4 untuk d4u4

// ─── Bitmap → float NCHW [0,1] dengan resize ke 1024x1024 ───────────────────
// Model WAJIB terima 1024x1024, jadi frame direscale dulu
static bool bitmapToNCHW1024(JNIEnv* env, jobject bitmap,
                              std::vector<float>& out) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return false;
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return false;

    int srcW = (int)info.width;
    int srcH = (int)info.height;
    auto* src = (uint8_t*)pixels;

    // Output fixed 1024x1024x3 NCHW
    int N = MODEL_SIZE * MODEL_SIZE;
    out.resize(3 * N);

    // Bilinear resize ke 1024x1024
    float scaleX = (float)srcW / MODEL_SIZE;
    float scaleY = (float)srcH / MODEL_SIZE;

    for (int dy = 0; dy < MODEL_SIZE; dy++) {
        float fy = dy * scaleY;
        int   y0 = (int)fy;
        int   y1 = std::min(y0 + 1, srcH - 1);
        float wy = fy - y0;

        for (int dx = 0; dx < MODEL_SIZE; dx++) {
            float fx = dx * scaleX;
            int   x0 = (int)fx;
            int   x1 = std::min(x0 + 1, srcW - 1);
            float wx = fx - x0;

            // Bilinear interpolation untuk R, G, B
            for (int c = 0; c < 3; c++) {
                float v00 = src[(y0 * srcW + x0) * 4 + c] / 255.0f;
                float v01 = src[(y0 * srcW + x1) * 4 + c] / 255.0f;
                float v10 = src[(y1 * srcW + x0) * 4 + c] / 255.0f;
                float v11 = src[(y1 * srcW + x1) * 4 + c] / 255.0f;

                float v = v00*(1-wx)*(1-wy) + v01*wx*(1-wy)
                        + v10*(1-wx)*wy     + v11*wx*wy;

                out[c * N + dy * MODEL_SIZE + dx] = v;
            }
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

// ─── float NCHW → Bitmap (output dari model, ukuran 2048 atau 4096) ──────────
static jobject ncHWtoBitmap(JNIEnv* env, const float* data, int w, int h) {
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

    int N = w * h;
    auto* dst = (uint8_t*)pixels;

    for (int i = 0; i < N; i++) {
        dst[i*4+0] = (uint8_t)(std::max(0.0f, std::min(1.0f, data[0*N+i])) * 255.0f + 0.5f); // R
        dst[i*4+1] = (uint8_t)(std::max(0.0f, std::min(1.0f, data[1*N+i])) * 255.0f + 0.5f); // G
        dst[i*4+2] = (uint8_t)(std::max(0.0f, std::min(1.0f, data[2*N+i])) * 255.0f + 0.5f); // B
        dst[i*4+3] = 255; // A
    }

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

    // Deteksi scale dari nama model
    if (path.find("d2u2") != std::string::npos) g_scale = 2;
    else if (path.find("d4u4") != std::string::npos) g_scale = 4;
    else g_scale = 2;

    if (g_net && g_session && g_modelPath == path) {
        LOGI("Model already loaded (scale=%dx)", g_scale);
        return JNI_TRUE;
    }

    if (g_net && g_session) { g_net->releaseSession(g_session); g_session = nullptr; }
    if (g_net) { MNN::Interpreter::destroy(g_net); g_net = nullptr; }

    LOGI("Loading model: %s (scale=%dx)", path.c_str(), g_scale);
    g_net = MNN::Interpreter::createFromFile(path.c_str());
    if (!g_net) { LOGE("Failed load model"); return JNI_FALSE; }

    // Resize input ke fixed 1024x1024 SEBELUM buat session
    auto* inputTensor = g_net->getSessionInput(nullptr, "input");
    if (inputTensor) {
        g_net->resizeTensor(inputTensor, {1, 3, MODEL_SIZE, MODEL_SIZE});
    }

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
        config.type = MNN_FORWARD_CPU; config.numThread = 4; config.backendConfig = nullptr;
        g_session = g_net->createSession(config);
    }
    if (!g_session) {
        MNN::Interpreter::destroy(g_net); g_net = nullptr;
        return JNI_FALSE;
    }

    // Resize session setelah session dibuat
    g_net->resizeSession(g_session);

    g_modelPath = path;
    LOGI("Model loaded OK ✅ input=1024x1024 output=%dx%d",
         MODEL_SIZE * g_scale, MODEL_SIZE * g_scale);
    return JNI_TRUE;
}

// ─── JNI: enhanceFrame ────────────────────────────────────────────────────────
extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_enhanceFrame(
        JNIEnv* env, jobject, jobject inputBitmap) {

    if (!g_net || !g_session) { LOGE("Not loaded!"); return nullptr; }

    // Bitmap → NCHW float [0,1], auto-resize ke 1024x1024
    std::vector<float> inputFloat;
    if (!bitmapToNCHW1024(env, inputBitmap, inputFloat)) return nullptr;

    // Copy ke input tensor (sudah fixed 1024x1024)
    auto* inputTensor = g_net->getSessionInput(g_session, "input");
    if (!inputTensor) { LOGE("No input tensor 'input'"); return nullptr; }

    auto* hostIn = MNN::Tensor::create<float>(
        {1, 3, MODEL_SIZE, MODEL_SIZE},
        inputFloat.data(),
        MNN::Tensor::CAFFE
    );
    inputTensor->copyFromHostTensor(hostIn);
    delete hostIn;

    // Run inference
    g_net->runSession(g_session);

    // Ambil output tensor
    auto* outTensor = g_net->getSessionOutput(g_session, "output");
    if (!outTensor) { LOGE("No output tensor 'output'"); return nullptr; }

    auto shape = outTensor->shape();
    if (shape.size() < 4) { LOGE("Bad output shape"); return nullptr; }

    int outH = shape[2]; // 2048 atau 4096
    int outW = shape[3];
    int outN = outH * outW;

    LOGI("Output: %dx%d", outW, outH);

    // Copy output ke host
    std::vector<float> outFloat(3 * outN);
    auto* hostOut = MNN::Tensor::create<float>(
        {1, 3, outH, outW},
        outFloat.data(),
        MNN::Tensor::CAFFE
    );
    outTensor->copyToHostTensor(hostOut);
    delete hostOut;

    // float NCHW → Bitmap
    return ncHWtoBitmap(env, outFloat.data(), outW, outH);
}

// ─── JNI: release ─────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_release(JNIEnv*, jobject) {
    if (g_net && g_session) { g_net->releaseSession(g_session); g_session = nullptr; }
    if (g_net) { MNN::Interpreter::destroy(g_net); g_net = nullptr; }
    g_modelPath.clear();
    LOGI("Released");
}
