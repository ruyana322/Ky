package com.d4nzxml.kythera.engine

import android.graphics.Bitmap

/** Penyedia gambar untuk objek IMAGE. [src] adalah path relatif di dalam folder project. */
fun interface ImageSource {
    fun bitmap(src: String): Bitmap?
}
