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
static ncnn::VulkanDevice* vkdev = nullptr;
static ncnn::VkAllocator* blob_vkallocator = nullptr;
static ncnn::VkAllocator* staging_vkallocator = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_RealSrEngine_initModel(JNIEnv* env, jobject thiz, jstring paramPath, jstring binPath) {
    const char* param_path = env->GetStringUTFChars(paramPath, 0);
    const char* bin_path = env->GetStringUTFChars(binPath, 0);

    ncnn::create_gpu_instance();
    if (ncnn::get_gpu_count() > 0) {
        vkdev = ncnn::get_gpu_device(0); 
        blob_vkallocator = new ncnn::VkBlobAllocator(vkdev);
        staging_vkallocator = new ncnn::VkStagingAllocator(vkdev);
    }

    if (net == nullptr) {
        net = new ncnn::Net();
    }
    
    net->opt.use_vulkan_compute = (vkdev != nullptr);
    net->opt.blob_vkallocator = blob_vkallocator;
    net->opt.workspace_vkallocator = blob_vkallocator;
    net->opt.staging_vkallocator = staging_vkallocator;

    int ret_param = net->load_param(param_path);
    int ret_bin = net->load_model(bin_path);

    env->ReleaseStringUTFChars(paramPath, param_path);
    env->ReleaseStringUTFChars(binPath, bin_path);

    if (ret_param != 0 || ret_bin != 0) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_RealSrEngine_processBitmap(JNIEnv* env, jobject thiz, jobject bitmap) {
    if (net == nullptr) return nullptr;

    ncnn::Mat in = ncnn::Mat::from_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_RGB);

    int scale = 4; 
    int tile_size = 200; 
    
    ncnn::Mat out(in.w * scale, in.h * scale, 3);
    out.fill(0.f);

    ncnn::Extractor ex = net->create_extractor();
    ex.set_vulkan_compute(true);

    for (int y = 0; y < in.h; y += tile_size) {
        for (int x = 0; x < in.w; x += tile_size) {
            
            int req_w = std::min(tile_size, in.w - x);
            int req_h = std::min(tile_size, in.h - y);

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

            ex.input("data", in_tile);
            ncnn::Mat out_tile;
            ex.extract("output", out_tile);

            // OBAT ANTI-CRASH JAHITAN (Boundary Check)
            int valid_w = req_w * scale;
            int valid_h = req_h * scale;

            for (int c = 0; c < 3; c++) {
                float* out_ptr = out.channel(c);
                const float* tile_ptr = out_tile.channel(c);
                
                for (int ty = 0; ty < valid_h; ty++) {
                    for (int tx = 0; tx < valid_w; tx++) {
                        int out_x = x * scale + tx;
                        int out_y = y * scale + ty;
                        
                        // Kunci gembok biar nggak nulis memori di luar batas!
                        if (out_x < out.w && out_y < out.h && tx < out_tile.w && ty < out_tile.h) {
                            out_ptr[out_y * out.w + out_x] = tile_ptr[ty * out_tile.w + tx];
                        }
                    }
                }
            }
        }
    }

    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888FieldID = env->GetStaticFieldID(bitmapConfigClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888Obj = env->GetStaticObjectField(bitmapConfigClass, argb8888FieldID);

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethodID = env->GetStaticMethodID(bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject newBitmap = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodID, out.w, out.h, argb8888Obj);

    out.to_android_bitmap(env, newBitmap, ncnn::Mat::PIXEL_RGB);

    return newBitmap;
}
