#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <algorithm> // Wajib ditambahin buat ngitung batas potongan poto

// Panggil mesin NCNN Tencent
#include "ncnn/net.h"
#include "ncnn/gpu.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "Kythera_AI", __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "Kythera_AI", __VA_ARGS__)

static ncnn::Net* net = nullptr;
static ncnn::VulkanDevice* vkdev = nullptr;
static ncnn::VkAllocator* blob_vkallocator = nullptr;
static ncnn::VkAllocator* staging_vkallocator = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_RealSrEngine_initModel(JNIEnv* env, jobject thiz, jstring paramPath, jstring binPath) {
    const char* param_path = env->GetStringUTFChars(paramPath, 0);
    const char* bin_path = env->GetStringUTFChars(binPath, 0);

    // Nyalain mesin GPU Vulkan!
    ncnn::create_gpu_instance();
    if (ncnn::get_gpu_count() > 0) {
        vkdev = ncnn::get_gpu_device(0); 
        blob_vkallocator = new ncnn::VkBlobAllocator(vkdev);
        staging_vkallocator = new ncnn::VkStagingAllocator(vkdev);
        LOGD("Vulkan GPU berhasil aktif! Siap ngebut!");
    }

    if (net == nullptr) {
        net = new ncnn::Net();
    }
    
    // Paksa AI-nya lari pakai GPU
    net->opt.use_vulkan_compute = (vkdev != nullptr);
    net->opt.blob_vkallocator = blob_vkallocator;
    net->opt.workspace_vkallocator = blob_vkallocator;
    net->opt.staging_vkallocator = staging_vkallocator;

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
    if (net == nullptr) {
        LOGE("Mesin AI belum nyala!");
        return nullptr;
    }

    // 1. Ubah format Poto Android (Bitmap) jadi format Matriks NCNN
    ncnn::Mat in = ncnn::Mat::from_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_RGB);

    // ==========================================
    // SISTEM TILING (POTONG - JAHIT) ANTI CRASH
    // ==========================================
    int scale = 4; // AI kita nge-HD-in 4x lipat
    int tile_size = 200; // GPU cuma ngerjain area 200x200 pixel per sesi (sangat aman buat RAM)
    
    // Siapin kanvas kosong raksasa buat nampung hasil jahitan akhir
    ncnn::Mat out(in.w * scale, in.h * scale, 3);
    out.fill(0.f);

    ncnn::Extractor ex = net->create_extractor();
    ex.set_vulkan_compute(true);

    // Looping buat ngebelah dan ngejahit gambar
    for (int y = 0; y < in.h; y += tile_size) {
        for (int x = 0; x < in.w; x += tile_size) {
            
            // Hitung sisa ukuran poto kalau udah di pinggir
            int req_w = std::min(tile_size, in.w - x);
            int req_h = std::min(tile_size, in.h - y);

            // A. Gunting kotak kecil dari gambar asli
            ncnn::Mat in_tile(req_w, req_h, 3);
            for (int c = 0; c < 3; c++) {
                const float* in_ptr = in.channel(c);
                float* tile_ptr = in_tile.channel(c);
                for (int ty = 0; ty < req_h; ty++) {
                    for (int tx = 0; tx < req_w; tx++) {
                        tile_ptr[ty * req_w + tx] = in_ptr[(y + ty) * in.w + (x + tx)];
                    }
                }
            }

            // B. Masukin potongan ini ke mesin cuci AI
            ex.input("data", in_tile);
            ncnn::Mat out_tile;
            ex.extract("output", out_tile);

            // C. Jahit potongan yang udah HD ke kanvas raksasa
            for (int c = 0; c < 3; c++) {
                float* out_ptr = out.channel(c);
                const float* tile_ptr = out_tile.channel(c);
                
                for (int ty = 0; ty < out_tile.h; ty++) {
                    for (int tx = 0; tx < out_tile.w; tx++) {
                        int out_x = x * scale + tx;
                        int out_y = y * scale + ty;
                        out_ptr[out_y * out.w + out_x] = tile_ptr[ty * out_tile.w + tx];
                    }
                }
            }
        }
    }
    // ==========================================

    // Siapin kanvas kosong baru buat Poto Android (ukurannya udah membesar)
    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888FieldID = env->GetStaticFieldID(bitmapConfigClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888Obj = env->GetStaticObjectField(bitmapConfigClass, argb8888FieldID);

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethodID = env->GetStaticMethodID(bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject newBitmap = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodID, out.w, out.h, argb8888Obj);

    // Tuang hasil jahitan akhir ke kanvas Poto Android yang baru
    out.to_android_bitmap(env, newBitmap, ncnn::Mat::PIXEL_RGB);

    return newBitmap;
}
