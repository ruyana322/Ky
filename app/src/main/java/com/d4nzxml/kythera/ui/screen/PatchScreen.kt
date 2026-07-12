package com.d4nzxml.kythera.ui.screen

import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.d4nzxml.kythera.ui.components.*
import com.d4nzxml.kythera.ui.theme.KColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

// ─────────────────────────────────────────────────────────────
//  ENUMS
// ─────────────────────────────────────────────────────────────

enum class PatchLogic(val label: String, val desc: String) {
    PATCH_ONLY(
        "Kythera Patch Only",
        "Merapikan struktur MP4 & inject Shark Sample Table + MTLib stamp (Tanpa Re-encode)."
    ),
    ENCODE_PATCH(
        "Encode + Patch",
        "Render ulang video untuk optimasi penuh, lalu inject Shark Sample Table."
    ),
    KYTHERA_60FPS(
        "Kythera 60fps",
        "Menyematkan Z-Payload + MTLib + Encoder String stamp eksklusif Kythera."
    )
}

enum class EncodeQuality(val label: String, val desc: String, val cmdParams: String) {
    CPU_CRF20(
        "Jernih & Stabil (CRF 20)",
        "CPU libx264. Ukuran file aman, ketajaman dipertahankan.",
        "-c:v libx264 -preset fast -crf 20 -bf 0"
    ),
    CPU_HIGH(
        "Sultan High Bitrate (25M)",
        "CPU libx264. Kualitas maksimal, ukuran file besar.",
        "-c:v libx264 -preset fast -b:v 25M -maxrate 25M -bufsize 50M -bf 0"
    ),
    GPU_FAST(
        "Super Cepat (GPU)",
        "Akselerasi GPU Hardware (15M). Cocok untuk proses kilat.",
        "-c:v h264_mediacodec -b:v 15M -bf 0"
    )
}

// ═════════════════════════════════════════════════════════════
//  MP4 PATCHER ENGINE — port dari patcher.js
// ═════════════════════════════════════════════════════════════

private object Mp4Engine {

    // ── Shark constants ───────────────────────────────────────
    private const val VIDEO_TIMESCALE       = 90000
    private const val VIDEO_DURATION        = 2269500
    private const val VIDEO_EDIT_MEDIA_TIME = 3000
    private const val VIDEO_SAMPLE_DELTA    = 1500
    private const val FAKE_SAMPLE_SIZE      = 8
    private val FAKE_SAMPLE_BYTES = byteArrayOf(0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00)

    // ── Low-level helpers ─────────────────────────────────────

    private fun r32(b: ByteArray, i: Int) =
        ((b[i].toInt() and 0xFF) shl 24) or ((b[i+1].toInt() and 0xFF) shl 16) or
        ((b[i+2].toInt() and 0xFF) shl 8) or (b[i+3].toInt() and 0xFF)

    private fun w32(b: ByteArray, i: Int, v: Int) {
        b[i]   = ((v shr 24) and 0xFF).toByte()
        b[i+1] = ((v shr 16) and 0xFF).toByte()
        b[i+2] = ((v shr  8) and 0xFF).toByte()
        b[i+3] = (v and 0xFF).toByte()
    }

    private fun cc(b: ByteArray, i: Int) =
        if (i + 4 > b.size) "" else String(b, i, 4, Charsets.ISO_8859_1)

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size)
        w32(out, 0, 8 + payload.size)
        type.toByteArray(Charsets.ISO_8859_1).copyInto(out, 4)
        payload.copyInto(out, 8)
        return out
    }

    private fun cat(vararg parts: ByteArray): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var p = 0; for (a in parts) { a.copyInto(out, p); p += a.size }
        return out
    }

    // ── Box model ─────────────────────────────────────────────

    data class B(val off: Int, val size: Int, val type: String) {
        val end          get() = off + size
        val contentStart get() = off + 8
    }

    private fun topLevel(buf: ByteArray, type: String): B? {
        var p = 0
        while (p + 8 <= buf.size) {
            val sz = r32(buf, p); val t = cc(buf, p + 4)
            if (sz < 8) break
            if (t == type) return B(p, sz, t)
            p += sz
        }
        return null
    }

    private fun child(buf: ByteArray, parent: B, type: String): B? {
        var p = parent.contentStart
        while (p + 8 <= parent.end) {
            val sz = r32(buf, p); val t = cc(buf, p + 4)
            if (sz < 8) break
            if (t == type) return B(p, sz, t)
            p += sz
        }
        return null
    }

    private fun descend(buf: ByteArray, start: B, path: List<String>): B? {
        var cur = start
        for (s in path) { cur = child(buf, cur, s) ?: return null }
        return cur
    }

    private fun hdlrType(buf: ByteArray, trak: B): String {
        val hdlr = descend(buf, trak, listOf("mdia", "hdlr")) ?: return ""
        val off = hdlr.contentStart + 8
        return if (off + 4 <= buf.size) cc(buf, off) else ""
    }

    // ─────────────────────────────────────────────────────────
    //  PATCH 1 — Z-PAYLOAD
    //  Isi 128 byte setelah offset mdat+10 dengan 0x5A
    // ─────────────────────────────────────────────────────────

    private fun patchZPayload(buf: ByteArray) {
        val mdat = topLevel(buf, "mdat")
            ?: throw Exception("Struktur video tidak valid. Pastikan file MP4 tidak corrupt.")
        val zt = mdat.off + 10
        for (i in 0 until 128) { if (zt + i < buf.size) buf[zt + i] = 0x5A }
    }

    // ─────────────────────────────────────────────────────────
    //  PATCH 2 — ENCODER STRING
    //  Rewrite "Lavf..." → "Lavf59.16.100"
    // ─────────────────────────────────────────────────────────

    private fun patchEncoderStr(buf: ByteArray) {
        val lavf   = "Lavf".toByteArray(Charsets.ISO_8859_1)
        val target = "Lavf59.16.100".toByteArray(Charsets.ISO_8859_1)
        var i = 0
        while (i <= buf.size - 16) {
            if (buf[i] == lavf[0] && buf[i+1] == lavf[1] &&
                buf[i+2] == lavf[2] && buf[i+3] == lavf[3]) {
                val nb = buf[i+4].toInt() and 0xFF
                if (nb in 0x30..0x39) {
                    var end = i + 4
                    while (end < buf.size) {
                        val b = buf[end].toInt() and 0xFF
                        if (b >= 0x20 && b < 0x7F) end++ else break
                    }
                    val ol = end - i
                    for (j in 0 until ol) buf[i+j] = if (j < target.size) target[j] else 0x00
                    return
                }
            }
            i++
        }
    }

    // ─────────────────────────────────────────────────────────
    //  PATCH 3 — MTLib freeform atom
    //  Inject ---- box (mean/name/data) ke moov/udta
    // ─────────────────────────────────────────────────────────

    private fun injectMTLib(buf: ByteArray): ByteArray {
        fun meanBox(): ByteArray {
            val d = "com.apple.quicktime".toByteArray(Charsets.ISO_8859_1)
            return box("mean", ByteArray(4) + d)
        }
        fun nameBox(): ByteArray {
            val k = "MTLib".toByteArray(Charsets.ISO_8859_1)
            return box("name", ByteArray(4) + k)
        }
        fun dataBox(): ByteArray {
            val v = "PyPVGCodec".toByteArray(Charsets.ISO_8859_1)
            val p = ByteArray(4 + v.size)
            w32(p, 0, 1); v.copyInto(p, 4)
            return box("data", p)
        }
        val freeform = box("----", cat(meanBox(), nameBox(), dataBox()))
        val moov = topLevel(buf, "moov") ?: return buf
        val udta = child(buf, moov, "udta")

        return if (udta != null) {
            val ins = udta.end
            val out = ByteArray(buf.size + freeform.size)
            buf.copyInto(out, 0, 0, ins)
            freeform.copyInto(out, ins)
            buf.copyInto(out, ins + freeform.size, ins)
            w32(out, moov.off, moov.size + freeform.size)
            w32(out, udta.off, udta.size + freeform.size)
            out
        } else {
            val udtaBox = box("udta", freeform)
            val ins = moov.end
            val out = ByteArray(buf.size + udtaBox.size)
            buf.copyInto(out, 0, 0, ins)
            udtaBox.copyInto(out, ins)
            buf.copyInto(out, ins + udtaBox.size, ins)
            w32(out, moov.off, moov.size + udtaBox.size)
            out
        }
    }

    // ─────────────────────────────────────────────────────────
    //  applyMetadataStamp — Z-Payload + MTLib + EncoderStr
    //  Setara applyMetadataStamp() di JS → dipakai KYTHERA_60FPS
    // ─────────────────────────────────────────────────────────

    fun applyMetadataStamp(input: ByteArray): ByteArray {
        val buf1 = input.copyOf()
        patchZPayload(buf1)
        val buf2 = injectMTLib(buf1)
        patchEncoderStr(buf2)
        return buf2
    }

    // ─────────────────────────────────────────────────────────
    //  SHARK SAMPLE TABLE
    //  Setara patchSharkSampleTableMethod() di JS
    //  → dipakai PATCH_ONLY & ENCODE_PATCH
    // ─────────────────────────────────────────────────────────

    fun patchSharkSampleTable(input: ByteArray): ByteArray {
        val ftyp = topLevel(input, "ftyp") ?: throw Exception("ftyp tidak ditemukan.")
        val moov = topLevel(input, "moov") ?: throw Exception("moov tidak ditemukan.")
        val mdat = topLevel(input, "mdat") ?: throw Exception("mdat tidak ditemukan.")

        // Cari video track
        var videoTrak: B? = null
        var p = moov.contentStart
        while (p + 8 <= moov.end) {
            val sz = r32(input, p); val t = cc(input, p + 4)
            if (sz < 8) break
            if (t == "trak") {
                val tb = B(p, sz, t)
                if (hdlrType(input, tb) == "vide") { videoTrak = tb; break }
            }
            p += sz
        }
        val vTrak = videoTrak ?: throw Exception("Track video tidak ditemukan.")

        val stbl = descend(input, vTrak, listOf("mdia", "minf", "stbl")) ?: throw Exception("stbl tidak ditemukan.")
        val mdhd = descend(input, vTrak, listOf("mdia", "mdhd"))          ?: throw Exception("mdhd tidak ditemukan.")
        val elst = descend(input, vTrak, listOf("edts", "elst"))           ?: throw Exception("elst tidak ditemukan.")
        val stts = child(input, stbl, "stts") ?: throw Exception("stts tidak ditemukan.")
        val stsc = child(input, stbl, "stsc") ?: throw Exception("stsc tidak ditemukan.")
        val stsz = child(input, stbl, "stsz") ?: throw Exception("stsz tidak ditemukan.")
        val stco = child(input, stbl, "stco") ?: throw Exception("stco tidak ditemukan.")

        // Parse
        val origSizes   = parseStsz(input, stsz)
        val origStscRows = parseStsc(input, stsc)
        val origOffsets = parseStco(input, stco)
        val fakeCount   = origSizes.size * 9

        // Build replacements
        fun fixedRep() = mapOf(
            mdhd to buildMdhd(input, mdhd),
            elst to buildElst(input, elst),
            stts to buildStts(origSizes.size, fakeCount),
            stsc to buildStsc(origStscRows, origOffsets.size),
            stsz to buildStsz(origSizes, fakeCount)
        )

        val allStco = collectStco(input, moov)

        fun stcoRep(delta: Int, fakeOff: Int) = allStco.associateWith { sc ->
            buildStco(origOffsets, delta, if (sc == stco) fakeOff else null, fakeCount)
        }

        // Pass 1 — ukur moov placeholder
        val moovPlaceholder = rebuildMoov(input, moov, vTrak, fixedRep() + stcoRep(0, 0))
        val mdatPayload     = input.copyOfRange(mdat.contentStart, mdat.end)
        val startPos        = ftyp.size + moovPlaceholder.size + 8

        // Pass 2 — delta real
        var delta    = startPos - mdat.contentStart
        var fakeOff  = startPos + mdatPayload.size
        var moovNew  = rebuildMoov(input, moov, vTrak, fixedRep() + stcoRep(delta, fakeOff))

        // Pass 3 — recalculate (sama kayak JS)
        val recalc  = ftyp.size + moovNew.size + 8
        delta        = recalc - mdat.contentStart
        fakeOff      = recalc + mdatPayload.size
        moovNew      = rebuildMoov(input, moov, vTrak, fixedRep() + stcoRep(delta, fakeOff))

        val ftypBytes = input.copyOfRange(ftyp.off, ftyp.end)
        val mdatNew   = box("mdat", cat(mdatPayload, FAKE_SAMPLE_BYTES))
        return cat(ftypBytes, moovNew, mdatNew)
    }

    // ── Parse helpers ──────────────────────────────────────────

    private fun parseStsz(buf: ByteArray, b: B): List<Int> {
        val cnt = r32(buf, b.contentStart + 8)
        return (0 until cnt).map { r32(buf, b.contentStart + 12 + it * 4) }
    }

    private fun parseStsc(buf: ByteArray, b: B): List<Triple<Int,Int,Int>> {
        val cnt = r32(buf, b.contentStart + 4)
        return (0 until cnt).map { i ->
            val o = b.contentStart + 8 + i * 12
            Triple(r32(buf, o), r32(buf, o+4), r32(buf, o+8))
        }
    }

    private fun parseStco(buf: ByteArray, b: B): List<Int> {
        val cnt = r32(buf, b.contentStart + 4)
        return (0 until cnt).map { r32(buf, b.contentStart + 8 + it * 4) }
    }

    // ── Build replacement boxes ────────────────────────────────

    private fun buildMdhd(buf: ByteArray, b: B): ByteArray {
        val p = buf.copyOfRange(b.contentStart, b.end)
        if (p[0] != 0.toByte()) throw Exception("Versi mdhd tidak didukung.")
        w32(p, 12, VIDEO_TIMESCALE); w32(p, 16, VIDEO_DURATION)
        return box("mdhd", p)
    }

    private fun buildElst(buf: ByteArray, b: B): ByteArray {
        val p = buf.copyOfRange(b.contentStart, b.end)
        if (p[0] != 0.toByte()) throw Exception("elst butuh version 0.")
        w32(p, 12, VIDEO_EDIT_MEDIA_TIME)
        return box("elst", p)
    }

    private fun buildStts(realCnt: Int, fakeCnt: Int): ByteArray {
        val p = ByteArray(4 + 4 + 8 + 8)
        w32(p, 4, 2); w32(p, 8, realCnt); w32(p, 12, VIDEO_SAMPLE_DELTA)
        w32(p, 16, fakeCnt); w32(p, 20, VIDEO_SAMPLE_DELTA)
        return box("stts", p)
    }

    private fun buildStsz(sizes: List<Int>, fakeCnt: Int): ByteArray {
        val total = sizes.size + fakeCnt
        val p = ByteArray(4 + 4 + 4 + total * 4)
        w32(p, 8, total); var o = 12
        for (s in sizes) { w32(p, o, s); o += 4 }
        repeat(fakeCnt) { w32(p, o, FAKE_SAMPLE_SIZE); o += 4 }
        return box("stsz", p)
    }

    private fun buildStsc(rows: List<Triple<Int,Int,Int>>, chunkCnt: Int): ByteArray {
        val r = rows.map { listOf(it.first, it.second, it.third) }.toMutableList()
        if (r.lastOrNull()?.get(1) != 1) r.add(listOf(chunkCnt + 1, 1, 1))
        val p = ByteArray(4 + 4 + r.size * 12); w32(p, 4, r.size); var o = 8
        for (row in r) { w32(p, o, row[0]); w32(p, o+4, row[1]); w32(p, o+8, row[2]); o += 12 }
        return box("stsc", p)
    }

    private fun buildStco(offsets: List<Int>, delta: Int, fakeOff: Int?, fakeCnt: Int): ByteArray {
        val cnt = offsets.size + (if (fakeOff != null) fakeCnt else 0)
        val p = ByteArray(4 + 4 + cnt * 4); w32(p, 4, cnt); var o = 8
        for (off in offsets) { w32(p, o, off + delta); o += 4 }
        if (fakeOff != null) repeat(fakeCnt) { w32(p, o, fakeOff); o += 4 }
        return box("stco", p)
    }

    // ── Collect all stco ───────────────────────────────────────

    private fun collectStco(buf: ByteArray, moov: B): List<B> {
        val result = mutableListOf<B>()
        var p = moov.contentStart
        while (p + 8 <= moov.end) {
            val sz = r32(buf, p); val t = cc(buf, p + 4)
            if (sz < 8) break
            if (t == "trak") {
                val trak = B(p, sz, t)
                val stbl = descend(buf, trak, listOf("mdia", "minf", "stbl"))
                val stco = stbl?.let { child(buf, it, "stco") }
                if (stco != null) result.add(stco)
            }
            p += sz
        }
        return result
    }

    // ── Rebuild moov ───────────────────────────────────────────

    private fun rebuildMoov(buf: ByteArray, moov: B, vTrak: B, rep: Map<B, ByteArray>): ByteArray {
        val children = mutableListOf<ByteArray>()
        var p = moov.contentStart
        while (p + 8 <= moov.end) {
            val sz = r32(buf, p); val t = cc(buf, p + 4)
            if (sz < 8) break
            val cb = B(p, sz, t)
            children.add(
                if (t == "trak" && cb.off == vTrak.off) rebuildTrak(buf, vTrak, rep)
                else rebuildGeneric(buf, cb, rep)
            )
            p += sz
        }
        return box("moov", cat(*children.toTypedArray()))
    }

    private fun rebuildTrak(buf: ByteArray, trak: B, rep: Map<B, ByteArray>): ByteArray {
        val children = mutableListOf<ByteArray>()
        var p = trak.contentStart
        while (p + 8 <= trak.end) {
            val sz = r32(buf, p); val t = cc(buf, p + 4)
            if (sz < 8) break
            children.add(rebuildGeneric(buf, B(p, sz, t), rep)); p += sz
        }
        return box("trak", cat(*children.toTypedArray()))
    }

    private val CONTAINERS = setOf("mdia", "minf", "stbl", "edts", "udta", "dinf")

    private fun rebuildGeneric(buf: ByteArray, b: B, rep: Map<B, ByteArray>): ByteArray {
        rep.entries.firstOrNull { it.key.off == b.off && it.key.type == b.type }?.let { return it.value }
        if (b.type !in CONTAINERS) return buf.copyOfRange(b.off, b.end)
        val children = mutableListOf<ByteArray>()
        var p = b.contentStart
        while (p + 8 <= b.end) {
            val sz = r32(buf, p); val t = cc(buf, p + 4)
            if (sz < 8) break
            children.add(rebuildGeneric(buf, B(p, sz, t), rep)); p += sz
        }
        return box(b.type, cat(*children.toTypedArray()))
    }
}

// ═════════════════════════════════════════════════════════════
//  COMPOSABLE
// ═════════════════════════════════════════════════════════════

@Composable
fun PatchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputUriString  by rememberSaveable { mutableStateOf<String?>(null) }
    var isProcessing    by rememberSaveable { mutableStateOf(false) }
    var statusText      by rememberSaveable { mutableStateOf("") }
    var errorLog        by rememberSaveable { mutableStateOf<String?>(null) }
    var isSuccess       by rememberSaveable { mutableStateOf(false) }
    var savedVideoUri   by remember { mutableStateOf<Uri?>(null) }
    var progressPercent by remember { mutableFloatStateOf(0f) }
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var selectedLogic   by remember { mutableStateOf(PatchLogic.PATCH_ONLY) }
    var selectedQuality by remember { mutableStateOf(EncodeQuality.CPU_CRF20) }

    val inputUri = inputUriString?.let { Uri.parse(it) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            inputUriString = it.toString()
            errorLog = null; isSuccess = false; savedVideoUri = null; progressPercent = 0f
            try {
                val r = MediaMetadataRetriever()
                r.setDataSource(context, it)
                videoDurationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                r.release()
                statusText = "✅ Siap: ${it.lastPathSegment}"
            } catch (e: Exception) {
                videoDurationMs = 0L
                statusText = "✅ Siap: ${it.lastPathSegment}"
            }
        }
    }

    fun executePatch() {
        if (inputUri == null) return
        scope.launch {
            isProcessing = true; isSuccess = false; errorLog = null
            savedVideoUri = null; progressPercent = 0f

            val fileName = "Kythera_Patched_${System.currentTimeMillis()}.mp4"
            val outPath  = File(context.getExternalFilesDir(null), fileName).absolutePath
            val safUrl   = FFmpegKitConfig.getSafParameterForRead(context, inputUri!!)
            val logic    = selectedLogic
            val quality  = selectedQuality

            FFmpegKitConfig.enableStatisticsCallback { stats ->
                if (videoDurationMs > 0) {
                    val pct = (stats.time.toFloat() / videoDurationMs).coerceIn(0f, 0.88f)
                    progressPercent = pct
                    statusText = "⚙️ Memproses: ${(pct * 100).toInt()}%"
                }
            }

            withContext(Dispatchers.IO) {
                try {
                    val finalFile = File(outPath)

                    when (logic) {

                        PatchLogic.PATCH_ONLY -> {
                            statusText = "⚙️ Merapikan MP4 (+faststart)..."
                            val session = FFmpegKit.execute(
                                "-hide_banner -i \"$safUrl\" -c copy -movflags +faststart \"$outPath\""
                            )
                            if (!ReturnCode.isSuccess(session.returnCode))
                                throw Exception(session.allLogsAsString)
                            progressPercent = 0.9f
                            statusText = "⚙️ Menerapkan Shark Sample Table..."
                            val patched = Mp4Engine.patchSharkSampleTable(finalFile.readBytes())
                            FileOutputStream(finalFile).use { it.write(patched) }
                        }

                        PatchLogic.ENCODE_PATCH -> {
                            statusText = "⚙️ Encoding (${quality.label})..."
                            val audioP = "-c:a aac -b:a 128k -shortest " +
                                "-metadata copyright=\"By kythera\" " +
                                "-metadata artist=\"By kythera\" -movflags +faststart"
                            val session = FFmpegKit.execute(
                                "-hide_banner -i \"$safUrl\" -threads 0 -vf \"format=yuv420p\" " +
                                "${quality.cmdParams} $audioP \"$outPath\""
                            )
                            if (!ReturnCode.isSuccess(session.returnCode))
                                throw Exception(session.allLogsAsString)
                            progressPercent = 0.9f
                            statusText = "⚙️ Menerapkan Shark Sample Table..."
                            val patched = Mp4Engine.patchSharkSampleTable(finalFile.readBytes())
                            FileOutputStream(finalFile).use { it.write(patched) }
                        }

                        PatchLogic.KYTHERA_60FPS -> {
                            statusText = "⚙️ Menyiapkan file dasar..."
                            val session = FFmpegKit.execute(
                                "-hide_banner -i \"$safUrl\" -c copy \"$outPath\""
                            )
                            if (!ReturnCode.isSuccess(session.returnCode))
                                throw Exception(session.allLogsAsString)
                            progressPercent = 0.9f
                            statusText = "⚙️ Menyematkan Kythera Metadata Stamp..."
                            val stamped = Mp4Engine.applyMetadataStamp(finalFile.readBytes())
                            FileOutputStream(finalFile).use { it.write(stamped) }
                        }
                    }

                    // Simpan ke galeri
                    progressPercent = 1f; statusText = "Menyimpan ke galeri..."
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kythera")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let { destUri ->
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            FileInputStream(finalFile).use { it.copyTo(out) }
                        }
                        finalFile.delete()
                        savedVideoUri = destUri
                        isSuccess = true
                        statusText = "✅ Done! Tersimpan di Movies/Kythera"
                    } ?: throw Exception("Gagal menyimpan ke Galeri")

                } catch (e: Exception) {
                    statusText = "❌ Proses Gagal"
                    errorLog = e.message ?: "Unknown Error"
                } finally {
                    FFmpegKitConfig.enableStatisticsCallback(null)
                }
            }
            isProcessing = false
        }
    }

    // ── UI ────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Kythera Patcher", color = KColor.Text, fontSize = 24.sp, fontWeight = FontWeight.W800)
        Text("MP4 Optimizer & Frame Injector", color = KColor.Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        GlassCard {
            KDropZone(
                onTap = { videoPicker.launch("video/*") },
                title = if (inputUri != null) "Ganti Video Target" else "Select video",
                subtitle = "MP4 File Only",
                icon = Icons.Rounded.CloudUpload,
                accentColor = KColor.Orange
            )
            if (statusText.isNotEmpty() && !isProcessing && errorLog == null) {
                Spacer(Modifier.height(12.dp))
                Text(statusText, color = if (isSuccess) KColor.Orange else KColor.Text2, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))

        GlassCard {
            Text("Metode Patch", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            PatchLogic.entries.forEach { logic ->
                val isActive = selectedLogic == logic
                Row(
                    modifier = Modifier
                        .fillMaxWidth().padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) KColor.Orange.copy(0.12f) else Color.Transparent)
                        .border(if (isActive) 1.dp else 0.dp,
                            if (isActive) KColor.Orange.copy(0.4f) else Color.Transparent,
                            RoundedCornerShape(10.dp))
                        .clickable { selectedLogic = logic }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(logic.label,
                            color = if (isActive) KColor.Orange else KColor.Text,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp)
                        Text(logic.desc, color = KColor.Text2, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    RadioButton(selected = isActive, onClick = { selectedLogic = logic },
                        colors = RadioButtonDefaults.colors(selectedColor = KColor.Orange))
                }
            }
        }

        AnimatedVisibility(
            visible = selectedLogic == PatchLogic.ENCODE_PATCH,
            enter = expandVertically(tween(300)), exit = shrinkVertically(tween(300))
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                GlassCard {
                    Text("Kualitas Encoding", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    EncodeQuality.entries.forEach { quality ->
                        val isActive = selectedQuality == quality
                        Row(
                            modifier = Modifier
                                .fillMaxWidth().padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isActive) KColor.Orange.copy(0.12f) else Color.Transparent)
                                .border(if (isActive) 1.dp else 0.dp,
                                    if (isActive) KColor.Orange.copy(0.4f) else Color.Transparent,
                                    RoundedCornerShape(10.dp))
                                .clickable { selectedQuality = quality }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(quality.label,
                                    color = if (isActive) KColor.Orange else KColor.Text,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp)
                                Text(quality.desc, color = KColor.Text2, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                            RadioButton(selected = isActive, onClick = { selectedQuality = quality },
                                colors = RadioButtonDefaults.colors(selectedColor = KColor.Orange))
                        }
                    }
                }
            }
        }

        if (errorLog != null) {
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                    .border(1.dp, Color(0xFFFF4444), RoundedCornerShape(10.dp))
                    .background(Color(0x22FF0000), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text("⚠️ ERROR", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(errorLog!!, color = Color(0xFFFFAAAA), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        if (isProcessing) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KColor.Surface).padding(16.dp)
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("⏳ $statusText", color = KColor.Orange, fontSize = 13.sp)
                        if (progressPercent > 0f)
                            Text("${(progressPercent * 100).toInt()}%",
                                color = KColor.Orange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (progressPercent > 0f && progressPercent < 1f)
                        LinearProgressIndicator(progress = { progressPercent },
                            modifier = Modifier.fillMaxWidth().height(5.dp), color = KColor.Orange)
                    else
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(5.dp), color = KColor.Orange)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (isSuccess && savedVideoUri != null) {
            KPrimaryButton(
                label = "Reset", icon = Icons.Rounded.Refresh,
                modifier = Modifier.fillMaxWidth(),
                startColor = KColor.Orange, endColor = Color(0xFFD97706),
                onClick = {
                    inputUriString = null; isSuccess = false
                    statusText = ""; savedVideoUri = null; progressPercent = 0f
                }
            )
        } else {
            KPrimaryButton(
                label = "Patch & Download", icon = Icons.Rounded.AutoFixHigh,
                enabled = inputUri != null && !isProcessing,
                startColor = KColor.Orange, endColor = Color(0xFFD97706),
                onClick = ::executePatch
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
