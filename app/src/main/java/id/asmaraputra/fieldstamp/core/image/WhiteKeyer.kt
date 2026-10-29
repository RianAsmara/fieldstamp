package id.asmaraputra.fieldstamp.core.image

/**
 * Turns the photo window of a non-transparent frame (e.g. a JPG exported from free Canva) into transparency.
 *
 * Only the **largest connected near-white region** is cleared. Other white areas such as text, logos and thin
 * borders stay opaque, so a frame with white lettering is not damaged. Pixels just outside the region fade by
 * brightness to hide JPEG noise and anti-aliased edges.
 *
 * Works on ARGB color ints (unpremultiplied), so it is plain Kotlin and unit-testable.
 */
object WhiteKeyer {
    /** Minimum channel value counted as "white". JPEG noise keeps true white at roughly 245-255. */
    const val WHITE_THRESHOLD = 232
    /** Below this, edge pixels stay fully opaque. */
    const val EDGE_FLOOR = 170
    /** The window must cover at least this share of the image to be treated as the photo area. */
    const val MIN_REGION_FRACTION = 0.05f

    data class Result(val pixels: IntArray, val clearedFraction: Float)

    /** @return keyed pixels, or null when no white area is large enough. */
    fun key(pixels: IntArray, width: Int, height: Int): Result? {
        require(pixels.size == width * height)
        val size = pixels.size
        val white = BooleanArray(size) { isWhite(pixels[it]) }

        // Label 4-connected white components with an explicit stack (no recursion), keep the largest.
        val label = IntArray(size)
        val stack = IntArray(size)
        var bestLabel = 0
        var bestCount = 0
        var next = 1
        for (start in 0 until size) {
            if (!white[start] || label[start] != 0) continue
            val id = next++
            var count = 0
            var top = 0
            stack[top++] = start
            label[start] = id
            while (top > 0) {
                val p = stack[--top]
                count++
                val x = p % width
                val y = p / width
                if (x > 0) { val q = p - 1; if (white[q] && label[q] == 0) { label[q] = id; stack[top++] = q } }
                if (x < width - 1) { val q = p + 1; if (white[q] && label[q] == 0) { label[q] = id; stack[top++] = q } }
                if (y > 0) { val q = p - width; if (white[q] && label[q] == 0) { label[q] = id; stack[top++] = q } }
                if (y < height - 1) { val q = p + width; if (white[q] && label[q] == 0) { label[q] = id; stack[top++] = q } }
            }
            if (count > bestCount) { bestCount = count; bestLabel = id }
        }
        if (bestLabel == 0 || bestCount < size * MIN_REGION_FRACTION) return null

        val out = pixels.copyOf()
        for (p in 0 until size) {
            if (label[p] == bestLabel) {
                out[p] = 0x00FFFFFF
                continue
            }
            // Soft edge: neighbours of the cleared region become partly transparent by brightness.
            val x = p % width
            val y = p / width
            val touches = (x > 0 && label[p - 1] == bestLabel) || (x < width - 1 && label[p + 1] == bestLabel) ||
                (y > 0 && label[p - width] == bestLabel) || (y < height - 1 && label[p + width] == bestLabel)
            if (touches) {
                val m = minChannel(pixels[p])
                if (m > EDGE_FLOOR) {
                    val alpha = (255 * (WHITE_THRESHOLD - m).coerceAtLeast(0) / (WHITE_THRESHOLD - EDGE_FLOOR)).coerceIn(0, 255)
                    out[p] = (alpha shl 24) or (pixels[p] and 0x00FFFFFF)
                }
            }
        }
        return Result(out, bestCount.toFloat() / size)
    }

    private fun minChannel(c: Int): Int = minOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)

    private fun isWhite(c: Int): Boolean = (c ushr 24) > 200 && minChannel(c) >= WHITE_THRESHOLD
}
