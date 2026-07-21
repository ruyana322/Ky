package com.d4nzxml.kythera.service // <--- Ini udah disesuaikan sama repo lu!

import android.content.res.AssetManager
import android.util.Log

class NcnnVideoBridge {
    companion object {
        private const val TAG = "NcnnBridgeKotlin"

        init {
            try {
                System.loadLibrary("ncnn_bridge") 
                Log.d(TAG, "Library NCNN sukses di-load!")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Waduh, gagal nge-load library C++: ${e.message}")
            }
        }
    }

    external fun initEngine(assetManager: AssetManager): Boolean
    external fun destroyEngine()
}
