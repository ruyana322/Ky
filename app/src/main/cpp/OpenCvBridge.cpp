#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <string>
#include <vector>

// OpenCV headers
#include "opencv2/core.hpp"
#include "opencv2/videoio.hpp"
#include "opencv2/imgproc.hpp"

#define TAG "KytheraOpenCV"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ─── Global state ─────────────────────────────────────────────────────────────
static cv::VideoCapture g_cap;
static cv::VideoWriter  g_writer;
static int g_totalFrames = 0;
static double g_fps      = 30.0;
static int g_width       = 0;
static int g_height      = 0;
static int g_out_width   = 0;  // Penyelamat 1: Simpan lebar target
static int g_out_height  = 0;  // Penyelamat 2: Simpan tinggi target

// ─── Helper: Mat → Bitmap ─────────────────────────────────────────────────────
static jobject matToBitmap(JNIEnv* env, const cv::Mat& mat) {
    jclass bitmapClass  = env->FindClass("android/graphics/Bitmap");
    jclass configClass  = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888Id = env->GetStaticFieldID(configClass, "ARGB_8888",
                            "Landroid/graphics/Bitmap$Config;");
    jobject argb8888    = env->GetStaticObjectField(configClass, argb8888Id);
    jmethodID create    = env->GetStaticMethodID(bitmapClass, "createBitmap",
                            "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    int w = mat.cols, h = mat.rows;
    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, create, w, h, argb8888);

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return nullptr;

    // BGR (OpenCV) → RGBA (Android)
    cv::Mat rgba;
    cv::cvtColor(mat, rgba, cv::COLOR_BGR2RGBA);

    memcpy(pixels, rgba.data, (size_t)(w * h * 4));
    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}

// ─── Helper: Bitmap → Mat ─────────────────────────────────────────────────────
static cv::Mat bitmapToMat(JNIEnv* env, jobject bitmap) {
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);

    void* pixels = nullptr;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    // RGBA → BGR
    cv::Mat rgba(info.height, info.width, CV_8UC4, pixels);
    cv::Mat bgr;
    cv::cvtColor(rgba, bgr, cv::COLOR_RGBA2BGR);

    AndroidBitmap_unlockPixels(env, bitmap);
    
    // Gak usah pakai .clone() lagi biar gak boros RAM dan menghindari OOM
    return bgr;
}

// ─── JNI: openVideo ───────────────────────────────────────────────────────────
extern "C" JNIEXPORT jintArray JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_openVideoNative(
        JNIEnv* env, jobject, jstring videoPath) {

    const char* path = env->GetStringUTFChars(videoPath, nullptr);
    std::string pathStr(path);
    env->ReleaseStringUTFChars(videoPath, path);

    if (g_cap.isOpened()) g_cap.release();

    g_cap.open(pathStr);
    if (!g_cap.isOpened()) {
        LOGE("Failed to open: %s", pathStr.c_str());
        return nullptr;
    }

    g_totalFrames = (int)g_cap.get(cv::CAP_PROP_FRAME_COUNT);
    g_fps         = g_cap.get(cv::CAP_PROP_FPS);
    g_width       = (int)g_cap.get(cv::CAP_PROP_FRAME_WIDTH);
    g_height      = (int)g_cap.get(cv::CAP_PROP_FRAME_HEIGHT);

    if (g_fps <= 0 || g_fps > 120) g_fps = 30.0;

    LOGI("Video opened: %dx%d, %.2f fps, %d frames",
         g_width, g_height, g_fps, g_totalFrames);

    jintArray result = env->NewIntArray(4);
    jint vals[4] = {
        g_totalFrames,
        (jint)(g_fps * 1000), 
        g_width,
        g_height
    };
    env->SetIntArrayRegion(result, 0, 4, vals);
    return result;
}

// ─── JNI: readFrame ───────────────────────────────────────────────────────────
extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_readFrame(
        JNIEnv* env, jobject) {

    if (!g_cap.isOpened()) return nullptr;

    cv::Mat frame;
    if (!g_cap.read(frame) || frame.empty()) return nullptr;

    return matToBitmap(env, frame);
}

// ─── JNI: openWriter ──────────────────────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_openWriterNative(
        JNIEnv* env, jobject,
        jstring outputPath,
        jint width, jint height) {

    const char* path = env->GetStringUTFChars(outputPath, nullptr);
    std::string pathStr(path);
    env->ReleaseStringUTFChars(outputPath, path);

    if (g_writer.isOpened()) g_writer.release();

    // 🔥 PROTEKSI 1: Paksa resolusi jadi angka Genap biar MediaCodec gak ngamuk
    if (width % 2 != 0) width -= 1;
    if (height % 2 != 0) height -= 1;

    g_out_width = width;
    g_out_height = height;

    int fourcc = cv::VideoWriter::fourcc('H','2','6','4');
    g_writer.open(pathStr, fourcc, g_fps, cv::Size(g_out_width, g_out_height));

    if (!g_writer.isOpened()) {
        fourcc = cv::VideoWriter::fourcc('M','J','P','G');
        std::string mjpgPath = pathStr.substr(0, pathStr.rfind('.')) + "_raw.avi";
        g_writer.open(mjpgPath, fourcc, g_fps, cv::Size(g_out_width, g_out_height));
    }

    LOGI("Writer opened: %dx%d @ %.2f fps → %s",
         g_out_width, g_out_height, g_fps, pathStr.c_str());
    return (jboolean)g_writer.isOpened();
}

// ─── JNI: writeFrame ──────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_writeFrame(
        JNIEnv* env, jobject, jobject bitmap) {

    if (!g_writer.isOpened()) return;
    cv::Mat mat = bitmapToMat(env, bitmap);
    
    if (!mat.empty()) {
        // 🔥 PROTEKSI 2: Kunci rapat dimensi memori! 
        // Kalau AI ngasih gambar dengan ukuran gak sesuai, paksa resize biar gak Segfault
        if (mat.cols != g_out_width || mat.rows != g_out_height) {
            cv::resize(mat, mat, cv::Size(g_out_width, g_out_height));
        }
        g_writer.write(mat);
    }
}

// ─── JNI: closeAll ────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_closeAll(
        JNIEnv*, jobject) {

    if (g_cap.isOpened())    g_cap.release();
    if (g_writer.isOpened()) g_writer.release();
    g_totalFrames = 0; g_fps = 30.0; g_width = g_height = 0;
    g_out_width = g_out_height = 0;
    LOGI("OpenCV resources released");
}

// ─── JNI: getTotalFrames ──────────────────────────────────────────────────────
extern "C" JNIEXPORT jint JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_getTotalFrames(
        JNIEnv*, jobject) { return (jint)g_totalFrames; }

// ─── JNI: getFps ─────────────────────────────────────────────────────────────
extern "C" JNIEXPORT jdouble JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_getFps(
        JNIEnv*, jobject) { return (jdouble)g_fps; }
