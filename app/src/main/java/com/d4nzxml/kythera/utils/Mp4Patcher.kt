package com.d4nzxml.kythera.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object Mp4Patcher {
    private const val FAKE_SAMPLE_SIZE = 8
    private val FAKE_SAMPLE_BYTES = byteArrayOf(0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00)
    private const val VIDEO_TIMESCALE = 90000
    private const val VIDEO_DURATION = 2269500
    private const val VIDEO_EDIT_MEDIA_TIME = 0
    private const val VIDEO_SAMPLE_DELTA = 1500

    private val CONTAINER_BOXES = setOf("moov", "trak", "mdia", "minf", "stbl", "edts", "dinf", "udta", "meta", "ilst")

    class Box(
        val type: String,
        val offset: Int,
        val size: Long,
        val headerSize: Int,
        val contentStart: Int,
        val end: Int,
        val path: String,
        val data: ByteArray,
        val view: ByteBuffer,
        var children: List<Box> = emptyList(),
        var prefixStart: Int = 0,
        var prefixEnd: Int = 0
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Box) return false
            return offset == other.offset && type == other.type
        }
        override fun hashCode(): Int = offset.hashCode() * 31 + type.hashCode()
    }

    private fun getBoxType(data: ByteArray, offset: Int): String {
        return String(data, offset, 4, StandardCharsets.US_ASCII)
    }

    private fun setBoxType(data: ByteArray, offset: Int, type: String) {
        val bytes = type.toByteArray(StandardCharsets.US_ASCII)
        for (i in 0 until 4) {
            data[offset + i] = bytes[i]
        }
    }

    private fun getUint32(view: ByteBuffer, offset: Int): Long {
        return view.getInt(offset).toLong() and 0xFFFFFFFFL
    }

    private fun setUint32(view: ByteBuffer, offset: Int, value: Long) {
        view.putInt(offset, value.toInt())
    }

    private fun readBox(view: ByteBuffer, data: ByteArray, offset: Int, end: Int, parentPath: String = ""): Box {
        if (offset + 8 > end) {
            throw Exception("MP4 invalido: kotak tidak lengkap.")
        }

        val smallSize = getUint32(view, offset)
        val type = getBoxType(data, offset + 4)
        var size = smallSize
        var headerSize = 8

        if (smallSize == 1L) {
            if (offset + 16 > end) throw Exception("MP4 invalido: kotak $type tidak lengkap.")
            val high = getUint32(view, offset + 8)
            val low = getUint32(view, offset + 12)
            size = (high shl 32) or low
            headerSize = 16
        } else if (smallSize == 0L) {
            size = (end - offset).toLong()
        }

        if (size < headerSize || offset + size > end) {
            throw Exception("MP4 invalido: ukuran salah di kotak $type.")
        }

        val path = if (parentPath.isNotEmpty()) "$parentPath/$type" else type

        return Box(
            type = type,
            offset = offset,
            size = size,
            headerSize = headerSize,
            contentStart = offset + headerSize,
            end = (offset + size).toInt(),
            path = path,
            data = data,
            view = view,
            prefixStart = offset + headerSize,
            prefixEnd = offset + headerSize
        )
    }

    private fun childStartForBox(box: Box): Int {
        return if (box.type == "meta") box.contentStart + 4 else box.contentStart
    }

    private fun parseBoxes(data: ByteArray, view: ByteBuffer, start: Int = 0, end: Int = data.size, parentPath: String = ""): List<Box> {
        val boxes = mutableListOf<Box>()
        var offset = start

        while (offset + 8 <= end) {
            val box = readBox(view, data, offset, end, parentPath)

            if (CONTAINER_BOXES.contains(box.type)) {
                val childStart = childStartForBox(box)
                if (childStart > box.end) {
                    throw Exception("MP4 invalido: container ${box.type} terlalu pendek.")
                }
                box.prefixStart = box.contentStart
                box.prefixEnd = childStart
                box.children = parseBoxes(data, view, childStart, box.end, box.path)
            }
            boxes.add(box)
            offset = box.end
        }
        return boxes
    }

    private fun findChild(box: Box, type: String): Box? = box.children.find { it.type == type }

    private fun findDescendant(box: Box, path: List<String>): Box? {
        var current: Box? = box
        for (type in path) {
            current = current?.let { findChild(it, type) }
            if (current == null) return null
        }
        return current
    }

    private fun findTopLevel(boxes: List<Box>, type: String): Box? = boxes.find { it.type == type }

    private fun handlerTypeForTrak(trak: Box): String? {
        val hdlr = findDescendant(trak, listOf("mdia", "hdlr"))
        if (hdlr == null || hdlr.offset + 20 > hdlr.end) return null
        return getBoxType(hdlr.data, hdlr.offset + 16)
    }

    private fun parseStsz(stsz: Box): List<Long> {
        val sampleSize = getUint32(stsz.view, stsz.offset + 12)
        val count = getUint32(stsz.view, stsz.offset + 16).toInt()

        if (sampleSize > 0) {
            return List(count) { sampleSize }
        }

        val tableStart = stsz.offset + 20
        if (tableStart + count * 4 > stsz.end) {
            throw Exception("MP4 invalido: stsz lebih kecil dari jumlah sampel.")
        }

        val sizes = mutableListOf<Long>()
        for (i in 0 until count) {
            sizes.add(getUint32(stsz.view, tableStart + i * 4))
        }
        return sizes
    }

    private fun parseStco(stco: Box): List<Long> {
        val count = getUint32(stco.view, stco.offset + 12).toInt()
        val tableStart = stco.offset + 16

        if (tableStart + count * 4 > stco.end) {
            throw Exception("MP4 invalido: stco lebih kecil dari jumlah chunk.")
        }

        val offsets = mutableListOf<Long>()
        for (i in 0 until count) {
            offsets.add(getUint32(stco.view, tableStart + i * 4))
        }
        return offsets
    }

    private fun parseStsc(stsc: Box): List<LongArray> {
        val count = getUint32(stsc.view, stsc.offset + 12).toInt()
        val tableStart = stsc.offset + 16

        if (tableStart + count * 12 > stsc.end) {
            throw Exception("MP4 invalido: stsc lebih kecil dari jumlah entry.")
        }

        val rows = mutableListOf<LongArray>()
        for (i in 0 until count) {
            val offset = tableStart + i * 12
            rows.add(longArrayOf(
                getUint32(stsc.view, offset),
                getUint32(stsc.view, offset + 4),
                getUint32(stsc.view, offset + 8)
            ))
        }
        return rows
    }

    private fun makeBox(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        val box = ByteArray(size)
        val view = ByteBuffer.wrap(box).order(ByteOrder.BIG_ENDIAN)
        setUint32(view, 0, size.toLong())
        setBoxType(box, 4, type)
        System.arraycopy(payload, 0, box, 8, payload.size)
        return box
    }

    private fun concatBytes(parts: List<ByteArray>): ByteArray {
        val total = parts.sumOf { it.size }
        val output = ByteArray(total)
        var offset = 0
        for (part in parts) {
            System.arraycopy(part, 0, output, offset, part.size)
            offset += part.size
        }
        return output
    }

    private fun boxBytes(box: Box): ByteArray {
        return box.data.copyOfRange(box.offset, box.end)
    }

    private fun boxPayload(box: Box): ByteArray {
        return box.data.copyOfRange(box.contentStart, box.end)
    }

    private fun buildMdhd(box: Box): ByteArray {
        val payload = boxPayload(box)
        val view = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val version = payload[0].toInt()

        if (version != 0) throw Exception("Versi mdhd tidak disupport: ${"$"}version")

        setUint32(view, 12, VIDEO_TIMESCALE.toLong())
        setUint32(view, 16, VIDEO_DURATION.toLong())
        return makeBox("mdhd", payload)
    }

    private fun buildElst(box: Box): ByteArray {
        val payload = boxPayload(box)
        val view = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val version = payload[0].toInt()
        val entryCount = getUint32(view, 4)

        if (version != 0 || entryCount < 1) throw Exception("elst version 0 butuh setidaknya satu entry.")

        setUint32(view, 12, VIDEO_EDIT_MEDIA_TIME.toLong())
        return makeBox("elst", payload)
    }

    private fun buildStts(realSampleCount: Int, fakeSampleCount: Int): ByteArray {
        val payload = ByteArray(4 + 4 + 8 + 8)
        val view = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)

        setUint32(view, 4, 2)
        setUint32(view, 8, realSampleCount.toLong())
        setUint32(view, 12, VIDEO_SAMPLE_DELTA.toLong())
        setUint32(view, 16, fakeSampleCount.toLong())
        setUint32(view, 20, VIDEO_SAMPLE_DELTA.toLong())

        return makeBox("stts", payload)
    }

    private fun buildStsz(originalSizes: List<Long>, fakeSampleCount: Int): ByteArray {
        val totalSamples = originalSizes.size + fakeSampleCount
        val payload = ByteArray(4 + 4 + 4 + totalSamples * 4)
        val view = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)

        setUint32(view, 8, totalSamples.toLong())

        var offset = 12
        for (size in originalSizes) {
            setUint32(view, offset, size)
            offset += 4
        }

        for (i in 0 until fakeSampleCount) {
            setUint32(view, offset, FAKE_SAMPLE_SIZE.toLong())
            offset += 4
        }

        return makeBox("stsz", payload)
    }

    private fun buildStsc(originalRows: List<LongArray>, originalChunkCount: Int): ByteArray {
        val rows = originalRows.map { it.clone() }.toMutableList()
        val lastRow = rows.lastOrNull()

        if (lastRow == null || lastRow[1] != 1L) {
            rows.add(longArrayOf(originalChunkCount + 1L, 1L, 1L))
        }

        val payload = ByteArray(4 + 4 + rows.size * 12)
        val view = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)

        setUint32(view, 4, rows.size.toLong())

        var offset = 8
        for (row in rows) {
            setUint32(view, offset, row[0])
            setUint32(view, offset + 4, row[1])
            setUint32(view, offset + 8, row[2])
            offset += 12
        }

        return makeBox("stsc", payload)
    }

    private fun buildStco(originalOffsets: List<Long>, delta: Int, fakeOffset: Long?, fakeSampleCount: Int): ByteArray {
        val count = originalOffsets.size + if (fakeOffset == null) 0 else fakeSampleCount
        val payload = ByteArray(4 + 4 + count * 4)
        val view = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)

        setUint32(view, 4, count.toLong())

        var tableOffset = 8
        for (offset in originalOffsets) {
            val shifted = offset + delta
            setUint32(view, tableOffset, shifted)
            tableOffset += 4
        }

        if (fakeOffset != null) {
            for (i in 0 until fakeSampleCount) {
                setUint32(view, tableOffset, fakeOffset)
                tableOffset += 4
            }
        }

        return makeBox("stco", payload)
    }

    private fun rebuildBox(box: Box, replacements: Map<Box, ByteArray>): ByteArray {
        if (replacements.containsKey(box)) {
            return replacements[box]!!
        }

        if (box.children.isEmpty()) {
            return boxBytes(box)
        }

        val parts = mutableListOf<ByteArray>()
        parts.add(box.data.copyOfRange(box.prefixStart, box.prefixEnd))
        for (child in box.children) {
            parts.add(rebuildBox(child, replacements))
        }

        return makeBox(box.type, concatBytes(parts))
    }

    private fun collectTrackStcoBoxes(moov: Box): List<Box> {
        val stcoBoxes = mutableListOf<Box>()

        moov.children.filter { it.type == "trak" }.forEach { trak ->
            val stbl = findDescendant(trak, listOf("mdia", "minf", "stbl")) ?: return@forEach
            val co64 = findChild(stbl, "co64")
            if (co64 != null) throw Exception("MP4 with co64 not supported by this method.")
            
            val stco = findChild(stbl, "stco")
            if (stco != null) stcoBoxes.add(stco)
        }
        return stcoBoxes
    }

    private fun buildStcoReplacements(
        stcoBoxes: List<Box>,
        videoStco: Box,
        delta: Int,
        fakeOffset: Long,
        fakeSampleCount: Int
    ): Map<Box, ByteArray> {
        val replacements = mutableMapOf<Box, ByteArray>()
        for (stco in stcoBoxes) {
            val fOffset = if (stco == videoStco) fakeOffset else null
            replacements[stco] = buildStco(parseStco(stco), delta, fOffset, fakeSampleCount)
        }
        return replacements
    }

    fun patchSharkSampleTableMethod(data: ByteArray): ByteArray {
        val view = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val topLevel = parseBoxes(data, view)

        val ftyp = findTopLevel(topLevel, "ftyp") ?: throw Exception("Caixa 'ftyp' tidak ada.")
        val moov = findTopLevel(topLevel, "moov") ?: throw Exception("Caixa 'moov' tidak ada.")
        val mdat = findTopLevel(topLevel, "mdat") ?: throw Exception("Caixa 'mdat' tidak ada.")

        val videoTrak = moov.children.find { it.type == "trak" && handlerTypeForTrak(it) == "vide" }
            ?: throw Exception("Track video tidak ditemukan.")

        val stbl = findDescendant(videoTrak, listOf("mdia", "minf", "stbl"))
        val mdhd = findDescendant(videoTrak, listOf("mdia", "mdhd"))
        val elst = findDescendant(videoTrak, listOf("edts", "elst"))
        val stts = stbl?.let { findChild(it, "stts") }
        val stsc = stbl?.let { findChild(it, "stsc") }
        val stsz = stbl?.let { findChild(it, "stsz") }
        val stco = stbl?.let { findChild(it, "stco") }

        if (stbl == null || mdhd == null || elst == null || stts == null || stsc == null || stsz == null || stco == null) {
            throw Exception("MP4 hilang struktur atom mdhd, elst, stts, stsc, stsz atau stco.")
        }

        val originalSizes = parseStsz(stsz)
        val realSampleCount = originalSizes.size
        val fakeSampleCount = realSampleCount * 9

        val originalStscRows = parseStsc(stsc)
        val originalChunkOffsets = parseStco(stco)
        val stcoBoxes = collectTrackStcoBoxes(moov)
        val preservedTopLevel = topLevel.filter { !listOf("ftyp", "moov", "mdat").contains(it.type) }.map { boxBytes(it) }

        val fixedReplacements = mutableMapOf(
            mdhd to buildMdhd(mdhd),
            elst to buildElst(elst),
            stts to buildStts(realSampleCount, fakeSampleCount),
            stsc to buildStsc(originalStscRows, originalChunkOffsets.size),
            stsz to buildStsz(originalSizes, fakeSampleCount)
        )

        val placeholderReplacements = fixedReplacements.toMutableMap()
        placeholderReplacements.putAll(buildStcoReplacements(stcoBoxes, stco, 0, 0L, fakeSampleCount))

        val moovPlaceholder = rebuildBox(moov, placeholderReplacements)
        val preservedBytes = concatBytes(preservedTopLevel)
        val oldMdatPayloadStart = mdat.contentStart
        val oldMdatPayload = data.copyOfRange(mdat.contentStart, mdat.end)
        
        val newMdatPayloadStart = ftyp.size + moovPlaceholder.size + preservedBytes.size + 8
        var delta = (newMdatPayloadStart - oldMdatPayloadStart).toInt()
        var fakeOffset = newMdatPayloadStart.toLong() + oldMdatPayload.size.toLong()

        var finalReplacements = fixedReplacements.toMutableMap()
        finalReplacements.putAll(buildStcoReplacements(stcoBoxes, stco, delta, fakeOffset, fakeSampleCount))

        var moovNew = rebuildBox(moov, finalReplacements)
        val recalculatedMdatPayloadStart = ftyp.size + moovNew.size + preservedBytes.size + 8
        delta = (recalculatedMdatPayloadStart - oldMdatPayloadStart).toInt()
        fakeOffset = recalculatedMdatPayloadStart.toLong() + oldMdatPayload.size.toLong()

        finalReplacements = fixedReplacements.toMutableMap()
        finalReplacements.putAll(buildStcoReplacements(stcoBoxes, stco, delta, fakeOffset, fakeSampleCount))

        moovNew = rebuildBox(moov, finalReplacements)
        
        val mdatPayloadNew = concatBytes(listOf(oldMdatPayload, FAKE_SAMPLE_BYTES))
        val mdatNew = makeBox("mdat", mdatPayloadNew)
        
        return concatBytes(listOf(boxBytes(ftyp), moovNew, preservedBytes, mdatNew))
    }
}
