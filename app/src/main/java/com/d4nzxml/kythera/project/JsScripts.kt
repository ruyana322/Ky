package com.d4nzxml.kythera.project

/**
 * Teks JavaScript yang dipakai app:
 * - [PRELUDE]: API scene (scene, text, rect, circle, image, audio, camera, preset) yang dijalankan sebelum skrip pengguna.
 * - [STARTER]: skrip contoh untuk project baru.
 * - [AI_GUIDE]: panduan yang bisa disalin dan ditempel ke AI agar skrip yang dihasilkan cocok dengan API ini.
 *
 * Catatan: sengaja tanpa karakter dolar dan tanpa komentar satu baris di PRELUDE, supaya aman
 * saat digabung menjadi satu string.
 */
object JsScripts {

    val PRELUDE: String = """
(function (g) {
  "use strict";
  var EASINGS = {
    linear: "LINEAR", easeInQuad: "EASE_IN_QUAD", easeOutQuad: "EASE_OUT_QUAD",
    easeInOutQuad: "EASE_IN_OUT_QUAD", easeOutBack: "EASE_OUT_BACK", easeOutBounce: "EASE_OUT_BOUNCE"
  };
  var PRESETS = {
    fadeIn: "FADE_IN", fadeOut: "FADE_OUT", slideIn: "SLIDE_IN", slideOut: "SLIDE_OUT",
    zoomIn: "ZOOM_IN", zoomOut: "ZOOM_OUT", shake: "SHAKE", bounce: "BOUNCE", rotate: "ROTATE", idle: "IDLE"
  };
  var KINDS = { narration: "NARRATION", music: "MUSIC", sfx: "SFX" };
  var S = null;

  function sec(v, label) {
    var n = Number(v);
    if (v === null || v === undefined || v === "" || !isFinite(n)) {
      throw new Error((label || "Waktu") + " harus berupa angka (detik)");
    }
    return Math.round(n * 1000);
  }
  function fin(v, label) {
    var n = Number(v);
    if (v === null || v === undefined || v === "" || !isFinite(n)) {
      throw new Error((label || "Nilai") + " harus berupa angka");
    }
    return n;
  }
  function num(o, k, d) {
    var v = o ? o[k] : undefined;
    if (v === undefined || v === null) { return d; }
    var n = Number(v);
    if (!isFinite(n)) { throw new Error("Opsi '" + k + "' harus berupa angka"); }
    return n;
  }
  function str(o, k, d) {
    var v = o ? o[k] : undefined;
    return (v === undefined || v === null) ? d : String(v);
  }
  function ease(name) {
    if (name === undefined || name === null) { return "LINEAR"; }
    if (EASINGS[name]) { return EASINGS[name]; }
    var up = String(name).toUpperCase();
    for (var k in EASINGS) { if (EASINGS[k] === up) { return up; } }
    throw new Error("Easing tidak dikenal: " + name);
  }
  function need() {
    if (!S) { throw new Error("Panggil scene({...}) lebih dulu sebelum menambah objek"); }
    return S;
  }

  function Obj(d) { this._d = d; }
  Object.keys(PRESETS).forEach(function (m) {
    Obj.prototype[m] = function (start, duration, direction) {
      this._d.presets.push({
        name: PRESETS[m],
        start: sec(start, "Waktu mulai " + m),
        duration: sec(duration, "Durasi " + m),
        direction: String(direction || "left").toUpperCase()
      });
      return this;
    };
  });
  Obj.prototype.keyframe = function (prop, time, value, easing) {
    var p = String(prop).toUpperCase();
    var list = this._d.kf[p] || (this._d.kf[p] = []);
    list.push({ t: sec(time, "Waktu keyframe"), v: fin(value, "Nilai keyframe"), e: ease(easing) });
    return this;
  };

  function make(type, o, extra) {
    var s = need();
    o = o || {};
    var d = {
      id: (o.id !== undefined && o.id !== null) ? String(o.id) : "o" + (s.nextId++),
      type: type,
      x: num(o, "x", 0.5), y: num(o, "y", 0.5),
      scale: num(o, "scale", 1), rotation: num(o, "rotation", 0), opacity: num(o, "opacity", 1),
      zIndex: Math.round(num(o, "z", 0)),
      color: str(o, "color", "#FFFFFFFF"),
      presets: [], kf: {}
    };
    for (var k in extra) { d[k] = extra[k]; }
    s.objects.push(d);
    return new Obj(d);
  }

  var api = {
    text: function (value, o) {
      o = o || {};
      return make("TEXT", o, { text: String(value), textSize: num(o, "size", 0.08) });
    },
    rect: function (o) {
      o = o || {};
      var w = num(o, "w", 0.2);
      return make("RECT", o, { width: w, height: num(o, "h", w) });
    },
    circle: function (o) {
      o = o || {};
      var d = num(o, "d", num(o, "w", 0.2));
      return make("CIRCLE", o, { width: d, height: d });
    },
    image: function (src, o) {
      o = o || {};
      var w = num(o, "w", 0.25);
      return make("IMAGE", o, { src: String(src), width: w, height: num(o, "h", w) });
    },
    audio: function (src, o) {
      var s = need();
      o = o || {};
      var kind = KINDS[String(str(o, "kind", "music")).toLowerCase()];
      if (!kind) { throw new Error("kind audio harus narration, music, atau sfx"); }
      var clip = {
        id: str(o, "id", "a" + (s.audio.length + 1)),
        src: String(src),
        startMs: sec(num(o, "start", 0), "start audio"),
        volume: num(o, "volume", 1),
        fadeInMs: sec(num(o, "fadeIn", 0), "fadeIn audio"),
        fadeOutMs: sec(num(o, "fadeOut", 0), "fadeOut audio"),
        loop: o.loop === true,
        kind: kind
      };
      if (o.end !== undefined && o.end !== null) { clip.endMs = sec(o.end, "end audio"); }
      s.audio.push(clip);
      return api;
    },
    camera: {
      zoom: function (start, end, from, to, easing) {
        var s = need();
        var list = s.cam.ZOOM || (s.cam.ZOOM = []);
        list.push({ t: sec(start), v: fin(from, "Zoom awal"), e: ease(easing) });
        list.push({ t: sec(end), v: fin(to, "Zoom akhir"), e: "LINEAR" });
      },
      pan: function (start, end, fromX, fromY, toX, toY, easing) {
        var s = need();
        var lx = s.cam.PAN_X || (s.cam.PAN_X = []);
        var ly = s.cam.PAN_Y || (s.cam.PAN_Y = []);
        lx.push({ t: sec(start), v: fin(fromX, "Pan X awal"), e: ease(easing) });
        lx.push({ t: sec(end), v: fin(toX, "Pan X akhir"), e: "LINEAR" });
        ly.push({ t: sec(start), v: fin(fromY, "Pan Y awal"), e: ease(easing) });
        ly.push({ t: sec(end), v: fin(toY, "Pan Y akhir"), e: "LINEAR" });
      }
    },
    duration: function (seconds) { need().durationMs = sec(seconds, "Durasi scene"); return api; },
    background: function (color) { need().background = String(color); return api; }
  };

  g.scene = function (opts) {
    opts = opts || {};
    S = {
      durationMs: sec(num(opts, "duration", 6), "Durasi scene"),
      background: str(opts, "background", "#000000"),
      objects: [], audio: [], cam: {}, nextId: 1
    };
    return api;
  };
  ["text", "rect", "circle", "image", "audio", "duration", "background"].forEach(function (k) {
    g.scene[k] = api[k];
  });
  g.scene.camera = api.camera;

  function tracks(map) {
    return Object.keys(map).map(function (p) { return { prop: p, frames: map[p] }; });
  }
  g.__jsvs = {
    build: function () {
      var s = need();
      return {
        format: "jsvideostudio.scene", version: 1,
        scene: {
          id: "script", durationMs: s.durationMs, background: s.background,
          objects: s.objects.map(function (d) {
            return {
              id: d.id, type: d.type, x: d.x, y: d.y, width: d.width, height: d.height,
              scale: d.scale, rotation: d.rotation, opacity: d.opacity, zIndex: d.zIndex,
              color: d.color, text: d.text, textSize: d.textSize, src: d.src,
              keyframes: tracks(d.kf), presets: d.presets
            };
          }),
          camera: tracks(s.cam),
          audio: s.audio
        }
      };
    }
  };
})(window)
""".trimIndent()

    val STARTER: String = """
// JS Video Studio: skrip scene.
// Waktu dalam DETIK. Posisi x,y: 0..1 (0.5 = tengah). Ukuran w,h,d,size: pecahan dari LEBAR canvas.
const s = scene({ duration: 6, background: "#151A2B" });

s.text("JS Video Studio", { x: 0.5, y: 0.2, size: 0.09, color: "#F8FAFC", z: 3 })
  .zoomIn(0, 0.7)
  .idle(0.7, 5.3);

s.text("Dibuat dari skrip JS", { x: 0.5, y: 0.28, size: 0.04, color: "#94A3B8", z: 3 })
  .fadeIn(1.2, 0.6);

s.rect({ x: 0.5, y: 0.52, w: 0.28, h: 0.28, color: "#8B5CF6", z: 1 })
  .slideIn(0.3, 0.9, "left")
  .rotate(1.5, 2);

s.circle({ x: 0.5, y: 0.8, d: 0.18, color: "#06B6D4", z: 2 })
  .fadeIn(0.8, 0.2)
  .bounce(0.8, 1)
  .shake(3.2, 0.7)
  .fadeOut(5, 0.8);

s.camera.zoom(0, 6, 1, 1.12, "easeInOutQuad");

// Menambah gambar dari aset (hapus tanda // di depan baris untuk mengaktifkan):
// s.image("assets/images/gambarku.png", { x: 0.5, y: 0.5, w: 0.3 }).zoomIn(0, 0.6);
// Menambah audio dari aset:
// s.audio("assets/audio/musik.mp3", { start: 0, volume: 0.6, fadeIn: 1, fadeOut: 2, kind: "music" });
""".trimIndent()

    private val GUIDE_HEAD: String = """
Tulis skrip JavaScript untuk app JS Video Studio (di Kythera). Skrip HANYA boleh memakai API di bawah:
tanpa import, tanpa DOM/canvas, tanpa fetch, tanpa setTimeout/requestAnimationFrame. Hasilnya satu scene.

ATURAN
- Waktu dalam DETIK.
- Posisi x,y: 0..1 (0.5 = tengah). Ukuran w,h,d,size: pecahan dari LEBAR canvas (0.2 = 20% lebar).
- Warna: "#RRGGBB" atau "#AARRGGBB".
- Mulai dengan: const s = scene({ duration: 10, background: "#101820" });
- Rantai preset pada objek: s.text("Halo", { x: 0.5, y: 0.3 }).fadeIn(0, 0.5).idle(0.5, 9);
- Aset hanya lewat path: assets/icons/nama.png, assets/images/nama.png, assets/audio/nama.mp3
  (pakai nama file yang saya sebutkan, jangan mengarang file lain).

API
s.text("teks", { x, y, size, color, z, opacity, scale, rotation })
s.rect({ x, y, w, h, color, z })
s.circle({ x, y, d, color, z })
s.image("assets/images/a.png", { x, y, w, h, z })          // h opsional (default = w)
s.audio("assets/audio/a.mp3", { start, end, volume, fadeIn, fadeOut, loop, kind })
   // volume 0..2, kind: "narration" | "music" | "sfx", end opsional
s.camera.zoom(mulai, selesai, dari, ke, easing)
s.camera.pan(mulai, selesai, dariX, dariY, keX, keY, easing)
Preset (dirantai pada objek; argumen: waktuMulai, durasi):
  .fadeIn() .fadeOut() .slideIn(mulai, durasi, "left"|"right"|"top"|"bottom") .slideOut(...)
  .zoomIn() .zoomOut() .shake() .bounce() .rotate() .idle()
Keyframe manual: .keyframe("x"|"y"|"scale"|"rotation"|"opacity", waktu, nilai, easing)
Easing: "linear" "easeInQuad" "easeOutQuad" "easeInOutQuad" "easeOutBack" "easeOutBounce"
Batas: maks 500 objek, maks 50 audio, durasi scene 0.1 sampai 3600 detik.

CONTOH SKRIP YANG BENAR
""".trimIndent()

    private val GUIDE_TAIL: String = """

BALAS DENGAN SATU BLOK KODE JavaScript SAJA, tanpa penjelasan.

PERMINTAAN SAYA:
(tulis ide animasi di sini)
""".trimIndent()

    val AI_GUIDE: String = GUIDE_HEAD + "\n" + STARTER + "\n" + GUIDE_TAIL
}
