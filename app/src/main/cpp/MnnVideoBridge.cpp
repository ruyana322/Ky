#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>
#include <cstring>

#include "MNN/Interpreter.hpp"
#include "MNN/MNNDefine.h"
#include "MNN/Tensor.hpp"

#define TAG "KytheraEnhance"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static const int TILE = 1024; // Model fixed input size

static MNN::Interpreter* g_net     = nullptr;
static MNN::Session*     g_session = nullptr;
static std::string       g_modelPath;
static int               g_scale   = 2;

// ─── Helper: lock bitmap pixels ───────────────────────────────────────────────
struct BitmapData {
    JNIEnv* env;
    jobject bitmap;
    void*   pixels = nullptr;
    int     w = 0, h = 0;
    bool    ok = false;

    BitmapData(JNIEnv* e, jobject b) : env(e), bitmap(b) {
        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(e, b, &info) < 0) return;
        if (AndroidBitmap_lockPixels(e, b, &pixels) < 0) return;
        w = info.width; h = info.height; ok = true;
    }
    ~BitmapData() { if (ok) AndroidBitmap_unlockPixels(env, bitmap); }
};

// ─── Create Bitmap ────────────────────────────────────────────────────────────
static jobject createBitmap(JNIEnv* env, int w, int h) {
    jclass bc = env->FindClass("android/graphics/Bitmap");
    jclass cc = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID fid = env->GetStaticFieldID(cc, "ARGB_8888",
                     "Landroid/graphics/Bitmap$Config;");
    jobject cfg = env->GetStaticObjectField(cc, fid);
    jmethodID cr = env->GetStaticMethodID(bc, "createBitmap",
                     "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    return env->CallStaticObjectMethod(bc, cr, w, h, cfg);
}

// ─── Run MNN inference on exactly 1024x1024 RGBA tile ─────────────────────────
// Input:  RGBA uint8 [TILE*TILE*4]
// Output: RGBA uint8 [TILE*scale * TILE*scale * 4]
static bool runTile(const uint8_t* tileIn,
                    std::vector<uint8_t>& tileOut) {
    int N   = TILE * TILE;
    int out = TILE * g_scale;
    int ON  = out * out;

    // RGBA → float NCHW/NHWC [0, 1]
    std::vector<float> floatIn(3 * N);
    for (int i = 0; i < N; i++) {
        // We feed CAFFE (NCHW) to MNN, let MNN handle conversion internally
        floatIn[0*N+i] = tileIn[i*4+0] / 255.0f; // R
        floatIn[1*N+i] = tileIn[i*4+1] / 255.0f; // G
        floatIn[2*N+i] = tileIn[i*4+2] / 255.0f; // B
    }

    auto* inputTensor = g_net->getSessionInput(g_session, nullptr);
    if (!inputTensor) { LOGE("No input tensor"); return false; }

    auto* hostIn = MNN::Tensor::create<float>(
        {1, 3, TILE, TILE}, floatIn.data(), MNN::Tensor::CAFFE);
    inputTensor->copyFromHostTensor(hostIn);
    delete hostIn;

    g_net->runSession(g_session);

    auto* outTensor = g_net->getSessionOutput(g_session, nullptr);
    if (!outTensor) { LOGE("No output tensor"); return false; }

    auto shape = outTensor->shape();
    if (shape.size() < 4) { LOGE("Bad shape"); return false; }

    int realOH, realOW;
    auto dimType = outTensor->getDimensionType();
    if (dimType == MNN::Tensor::TENSORFLOW) {
        realOH = shape[1];
        realOW = shape[2];
    } else {
        realOH = shape[2];
        realOW = shape[3];
    }
    int realON = realOH * realOW;

    std::vector<float> floatOut(3 * realON);
    
    // Create host tensor with EXACT SAME shape array and dimension type to guarantee safe copy
    auto* hostOut = MNN::Tensor::create<float>(shape, floatOut.data(), dimType);
    outTensor->copyToHostTensor(hostOut);
    delete hostOut;

    // float → RGBA uint8
    tileOut.resize((size_t)(realOW * realOH * 4));
    if (dimType == MNN::Tensor::TENSORFLOW) {
        // NHWC layout (RGB RGB RGB ...)
        for (int i = 0; i < realON; i++) {
            tileOut[i*4+0] = (uint8_t)(std::max(0.f, std::min(1.f, floatOut[i*3+0])) * 255.f + 0.5f);
            tileOut[i*4+1] = (uint8_t)(std::max(0.f, std::min(1.f, floatOut[i*3+1])) * 255.f + 0.5f);
            tileOut[i*4+2] = (uint8_t)(std::max(0.f, std::min(1.f, floatOut[i*3+2])) * 255.f + 0.5f);
            tileOut[i*4+3] = 255;
        }
    } else {
        // CAFFE layout (RRR... GGG... BBB...)
        for (int i = 0; i < realON; i++) {
            tileOut[i*4+0] = (uint8_t)(std::max(0.f, std::min(1.f, floatOut[0*realON+i])) * 255.f + 0.5f);
            tileOut[i*4+1] = (uint8_t)(std::max(0.f, std::min(1.f, floatOut[1*realON+i])) * 255.f + 0.5f);
            tileOut[i*4+2] = (uint8_t)(std::max(0.f, std::min(1.f, floatOut[2*realON+i])) * 255.f + 0.5f);
            tileOut[i*4+3] = 255;
        }
    }
    return true;
}

// ─── JNI: loadModel ───────────────────────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_loadModel(
        JNIEnv* env, jobject, jstring modelPath, jint gpuMode) {

    const char* p = env->GetStringUTFChars(modelPath, nullptr);
    std::string path(p);
    env->ReleaseStringUTFChars(modelPath, p);

    if (g_net && g_session && g_modelPath == path) {
        LOGI("Already loaded scale=%dx", g_scale);
        return JNI_TRUE;
    }

    if (g_net && g_session) { g_net->releaseSession(g_session); g_session = nullptr; }
    if (g_net) { MNN::Interpreter::destroy(g_net); g_net = nullptr; }

    g_net = MNN::Interpreter::createFromFile(path.c_str());
    if (!g_net) { LOGE("Load failed"); return JNI_FALSE; }



    MNN::ScheduleConfig config;
    MNN::BackendConfig backendCfg;
    if (gpuMode == 0) {
        backendCfg.precision = MNN::BackendConfig::Precision_Low;
        backendCfg.power     = MNN::BackendConfig::Power_High;
        config.type          = MNN_FORWARD_OPENCL;
        config.backendConfig = &backendCfg;
        LOGI("GPU OpenCL");
    } else {
        config.type = MNN_FORWARD_CPU; config.numThread = 4;
        LOGI("CPU x4");
    }

    g_session = g_net->createSession(config);
    if (!g_session) {
        config.type = MNN_FORWARD_CPU; config.numThread = 4;
        config.backendConfig = nullptr;
        g_session = g_net->createSession(config);
    }
    if (!g_session) {
        MNN::Interpreter::destroy(g_net); g_net = nullptr;
        return JNI_FALSE;
    }

    // Set fixed input size
    auto* inputTensor = g_net->getSessionInput(g_session, nullptr);
    if (inputTensor) {
        g_net->resizeTensor(inputTensor, {1, 3, TILE, TILE});
        g_net->resizeSession(g_session);
    }
    // Dynamically calculate scale from the output tensor
    auto* outTensor = g_net->getSessionOutput(g_session, nullptr);
    if (outTensor) {
        auto shape = outTensor->shape();
        if (shape.size() >= 4) {
            int maxDim = std::max({shape[1], shape[2], shape[3]});
            g_scale = maxDim / TILE;
            if (g_scale < 1) g_scale = 1;
        }
    }

    g_modelPath = path;
    LOGI("Loaded OK scale=%dx", g_scale);
    return JNI_TRUE;
}

// ─── JNI: enhanceFrame ────────────────────────────────────────────────────────
// Padding approach: pad frame ke kelipatan TILE, proses tile per tile, crop balik
extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_enhanceFrame(
        JNIEnv* env, jobject, jobject inputBitmap) {

    if (!g_net || !g_session) { LOGE("Not loaded"); return nullptr; }

    BitmapData src(env, inputBitmap);
    if (!src.ok) return nullptr;

    int srcW = src.w, srcH = src.h;
    auto* srcPx = (uint8_t*)src.pixels;

    // Hitung jumlah tile yang dibutuhkan
    int tilesX = (srcW + TILE - 1) / TILE;
    int tilesY = (srcH + TILE - 1) / TILE;

    // Output size = input * scale
    int outW = srcW * g_scale;
    int outH = srcH * g_scale;

    LOGI("Frame %dx%d → tiles %dx%d → output %dx%d",
         srcW, srcH, tilesX, tilesY, outW, outH);

    // Alokasi output bitmap
    jobject outBitmap = createBitmap(env, outW, outH);
    void* outPxVoid = nullptr;
    if (AndroidBitmap_lockPixels(env, outBitmap, &outPxVoid) < 0) return nullptr;
    auto* outPx = (uint8_t*)outPxVoid;
    memset(outPx, 0, (size_t)(outW * outH * 4));

    bool success = true;

    // Buffer tile input (1024x1024 RGBA)
    std::vector<uint8_t> tileIn(TILE * TILE * 4, 0);
    std::vector<uint8_t> tileOut;

    for (int ty = 0; ty < tilesY && success; ty++) {
        for (int tx = 0; tx < tilesX && success; tx++) {

            // Fill tile dengan pixel dari frame asli (pad dengan 0 kalau out of bounds)
            memset(tileIn.data(), 0, tileIn.size());

            int srcStartX = tx * TILE;
            int srcStartY = ty * TILE;
            int copyW = std::min(TILE, srcW - srcStartX);
            int copyH = std::min(TILE, srcH - srcStartY);

            for (int row = 0; row < copyH; row++) {
                const uint8_t* srcRow = srcPx + ((srcStartY + row) * srcW + srcStartX) * 4;
                uint8_t* dstRow       = tileIn.data() + row * TILE * 4;
                memcpy(dstRow, srcRow, (size_t)(copyW * 4));
            }

            // Run MNN inference pada tile 1024x1024
            if (!runTile(tileIn.data(), tileOut)) {
                success = false; break;
            }

            // Tulis hasil tile ke output bitmap
            int outTileW    = TILE * g_scale;
            int outTileH    = TILE * g_scale;
            int outStartX   = tx * outTileW;
            int outStartY   = ty * outTileH;
            int validOutW   = std::min(outTileW, outW - outStartX);
            int validOutH   = std::min(outTileH, outH - outStartY);

            for (int row = 0; row < validOutH; row++) {
                const uint8_t* srcRow = tileOut.data() + row * outTileW * 4;
                uint8_t* dstRow       = outPx + ((outStartY + row) * outW + outStartX) * 4;
                memcpy(dstRow, srcRow, (size_t)(validOutW * 4));
            }
        }
    }

    AndroidBitmap_unlockPixels(env, outBitmap);

    if (!success) return nullptr;
    return outBitmap;
}

// ─── JNI: releaseNative ─────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_releaseNative(JNIEnv*, jobject) {
    if (g_net && g_session) { g_net->releaseSession(g_session); g_session = nullptr; }
    if (g_net) { MNN::Interpreter::destroy(g_net); g_net = nullptr; }
    g_modelPath.clear();
    LOGI("Released");
}
