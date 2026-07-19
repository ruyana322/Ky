#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <string>

#include "opencv2/core.hpp"
#include "opencv2/videoio.hpp"
#include "opencv2/imgproc.hpp"

#define TAG "KytheraOpenCV"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static cv::VideoCapture g_cap;
static cv::VideoWriter  g_writer;
static int    g_totalFrames = 0;
static double g_fps         = 30.0;
static int    g_width       = 0;
static int    g_height      = 0;
static int    g_rotation    = 0;  // rotation dari metadata video

// ─── Helper: Mat → Bitmap (handle rotation) ───────────────────────────────────
static jobject matToBitmap(JNIEnv* env, cv::Mat& mat) {
    // Auto rotate frame sesuai metadata video
    cv::Mat rotated;
    switch (g_rotation) {
        case 90:
            cv::rotate(mat, rotated, cv::ROTATE_90_CLOCKWISE);
            break;
        case 180:
            cv::rotate(mat, rotated, cv::ROTATE_180);
            break;
        case 270:
            cv::rotate(mat, rotated, cv::ROTATE_90_COUNTERCLOCKWISE);
            break;
        default:
            rotated = mat;
            break;
    }

    jclass bitmapClass  = env->FindClass("android/graphics/Bitmap");
    jclass configClass  = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888Id = env->GetStaticFieldID(configClass, "ARGB_8888",
                            "Landroid/graphics/Bitmap$Config;");
    jobject argb8888    = env->GetStaticObjectField(configClass, argb8888Id);
    jmethodID create    = env->GetStaticMethodID(bitmapClass, "createBitmap",
                            "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    int w = rotated.cols, h = rotated.rows;
    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, create, w, h, argb8888);

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return nullptr;

    cv::Mat rgba;
    cv::cvtColor(rotated, rgba, cv::COLOR_BGR2RGBA);
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
    cv::Mat rgba(info.height, info.width, CV_8UC4, pixels);
    cv::Mat bgr;
    cv::cvtColor(rgba, bgr, cv::COLOR_RGBA2BGR);
    AndroidBitmap_unlockPixels(env, bitmap);
    return bgr.clone();
}

// ─── JNI: openVideo ───────────────────────────────────────────────────────────
extern "C" JNIEXPORT jintArray JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_openVideo(
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

    // Baca rotation metadata
    // OpenCV CAP_PROP_ORIENTATION_META = 181 (tersedia di OpenCV 4.5+)
    double rotMeta = g_cap.get(181); // CAP_PROP_ORIENTATION_META
    g_rotation = (int)rotMeta;

    // Validasi rotation value
    if (g_rotation != 0 && g_rotation != 90 && g_rotation != 180 && g_rotation != 270) {
        g_rotation = 0;
    }

    if (g_fps <= 0 || g_fps > 120) g_fps = 30.0;

    LOGI("Video: %dx%d, %.2f fps, %d frames, rotation=%d",
         g_width, g_height, g_fps, g_totalFrames, g_rotation);

    jintArray result = env->NewIntArray(5);
    jint vals[5] = {
        g_totalFrames,
        (jint)(g_fps * 1000),
        // Width/height sudah dihitung dengan rotation
        (g_rotation == 90 || g_rotation == 270) ? g_height : g_width,
        (g_rotation == 90 || g_rotation == 270) ? g_width  : g_height,
        g_rotation
    };
    env->SetIntArrayRegion(result, 0, 5, vals);
    return result;
}

// ─── JNI: readFrame ───────────────────────────────────────────────────────────
extern "C" JNIEXPORT jobject JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_readFrame(
        JNIEnv* env, jobject) {

    if (!g_cap.isOpened()) return nullptr;
    cv::Mat frame;
    if (!g_cap.read(frame) || frame.empty()) return nullptr;
    // matToBitmap auto-rotate sesuai g_rotation
    return matToBitmap(env, frame);
}

// ─── JNI: openWriter ──────────────────────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_openWriter(
        JNIEnv* env, jobject,
        jstring outputPath, jint width, jint height) {

    const char* path = env->GetStringUTFChars(outputPath, nullptr);
    std::string pathStr(path);
    env->ReleaseStringUTFChars(outputPath, path);

    if (g_writer.isOpened()) g_writer.release();

    // H264 encoder
    int fourcc = cv::VideoWriter::fourcc('H','2','6','4');
    g_writer.open(pathStr, fourcc, g_fps, cv::Size(width, height));

    if (!g_writer.isOpened()) {
        // Fallback AVC1
        fourcc = cv::VideoWriter::fourcc('a','v','c','1');
        g_writer.open(pathStr, fourcc, g_fps, cv::Size(width, height));
    }

    LOGI("Writer: %dx%d @ %.2f fps → %s (opened=%d)",
         width, height, g_fps, pathStr.c_str(), g_writer.isOpened());
    return (jboolean)g_writer.isOpened();
}

// ─── JNI: writeFrame ──────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_writeFrame(
        JNIEnv* env, jobject, jobject bitmap) {

    if (!g_writer.isOpened()) return;
    cv::Mat mat = bitmapToMat(env, bitmap);
    if (!mat.empty()) g_writer.write(mat);
}

// ─── JNI: closeAll ────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_closeAll(JNIEnv*, jobject) {
    if (g_cap.isOpened())    g_cap.release();
    if (g_writer.isOpened()) g_writer.release();
    g_totalFrames = 0; g_fps = 30.0;
    g_width = g_height = g_rotation = 0;
    LOGI("OpenCV released");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_getTotalFrames(JNIEnv*, jobject) {
    return (jint)g_totalFrames;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_d4nzxml_kythera_service_OpenCvBridge_getFps(JNIEnv*, jobject) {
    return (jdouble)g_fps;
}
