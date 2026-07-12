package com.d4nzxml.kythera.ui.screen

import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

// ─────────────────────────────────────────────────────────────────────────────
// ENUMS
// ─────────────────────────────────────────────────────────────────────────────
enum class PatchMode(val label: String, val icon: String, val desc: String) {
    SHARK_ONLY(
        "Patch Only", "🩹",
        "NXT_SHARK537: Rebuild tabel MP4 + inject fake samples. Tanpa re-encode."
    ),
    ENCODE_SHARK(
        "Encode + Patch", "🎬",
        "Re-encode dulu lalu terapkan NXT_SHARK537 Method. Kualitas optimal."
    ),
    ENCODE_ONLY(
        "Encode Only", "🔧",
        "Hanya re-encode tanpa patch. Untuk perbaikan video rusak."
    ),
    KYTHERA_60(
        "Kythera 60fps", "⚡",
        "Sematkan Z-Payload + encoder tag eksklusif Kythera."
    )
}

enum class EncodePreset(val label: String, val crf: Int, val preset: String) {
    BALANCED("⚖️ Seimbang (CRF 20)", 20, "fast"),
    QUALITY("✨ Kualitas (CRF 18)", 18, "fast"),
    SPEED("⚡ Ultrafast (CRF 22)", 22, "ultrafast"),
}

// ─────────────────────────────────────────────────────────────────────────────
// NXT_SHARK537 — Port FULL dari popup.js (rebuild tabel, bukan in-place patch)
// ─────────────────────────────────────────────────────────────────────────────
private object SharkPatcher {

    // ── Konstanta (sama persis popup.js) ─────────────────────────────────────
    private val   FAKE_SAMPLE_BYTES  = byteArrayOf(0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00)
    private const val FAKE_SAMPLE_SIZE   = 8L
    private const val VIDEO_TIMESCALE    = 90000L
    private const val VIDEO_DURATION     = 2269500L
    private const val VIDEO_EDIT_MEDIA_TIME = 0L
    private const val VIDEO_SAMPLE_DELTA = 1500L
    private val CONTAINER_BOXES = setOf("moov","trak","mdia","minf","stbl","edts","dinf","udta","meta","ilst")

    // ── Helpers ───────────────────────────────────────────────────────────────
    private data class Box(
        val type: String, val offset: Int, val size: Int, val headerSize: Int,
        val data: ByteArray,
        val children: MutableList<Box> = mutableListOf(),
        var prefixStart: Int = 0, var prefixEnd: Int = 0
    ) {
        val contentStart get() = offset + headerSize
        val end          get() = offset + size
    }

    private fun u32(d: ByteArray, o: Int): Long =
        ((d[o].toLong() and 0xFF) shl 24) or ((d[o+1].toLong() and 0xFF) shl 16) or
        ((d[o+2].toLong() and 0xFF) shl 8)  or (d[o+3].toLong() and 0xFF)

    private fun pu32(b: ByteArray, o: Int, v: Long) {
        b[o]=(( v shr 24) and 0xFF).toByte(); b[o+1]=((v shr 16) and 0xFF).toByte()
        b[o+2]=((v shr 8) and 0xFF).toByte(); b[o+3]=(v and 0xFF).toByte()
    }

    private fun boxType(d: ByteArray, o: Int) = String(byteArrayOf(d[o],d[o+1],d[o+2],d[o+3]))

    private fun parseBoxes(data: ByteArray, start: Int = 0, end: Int = data.size): List<Box> {
        val boxes = mutableListOf<Box>()
        var offset = start
        while (offset + 8 <= end) {
            val small = u32(data, offset)
            val type  = boxType(data, offset + 4)
            val (size, hdr) = when {
                small == 1L  -> {
                    val h = u32(data, offset+8); val l = u32(data, offset+12)
                    Pair(h * 4294967296L + l, 16)
                }
                small == 0L  -> Pair((end - offset).toLong(), 8)
                else         -> Pair(small, 8)
            }
            val box = Box(type, offset, size.toInt(), hdr, data,
                prefixStart = offset + hdr, prefixEnd = offset + hdr)
            if (CONTAINER_BOXES.contains(type)) {
                val cs = if (type == "meta") box.contentStart + 4 else box.contentStart
                box.prefixEnd = cs
                box.children.addAll(parseBoxes(data, cs, box.end))
            }
            boxes.add(box)
            offset += size.toInt()
        }
        return boxes
    }

    private fun findChild(box: Box, type: String) = box.children.find { it.type == type }
    private fun findDesc(box: Box, path: List<String>): Box? {
        var c: Box? = box
        for (t in path) { c = c?.let { findChild(it, t) } ?: return null }
        return c
    }
    private fun findTop(boxes: List<Box>, type: String) = boxes.find { it.type == type }
    private fun handlerType(trak: Box): String? {
        val hdlr = findDesc(trak, listOf("mdia","hdlr")) ?: return null
        return if (hdlr.offset + 20 <= hdlr.end) boxType(hdlr.data, hdlr.offset + 16) else null
    }

    private fun parseStsz(stsz: Box): List<Long> {
        val sampleSize = u32(stsz.data, stsz.offset + 12)
        val count      = u32(stsz.data, stsz.offset + 16).toInt()
        if (sampleSize != 0L) return List(count) { sampleSize }
        val ts = stsz.offset + 20
        return List(count) { i -> u32(stsz.data, ts + i * 4) }
    }

    private fun parseStco(stco: Box): List<Long> {
        val count = u32(stco.data, stco.offset + 12).toInt()
        val ts    = stco.offset + 16
        return List(count) { i -> u32(stco.data, ts + i * 4) }
    }

    private fun parseStsc(stsc: Box): List<Triple<Long,Long,Long>> {
        val count = u32(stsc.data, stsc.offset + 12).toInt()
        val ts    = stsc.offset + 16
        return List(count) { i ->
            val b = ts + i * 12
            Triple(u32(stsc.data,b), u32(stsc.data,b+4), u32(stsc.data,b+8))
        }
    }

    private fun makeBox(type: String, payload: ByteArray): ByteArray {
        val buf = ByteArray(8 + payload.size)
        pu32(buf, 0, (8 + payload.size).toLong())
        type.forEachIndexed { i, c -> buf[4+i] = c.code.toByte() }
        System.arraycopy(payload, 0, buf, 8, payload.size)
        return buf
    }

    private fun boxBytes(box: Box) = box.data.copyOfRange(box.offset, box.end)

    private fun concat(parts: List<ByteArray>): ByteArray {
        val total = parts.sumOf { it.size }
        val out   = ByteArray(total); var pos = 0
        parts.forEach { System.arraycopy(it, 0, out, pos, it.size); pos += it.size }
        return out
    }

    // ── Box builders (persis popup.js) ───────────────────────────────────────
    private fun buildMdhd(mdhd: Box): ByteArray {
        val p = mdhd.data.copyOfRange(mdhd.contentStart, mdhd.end).copyOf()
        if (p[0].toInt() == 1) { pu32(p,24,0L); pu32(p,28,VIDEO_TIMESCALE); pu32(p,32,0L); pu32(p,36,VIDEO_DURATION) }
        else                   { pu32(p,12,VIDEO_TIMESCALE); pu32(p,16,VIDEO_DURATION) }
        return makeBox("mdhd", p)
    }

    private fun buildElst(elst: Box): ByteArray {
        val p   = elst.data.copyOfRange(elst.contentStart, elst.end).copyOf()
        val ver = p[0].toInt()
        val cnt = u32(p, 4).toInt()
        if (cnt >= 1) {
            if (ver == 1) { pu32(p,8,0L);pu32(p,12,VIDEO_DURATION);pu32(p,16,0L);pu32(p,20,VIDEO_EDIT_MEDIA_TIME) }
            else          { pu32(p,8,VIDEO_DURATION);pu32(p,12,VIDEO_EDIT_MEDIA_TIME) }
        }
        return makeBox("elst", p)
    }

    private fun buildStts(real: Int, fake: Int): ByteArray {
        val p = ByteArray(4 + 4 + 2*8)
        pu32(p,4,2L); pu32(p,8,real.toLong()); pu32(p,12,VIDEO_SAMPLE_DELTA)
        pu32(p,16,fake.toLong()); pu32(p,20,VIDEO_SAMPLE_DELTA)
        return makeBox("stts", p)
    }

    private fun buildStsz(orig: List<Long>, fake: Int): ByteArray {
        val total = orig.size + fake
        val p = ByteArray(4 + 4 + total*4); pu32(p,4,total.toLong())
        var off = 8; orig.forEach { pu32(p,off,it); off+=4 }
        repeat(fake) { pu32(p,off,FAKE_SAMPLE_SIZE); off+=4 }
        return makeBox("stsz", p)
    }

    private fun buildStsc(orig: List<Triple<Long,Long,Long>>, chunkCount: Int): ByteArray {
        val rows = orig.toMutableList()
        if (rows.lastOrNull()?.second != 1L) rows.add(Triple((chunkCount+1).toLong(),1L,1L))
        val p = ByteArray(4 + 4 + rows.size*12); pu32(p,4,rows.size.toLong())
        var off = 8; rows.forEach { (f,spc,sdi) -> pu32(p,off,f);pu32(p,off+4,spc);pu32(p,off+8,sdi);off+=12 }
        return makeBox("stsc", p)
    }

    private fun buildStco(orig: List<Long>, delta: Long, fakeOff: Long? = null, fake: Int = 0): ByteArray {
        val count = orig.size + (if (fakeOff != null) fake else 0)
        val p = ByteArray(4 + 4 + count*4); pu32(p,4,count.toLong())
        var off = 8
        orig.forEach { pu32(p,off,it+delta); off+=4 }
        if (fakeOff != null) repeat(fake) { pu32(p,off,fakeOff); off+=4 }
        return makeBox("stco", p)
    }

    private fun rebuildBox(box: Box, rep: Map<Box,ByteArray>): ByteArray {
        rep[box]?.let { return it }
        if (box.children.isEmpty()) return boxBytes(box)
        val parts = mutableListOf(box.data.copyOfRange(box.prefixStart, box.prefixEnd))
        box.children.forEach { parts.add(rebuildBox(it, rep)) }
        return makeBox(box.type, concat(parts))
    }

    private fun collectStcos(moov: Box): List<Box> {
        val result = mutableListOf<Box>()
        moov.children.filter { it.type == "trak" }.forEach { trak ->
            val stbl = findDesc(trak, listOf("mdia","minf","stbl")) ?: return@forEach
            // reject co64
            if (findChild(stbl, "co64") != null) throw Exception("File pakai co64, tidak didukung.")
            findChild(stbl, "stco")?.let { result.add(it) }
        }
        return result
    }

    // ── MAIN: full rebuild seperti popup.js ───────────────────────────────────
    fun patch(input: ByteArray): ByteArray {
        val top  = parseBoxes(input)
        val ftyp = findTop(top, "ftyp") ?: throw Exception("Box ftyp tidak ditemukan.")
        val moov = findTop(top, "moov") ?: throw Exception("Box moov tidak ditemukan.")
        val mdat = findTop(top, "mdat") ?: throw Exception("Box mdat tidak ditemukan.")

        val videoTrak = moov.children.find { it.type == "trak" && handlerType(it) == "vide" }
            ?: throw Exception("Track video tidak ditemukan.")

        val stbl = findDesc(videoTrak, listOf("mdia","minf","stbl"))
            ?: throw Exception("Box stbl tidak ditemukan.")
        val mdhd = findDesc(videoTrak, listOf("mdia","mdhd"))
            ?: throw Exception("Box mdhd tidak ditemukan.")
        val elst = findDesc(videoTrak, listOf("edts","elst")) // nullable — opsional
        val stts = findChild(stbl,"stts") ?: throw Exception("Box stts tidak ditemukan.")
        val stsc = findChild(stbl,"stsc") ?: throw Exception("Box stsc tidak ditemukan.")
        val stsz = findChild(stbl,"stsz") ?: throw Exception("Box stsz tidak ditemukan.")
        val stco = findChild(stbl,"stco") ?: throw Exception("Box stco tidak ditemukan.")

        val origSizes  = parseStsz(stsz)
        val realCount  = origSizes.size
        val fakeCount  = realCount * 9   // sama persis popup.js
        val origStscR  = parseStsc(stsc)
        val origStcoO  = parseStco(stco)
        val stcoBoxes  = collectStcos(moov)
        val preserved  = top.filter { it.type !in listOf("ftyp","moov","mdat") }.map { boxBytes(it) }

        val fixed = mutableMapOf<Box,ByteArray>(
            mdhd to buildMdhd(mdhd),
            stts to buildStts(realCount, fakeCount),
            stsc to buildStsc(origStscR, origStcoO.size),
            stsz to buildStsz(origSizes, fakeCount)
        )
        if (elst != null) fixed[elst] = buildElst(elst)

        // Pass 1 — placeholder delta=0
        val ph = HashMap(fixed)
        stcoBoxes.forEach { s -> ph[s] = buildStco(parseStco(s), 0L, if (s===stco) 0L else null, fakeCount) }
        val moovPh    = rebuildBox(moov, ph)
        val preservedB= concat(preserved)
        val oldMdatPay= input.copyOfRange(mdat.contentStart, mdat.end)
        val newMdatStart0 = ftyp.size + moovPh.size + preservedB.size + 8L
        var delta     = newMdatStart0 - mdat.contentStart
        var fakeOff   = newMdatStart0 + oldMdatPay.size

        // Pass 2
        var fr = HashMap(fixed)
        stcoBoxes.forEach { s -> fr[s] = buildStco(parseStco(s), delta, if (s===stco) fakeOff else null, fakeCount) }
        var moovNew   = rebuildBox(moov, fr)
        val newMdatStart1 = ftyp.size + moovNew.size + preservedB.size + 8L
        delta   = newMdatStart1 - mdat.contentStart
        fakeOff = newMdatStart1 + oldMdatPay.size

        // Pass 3 — final
        fr = HashMap(fixed)
        stcoBoxes.forEach { s -> fr[s] = buildStco(parseStco(s), delta, if (s===stco) fakeOff else null, fakeCount) }
        moovNew = rebuildBox(moov, fr)

        return concat(listOf(boxBytes(ftyp), moovNew, preservedB, makeBox("mdat", concat(listOf(oldMdatPay, FAKE_SAMPLE_BYTES)))))
    }

    // ── Z-Payload + encoder (Kythera 60fps) ──────────────────────────────────
    fun patchKythera60(input: ByteArray): ByteArray {
        val buf = input.copyOf()
        // Z-Payload
        var mdatPos = 0
        while (mdatPos + 8 <= buf.size) {
            val sz = ((buf[mdatPos].toLong() and 0xFF) shl 24) or ((buf[mdatPos+1].toLong() and 0xFF) shl 16) or
                     ((buf[mdatPos+2].toLong() and 0xFF) shl 8)  or (buf[mdatPos+3].toLong() and 0xFF)
            if (String(byteArrayOf(buf[mdatPos+4],buf[mdatPos+5],buf[mdatPos+6],buf[mdatPos+7])) == "mdat") {
                val zt = mdatPos + 10
                for (i in 0 until 128) if (zt+i < buf.size) buf[zt+i] = 0x5A
                break
            }
            if (sz < 8) break
            mdatPos += sz.toInt()
        }
        // Encoder tag
        val lavf   = "Lavf".toByteArray()
        val target = "Lavf59.16.100".toByteArray()
        var i = 0
        while (i <= buf.size - 16) {
            if (buf[i]==lavf[0]&&buf[i+1]==lavf[1]&&buf[i+2]==lavf[2]&&buf[i+3]==lavf[3]&&buf[i+4] in 0x30..0x39) {
                var end = i+4; while (end<buf.size&&buf[end]>=0x20&&buf[end]<0x7F) end++
                val ol = end - i; for (j in 0 until ol) buf[i+j] = if (j<target.size) target[j] else 0x00
                break
            }
            i++
        }
        return buf
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PIPELINE
// ─────────────────────────────────────────────────────────────────────────────
private suspend fun runPatchPipeline(
    context:    android.content.Context,
    sourceUri:  Uri,
    mode:       PatchMode,
    preset:     EncodePreset,
    onProgress: (String, Int) -> Unit
): File? = withContext(Dispatchers.IO) {
    val ts        = System.currentTimeMillis()
    val cacheDir  = context.cacheDir
    val inputFile = File(cacheDir, "ky_in_$ts.mp4")
    val encFile   = File(cacheDir, "ky_enc_$ts.mp4")
    val outFile   = File(cacheDir, "ky_out_$ts.mp4")

    try {
        onProgress("📂 Menyalin video...", 5)
        context.contentResolver.openInputStream(sourceUri)?.use { ins ->
            inputFile.outputStream().use { ins.copyTo(it) }
        } ?: return@withContext null

        when (mode) {
            PatchMode.SHARK_ONLY -> {
                onProgress("🩹 Membaca video...", 20)
                val raw = inputFile.readBytes()
                onProgress("🩹 Rebuilding MP4 sample tables...", 50)
                val patched = SharkPatcher.patch(raw)
                onProgress("🩹 Menulis output...", 90)
                outFile.writeBytes(patched)
            }

            PatchMode.ENCODE_SHARK -> {
                onProgress("🎬 Menyiapkan encoder...", 10)
                var lastP = 10
                FFmpegKitConfig.enableStatisticsCallback { stats ->
                    val ratio = (stats.time / 1000.0).coerceIn(0.0, 1.0)
                    val p = (10 + ratio * 65).toInt().coerceAtMost(75)
                    if (p > lastP) { lastP = p; onProgress("🎬 Encoding ${(ratio*100).toInt()}%...", p) }
                }
                val cmd = "-y -i \"${inputFile.absolutePath}\" " +
                          "-vf format=yuv420p " +
                          "-c:v libx264 -preset ${preset.preset} -crf ${preset.crf} " +
                          "-bf 0 -movflags +faststart " +
                          "-c:a aac -b:a 128k " +
                          "-metadata copyright=\"By Kythera\" " +
                          "-metadata artist=\"D4nzxml\" " +
                          "\"${encFile.absolutePath}\""
                val session = FFmpegKit.execute(cmd)
                FFmpegKitConfig.enableStatisticsCallback(null)
                if (!ReturnCode.isSuccess(session.returnCode))
                    throw Exception("Encode gagal:\n${session.allLogsAsString?.takeLast(500)}")

                onProgress("🩹 Rebuilding MP4 sample tables...", 80)
                val raw     = encFile.readBytes()
                val patched = SharkPatcher.patch(raw)
                onProgress("🩹 Menulis output...", 95)
                outFile.writeBytes(patched)
            }

            PatchMode.ENCODE_ONLY -> {
                onProgress("🔧 Encoding...", 10)
                var lastP = 10
                FFmpegKitConfig.enableStatisticsCallback { stats ->
                    val ratio = (stats.time / 1000.0).coerceIn(0.0, 1.0)
                    val p = (10 + ratio * 85).toInt().coerceAtMost(95)
                    if (p > lastP) { lastP = p; onProgress("🔧 Encoding ${(ratio*100).toInt()}%...", p) }
                }
                val cmd = "-y -i \"${inputFile.absolutePath}\" " +
                          "-c:v libx264 -preset ${preset.preset} -crf ${preset.crf} " +
                          "-movflags +faststart -c:a aac -b:a 128k " +
                          "\"${outFile.absolutePath}\""
                val session = FFmpegKit.execute(cmd)
                FFmpegKitConfig.enableStatisticsCallback(null)
                if (!ReturnCode.isSuccess(session.returnCode))
                    throw Exception("Encode gagal:\n${session.allLogsAsString?.takeLast(500)}")
            }

            PatchMode.KYTHERA_60 -> {
                onProgress("⚡ Membaca video...", 20)
                val raw = inputFile.readBytes()
                onProgress("⚡ Menyematkan Z-Payload + encoder tag...", 60)
                outFile.writeBytes(SharkPatcher.patchKythera60(raw))
            }
        }

        onProgress("✅ Selesai!", 100)
        outFile

    } catch (e: Exception) {
        onProgress("❌ ${e.message?.take(120)}", -1)
        null
    } finally {
        inputFile.delete()
        encFile.delete()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ANIMASI (sama dengan TikTokScreen)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PatchAnimation(statusMsg: String, progress: Int) {
    val inf = rememberInfiniteTransition(label = "patch")
    val pulse by inf.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val scanY by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "scan")
    val dot   by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot")

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(90.dp).scale(pulse).clip(CircleShape).background(Color(0x33FFA040)))
            Box(
                Modifier.size(70.dp).clip(CircleShape).background(
                    Brush.radialGradient(listOf(Color(0xFFFF8C00), Color(0xFF7B3800)))
                ), contentAlignment = Alignment.Center
            ) { Text("⚡", fontSize = 28.sp) }
        }

        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2A2A1E))) {
            if (progress in 0..100) {
                Box(Modifier.fillMaxWidth(progress/100f).fillMaxHeight().background(
                    Brush.horizontalGradient(listOf(Color(0xFFFF8C00), Color(0xFFFFCC44)))
                ))
            }
            Box(Modifier.fillMaxWidth(scanY).fillMaxHeight().background(Color(0x88FFCC44)))
        }

        Text(statusMsg, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(Modifier.size(6.dp).clip(CircleShape).background(
                    Color(0xFFFF8C00).copy(alpha = if (i==0) dot else 1f - dot*0.3f)
                ))
            }
        }
        if (progress in 0..100) Text("$progress%", color = Color(0xFFFFCC44), fontSize = 12.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PatchScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var inputUriString  by rememberSaveable { mutableStateOf<String?>(null) }
    var isProcessing    by remember { mutableStateOf(false) }
    var statusMsg       by remember { mutableStateOf("") }
    var progressVal     by remember { mutableStateOf(0) }
    var errorLog        by remember { mutableStateOf<String?>(null) }
    var isSuccess       by remember { mutableStateOf(false) }
    var savedVideoUri   by remember { mutableStateOf<Uri?>(null) }
    var selectedMode    by remember { mutableStateOf(PatchMode.SHARK_ONLY) }
    var selectedPreset  by remember { mutableStateOf(EncodePreset.BALANCED) }

    val inputUri = inputUriString?.let { Uri.parse(it) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            inputUriString = it.toString()
            errorLog       = null
            isSuccess      = false
            savedVideoUri  = null
            statusMsg      = "✅ Siap: ${it.lastPathSegment}"
        }
    }

    fun process() {
        if (inputUri == null) return
        scope.launch {
            isProcessing = true; isSuccess = false; errorLog = null
            savedVideoUri = null; progressVal = 0

            val outFile = runPatchPipeline(context, inputUri, selectedMode, selectedPreset) { msg, p ->
                statusMsg   = msg
                progressVal = p.coerceAtLeast(0)
            }

            if (outFile != null && outFile.exists()) {
                // Simpan ke galeri
                val fileName = "Kythera_Patched_${System.currentTimeMillis()}.mp4"
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Kythera")
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let { dest ->
                    context.contentResolver.openOutputStream(dest)?.use { out ->
                        FileInputStream(outFile).use { it.copyTo(out) }
                    }
                    outFile.delete()
                    savedVideoUri = dest
                }
                isSuccess  = true
                statusMsg  = "✅ Tersimpan di Galeri/Kythera"
            } else {
                errorLog = statusMsg.removePrefix("❌ ")
                statusMsg = "❌ Gagal!"
            }
            isProcessing = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Text("Patch Video", color = KColor.Text, fontSize = 22.sp, fontWeight = FontWeight.W800)
            Text("NXT_SHARK537 Method — MP4 Table Rebuild", color = KColor.Orange, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            // Input
            GlassCard {
                KDropZone(
                    onTap       = { picker.launch("video/*") },
                    title       = if (inputUri != null) "Ganti Video" else "Pilih Video",
                    subtitle    = "MP4 — Patch, Encode, atau Keduanya",
                    icon        = Icons.Rounded.CloudUpload,
                    accentColor = KColor.Orange
                )
                if (statusMsg.isNotEmpty() && !isProcessing && errorLog == null) {
                    Spacer(Modifier.height(12.dp))
                    Text(statusMsg, color = if (isSuccess) KColor.Orange else KColor.Text2, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Mode selector
            GlassCard {
                Text("Mode Proses", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                PatchMode.entries.forEach { mode ->
                    val active = selectedMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                if (active) Color(0xFFFF8C00) else Color(0xFF2A2A1E),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedMode = mode }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(mode.icon, fontSize = 20.sp)
                        Column(Modifier.weight(1f)) {
                            Text(mode.label, fontWeight = FontWeight.SemiBold,
                                color = if (active) Color.White else KColor.Text, fontSize = 14.sp)
                            Text(mode.desc, fontSize = 11.sp,
                                color = if (active) Color(0xFFFFEECC) else KColor.Text2, lineHeight = 16.sp)
                        }
                    }
                }
            }

            // Preset (muncul kalau encode)
            if (selectedMode == PatchMode.ENCODE_SHARK || selectedMode == PatchMode.ENCODE_ONLY) {
                Spacer(Modifier.height(14.dp))
                GlassCard {
                    Text("Kualitas Encode", color = KColor.Text, fontWeight = FontWeight.W600, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EncodePreset.entries.forEach { p ->
                            val active = selectedPreset == p
                            Box(
                                Modifier.weight(1f)
                                    .background(
                                        if (active) Color(0xFFFF8C00) else Color(0xFF2A2A1E),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedPreset = p }
                                    .padding(vertical = 12.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(p.label, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = if (active) Color.White else KColor.Text2)
                            }
                        }
                    }
                }
            }

            // Error
            if (errorLog != null) {
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .border(1.dp, Color(0xFFFF4444), RoundedCornerShape(10.dp))
                        .background(Color(0x22FF0000), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("⚠️ ERROR", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(errorLog!!, color = Color(0xFFFFAAAA), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }

            Spacer(Modifier.height(20.dp))

            if (isSuccess && savedVideoUri != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KPrimaryButton(
                        label = "Tonton", icon = Icons.Rounded.PlayArrow,
                        modifier = Modifier.weight(1f), startColor = KColor.Orange, endColor = Color(0xFFD97706),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(savedVideoUri, "video/mp4")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    )
                    KPrimaryButton(
                        label = "Reset", icon = Icons.Rounded.Refresh,
                        modifier = Modifier.weight(1f), startColor = KColor.Orange, endColor = Color(0xFFD97706),
                        onClick = {
                            inputUriString = null; isSuccess = false
                            statusMsg = ""; savedVideoUri = null; errorLog = null
                        }
                    )
                }
            } else {
                KPrimaryButton(
                    label = "Patch & Download", icon = Icons.Rounded.AutoFixHigh,
                    enabled = inputUri != null && !isProcessing,
                    startColor = KColor.Orange, endColor = Color(0xFFD97706),
                    onClick = ::process
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // Overlay processing — style TikTok screen
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xF01A1400), Color(0xF0200A00))),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(28.dp)
                ) {
                    Text("KYTHERA PATCHER", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF8C00), letterSpacing = 4.sp)
                    Spacer(Modifier.height(4.dp))
                    PatchAnimation(statusMsg = statusMsg, progress = progressVal)
                }
            }
        }
    }
}
