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
// 🔥 SAKLAR ON! AI SEKARANG HIDUP!
static bool DEBUG_BYPASS_MNN = false;

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

    if (DEBUG_BYPASS_MNN) {
        LOGI("DEBUG: bypass mode ON");
        return rgbaToBitmap(env, inputRGBA.data(), inW, inH);
    }

    if (!g_net || !g_session) return nullptr;

    auto* inputTensor = g_net->getSessionInput(g_session, nullptr);
    bool isNhwc = (inputTensor->getDimensionType() == MNN::Tensor::TENSORFLOW);

    // 1. Sesuaikan ukuran input
    if (isNhwc) {
        g_net->resizeTensor(inputTensor, {1, inH, inW, 3});
    } else {
        g_net->resizeTensor(inputTensor, {1, 3, inH, inW});
    }
    g_net->resizeSession(g_session);

    // 🔥 FIX 1: BIKIN WADAH INPUT DENGAN TIPE DATA SPESIFIK!
    halide_type_t inType = inputTensor->getType();
    auto* hostIn = MNN::Tensor::create(inputTensor->shape(), inType, nullptr, inputTensor->getDimensionType());

    // Masukin nilai RGBA asli (0-255) ke memori MNN
    if (inType.code == halide_type_float) {
        float* ptr = hostIn->host<float>();
        for (int i = 0; i < inW * inH; i++) {
            if (isNhwc) {
                ptr[i*3+0] = (float)inputRGBA[i*4+0] / 255.0f; // R
                ptr[i*3+1] = (float)inputRGBA[i*4+1] / 255.0f; // G
                ptr[i*3+2] = (float)inputRGBA[i*4+2] / 255.0f; // B
            } else {
                ptr[0*inW*inH + i] = (float)inputRGBA[i*4+0] / 255.0f; // R
                ptr[1*inW*inH + i] = (float)inputRGBA[i*4+1] / 255.0f; // G
                ptr[2*inW*inH + i] = (float)inputRGBA[i*4+2] / 255.0f; // B
            }
        }
    } else {
        uint8_t* ptr = hostIn->host<uint8_t>();
        for (int i = 0; i < inW * inH; i++) {
            if (isNhwc) {
                ptr[i*3+0] = inputRGBA[i*4+0]; // R
                ptr[i*3+1] = inputRGBA[i*4+1]; // G
                ptr[i*3+2] = inputRGBA[i*4+2]; // B
            } else {
                ptr[0*inW*inH + i] = inputRGBA[i*4+0]; // R
                ptr[1*inW*inH + i] = inputRGBA[i*4+1]; // G
                ptr[2*inW*inH + i] = inputRGBA[i*4+2]; // B
            }
        }
    }

    inputTensor->copyFromHostTensor(hostIn);
    delete hostIn;

    // RUN AI ENGINE
    g_net->runSession(g_session);

    // ─── AMBIL OUTPUT ───
    auto* outTensor = g_net->getSessionOutput(g_session, nullptr);
    if (!outTensor) return nullptr;

    // 🔥 FIX 2: BIKIN WADAH OUTPUT MANUAL YANG SANGAT SPESIFIK!
    halide_type_t outType = outTensor->getType();
    auto* hostOut = MNN::Tensor::create(outTensor->shape(), outType, nullptr, outTensor->getDimensionType());
    
    // Copy data (Ini titik keramatnya, harusnya udah gak Force Close!)
    outTensor->copyToHostTensor(hostOut); 

    auto shape = outTensor->shape();
    bool isOutNhwc = (outTensor->getDimensionType() == MNN::Tensor::TENSORFLOW);
    int outC = isOutNhwc ? shape[3] : shape[1];
    int outH = isOutNhwc ? shape[1] : shape[2];
    int outW = isOutNhwc ? shape[2] : shape[3];

    // Konversi hasil AI jadi gambar Android
    std::vector<uint8_t> finalRGBA(outW * outH * 4);
    
    if (outType.code == halide_type_float) {
        float* ptr = hostOut->host<float>();
        for (int i = 0; i < outW * outH; i++) {
            if (isOutNhwc) {
                finalRGBA[i*4+0] = (uint8_t)std::max(0.0f, std::min(255.0f, ptr[i*outC+0] * 255.0f));
                finalRGBA[i*4+1] = (uint8_t)std::max(0.0f, std::min(255.0f, ptr[i*outC+1] * 255.0f));
                finalRGBA[i*4+2] = (uint8_t)std::max(0.0f, std::min(255.0f, ptr[i*outC+2] * 255.0f));
            } else {
                finalRGBA[i*4+0] = (uint8_t)std::max(0.0f, std::min(255.0f, ptr[0*outW*outH+i] * 255.0f));
                finalRGBA[i*4+1] = (uint8_t)std::max(0.0f, std::min(255.0f, ptr[1*outW*outH+i] * 255.0f));
                finalRGBA[i*4+2] = (uint8_t)std::max(0.0f, std::min(255.0f, ptr[2*outW*outH+i] * 255.0f));
            }
            finalRGBA[i*4+3] = 255;
        }
    } else {
        uint8_t* ptr = hostOut->host<uint8_t>();
        for (int i = 0; i < outW * outH; i++) {
            if (isOutNhwc) {
                finalRGBA[i*4+0] = ptr[i*outC+0];
                finalRGBA[i*4+1] = ptr[i*outC+1];
                finalRGBA[i*4+2] = ptr[i*outC+2];
            } else {
                finalRGBA[i*4+0] = ptr[0*outW*outH+i];
                finalRGBA[i*4+1] = ptr[1*outW*outH+i];
                finalRGBA[i*4+2] = ptr[2*outW*outH+i];
            }
            finalRGBA[i*4+3] = 255;
        }
    }

    delete hostOut; 

    return rgbaToBitmap(env, finalRGBA.data(), outW, outH);
}

// ─── JNI: release ─────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_service_MnnVideoBridge_release(JNIEnv*, jobject) {
    if (g_net && g_session) { g_net->releaseSession(g_session); g_session = nullptr; }
    if (g_net) { MNN::Interpreter::destroy(g_net); g_net = nullptr; }
    g_modelPath.clear();
}
