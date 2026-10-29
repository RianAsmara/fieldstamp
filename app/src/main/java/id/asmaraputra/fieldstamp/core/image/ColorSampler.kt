package id.asmaraputra.fieldstamp.core.image

/** Finds the dominant opaque color of a region, e.g. the fill of a template's info panel. */
object ColorSampler {
    /**
     * Colors are bucketed at 4 bits per channel so JPEG noise and anti-aliasing do not split the vote; the result is
     * the average of the winning bucket. Transparent and semi-transparent pixels are ignored.
     * @return opaque ARGB, or null when the region has no opaque pixels.
     */
    fun dominantOpaqueColor(pixels: IntArray): Int? {
        val counts = HashMap<Int, IntArray>() // bucket -> [count, sumR, sumG, sumB]
        for (c in pixels) {
            if ((c ushr 24) < 230) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val acc = counts.getOrPut(key) { IntArray(4) }
            acc[0]++; acc[1] += r; acc[2] += g; acc[3] += b
        }
        val best = counts.values.maxByOrNull { it[0] } ?: return null
        val n = best[0]
        return (0xFF shl 24) or ((best[1] / n) shl 16) or ((best[2] / n) shl 8) or (best[3] / n)
    }
}
