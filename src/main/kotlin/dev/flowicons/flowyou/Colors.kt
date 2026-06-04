package dev.flowicons.flowyou

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Color math ported verbatim from the upstream VSCode extension's `colors.js`
 * (via the Zed port's `update-icons.cjs`). Used by [FlowYouBuilder] to
 * auto-derive a light-mode palette from a dark-mode one.
 */
object Colors {

    fun hexToRgb(hex: String): IntArray {
        var h = hex.removePrefix("#")
        if (h.length == 3) {
            h = h.map { "$it$it" }.joinToString("")
        }
        val num = h.toLong(16).toInt()
        return intArrayOf((num shr 16) and 255, (num shr 8) and 255, num and 255)
    }

    /** @return [h(0..360), s(0..100), l(0..100)] */
    fun rgbToHsl(rIn: Int, gIn: Int, bIn: Int): DoubleArray {
        val r = rIn / 255.0
        val g = gIn / 255.0
        val b = bIn / 255.0
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        val l = (mx + mn) / 2
        var h: Double
        val s: Double
        if (mx == mn) {
            h = 0.0
            s = 0.0
        } else {
            val d = mx - mn
            s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
            h = when (mx) {
                r -> (g - b) / d + (if (g < b) 6 else 0)
                g -> (b - r) / d + 2
                else -> (r - g) / d + 4
            }
            h /= 6
        }
        return doubleArrayOf(h * 360, s * 100, l * 100)
    }

    fun hslToRgb(hIn: Double, sIn: Double, lIn: Double): IntArray {
        val h = hIn / 360
        val s = sIn / 100
        val l = lIn / 100
        val r: Double
        val g: Double
        val b: Double
        if (s == 0.0) {
            r = l; g = l; b = l
        } else {
            val q = if (l < 0.5) l * (1 + s) else l + s - l * s
            val p = 2 * l - q
            r = hue2rgb(p, q, h + 1.0 / 3)
            g = hue2rgb(p, q, h)
            b = hue2rgb(p, q, h - 1.0 / 3)
        }
        return intArrayOf((r * 255).roundToInt(), (g * 255).roundToInt(), (b * 255).roundToInt())
    }

    private fun hue2rgb(p: Double, q: Double, tIn: Double): Double {
        var t = tIn
        if (t < 0) t += 1
        if (t > 1) t -= 1
        if (t < 1.0 / 6) return p + (q - p) * 6 * t
        if (t < 1.0 / 2) return q
        if (t < 2.0 / 3) return p + (q - p) * (2.0 / 3 - t) * 6
        return p
    }

    fun rgbToHex(r: Int, g: Int, b: Int): String =
        "#" + listOf(r, g, b).joinToString("") { it.toString(16).padStart(2, '0') }.lowercase()

    /** Auto-derive a light-mode color from a dark-mode color (extension heuristic). */
    fun darken(hex: String): String {
        val (r, g, b) = hexToRgb(hex).toList()
        val (h, s0, l0) = rgbToHsl(r, g, b).toList()
        val s = max(0.0, min(100.0, s0 + (if (s0 > 90) 1 else 8)))
        val l = max(0.0, min(100.0, l0 - 8))
        val rgb = hslToRgb(h, s, l)
        return rgbToHex(rgb[0], rgb[1], rgb[2])
    }

    fun invert(hex: String): String {
        val (r, g, b) = hexToRgb(hex).toList()
        return rgbToHex(255 - r, 255 - g, 255 - b)
    }
}
