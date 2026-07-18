package com.d4nzxml.kythera.service;

public class EnhanceNative {

    // Load library asli bawaan mesin AI-nya
    static {
        System.loadLibrary("enhance"); 
    }

    // 1. Init: Menerima model .mnn dalam bentuk byte array, mengembalikan ID mesin (long)
    public native long nativeInit(byte[] modelData, int size);

    // 2. Enhance: Menerima gambar byte array RGBA, mengembalikan hasil upscale byte array RGBA
    public native byte[] nativeEnhance(long objectId, byte[] inputRGBA, int colorFormat, int width, int height);
    
    // 3. Release: Bersih-bersih RAM kalau udah kelar
    public native void nativeRelease(long objectId); 
}
