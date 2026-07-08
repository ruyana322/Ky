#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>

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
// ... (Biarkan fungsi initModel yang tadi tetep di atas) ...

extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_RealSrEngine_processBitmap(JNIEnv* env, jobject thiz, jobject bitmap) {
    if (net == nullptr) {
        LOGE("Mesin AI belum nyala!");
        return nullptr;
    }

    // 1. Ubah format Poto Android (Bitmap) jadi format Matriks NCNN
    ncnn::Mat in = ncnn::Mat::from_android_bitmap(env, bitmap, ncnn::Mat::PIXEL_RGB);

    // 2. Masukin Poto ke dalam mesin cuci AI (Vulkan GPU)
    ncnn::Extractor ex = net->create_extractor();
    ex.set_vulkan_compute(true);
    ex.input("data", in);
    
    ncnn::Mat out;
    ex.extract("output", out);

    // 3. Siapin kanvas kosong baru buat Poto hasil HD (ukurannya udah membesar)
    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888FieldID = env->GetStaticFieldID(bitmapConfigClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888Obj = env->GetStaticObjectField(bitmapConfigClass, argb8888FieldID);

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethodID = env->GetStaticMethodID(bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject newBitmap = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodID, out.w, out.h, argb8888Obj);

    // 4. Tuang hasil dari AI ke kanvas Poto Android yang baru
    out.to_android_bitmap(env, newBitmap, ncnn::Mat::PIXEL_RGB);

    return newBitmap;
}
