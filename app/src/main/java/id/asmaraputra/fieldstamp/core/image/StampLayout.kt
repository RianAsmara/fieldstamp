package id.asmaraputra.fieldstamp.core.image

import id.asmaraputra.fieldstamp.core.model.Formats
import id.asmaraputra.fieldstamp.core.model.CaptureTime
import id.asmaraputra.fieldstamp.core.model.GeoPoint
import id.asmaraputra.fieldstamp.core.model.StampField
import id.asmaraputra.fieldstamp.core.model.TemplateConfig
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Values available to the stamp. Absent values are omitted from the output, never substituted. */
data class StampData(
    val captureTime: CaptureTime,
    val projectName: String?,
    val activityName: String?,
    val caption: String,
    val category: String?,
    val location: GeoPoint?,
    val locationName: String? = null,
)

data class StampLabels(
    val project: String,
    val activity: String,
    val category: String,
    val coordinates: String,
    val place: String,
)

data class StampLine(val field: StampField, val text: String, val emphasized: Boolean)

data class IntSize(val width: Int, val height: Int) {
    val longEdge: Int get() = max(width, height)
    val shortEdge: Int get() = min(width, height)
}

data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

object StampLayout {
    /** Long-edge cap for rendered output; keeps memory bounded and files shareable. */
    const val DEFAULT_MAX_LONG_EDGE = 2560

    fun lines(config: TemplateConfig, data: StampData, labels: StampLabels): List<StampLine> =
        config.normalized().enabledFields.mapNotNull { field ->
            when (field) {
                StampField.DATE_TIME -> StampLine(field, Formats.stampDateTime(data.captureTime, config.use24Hour), true)
                StampField.PROJECT -> data.projectName?.takeIf { it.isNotBlank() }?.let { StampLine(field, "${labels.project}: $it", false) }
                StampField.ACTIVITY -> data.activityName?.takeIf { it.isNotBlank() }?.let { StampLine(field, "${labels.activity}: $it", false) }
                StampField.CAPTION -> data.caption.trim().takeIf { it.isNotEmpty() }?.let { StampLine(field, it, false) }
                StampField.CATEGORY -> data.category?.takeIf { it.isNotBlank() }?.let { StampLine(field, "${labels.category}: $it", false) }
                StampField.LOCATION_NAME -> data.locationName?.trim()?.takeIf { it.isNotEmpty() }?.let { StampLine(field, "${labels.place}: $it", false) }
                StampField.COORDINATES -> data.location?.let { StampLine(field, "${labels.coordinates}: ${Formats.coordinatesWithAccuracy(it)}", false) }
                StampField.CUSTOM_TEXT -> config.customText.trim().takeIf { it.isNotEmpty() }?.let { StampLine(field, it, false) }
            }
        }

    /**
     * Output canvas. With a template the canvas takes the template's aspect ratio (the frame the user
     * designed); without one it keeps the photo's own aspect ratio. The long edge never exceeds [maxLongEdge]
     * and is never upscaled beyond the larger of the photo and template sources.
     */
    fun canvasSize(photo: IntSize, template: IntSize?, maxLongEdge: Int = DEFAULT_MAX_LONG_EDGE): IntSize {
        val aspectSource = template ?: photo
        val naturalLong = max(photo.longEdge, template?.longEdge ?: 0)
        val targetLong = min(maxLongEdge, naturalLong).coerceAtLeast(1)
        return if (aspectSource.width >= aspectSource.height) {
            IntSize(targetLong, (targetLong.toDouble() * aspectSource.height / aspectSource.width).roundToInt().coerceAtLeast(1))
        } else {
            IntSize((targetLong.toDouble() * aspectSource.width / aspectSource.height).roundToInt().coerceAtLeast(1), targetLong)
        }
    }

    /** Centered crop of [source] that matches [target]'s aspect ratio (cover fit, no stretching). */
    fun centerCrop(source: IntSize, target: IntSize): IntRect {
        val sourceAspect = source.width.toDouble() / source.height
        val targetAspect = target.width.toDouble() / target.height
        return if (sourceAspect > targetAspect) {
            val w = (source.height * targetAspect).roundToInt().coerceIn(1, source.width)
            val left = (source.width - w) / 2
            IntRect(left, 0, left + w, source.height)
        } else {
            val h = (source.width / targetAspect).roundToInt().coerceIn(1, source.height)
            val top = (source.height - h) / 2
            IntRect(0, top, source.width, top + h)
        }
    }

    /** Largest rect with [content]'s aspect ratio that fits inside [box] (contain fit, centered). */
    fun fitInside(content: IntSize, boxWidth: Float, boxHeight: Float): Pair<Float, Float> {
        val scale = min(boxWidth / content.width, boxHeight / content.height)
        return content.width * scale to content.height * scale
    }

    /** Pixel rectangle of a custom text box on a canvas. */
    fun boxRect(canvas: IntSize, box: id.asmaraputra.fieldstamp.core.model.StampBox): FloatArray {
        val b = box.normalized()
        val l = b.left * canvas.width
        val t = b.top * canvas.height
        return floatArrayOf(l, t, l + b.width * canvas.width, t + b.height * canvas.height)
    }

    /**
     * Largest text size ≤ [start] (down to [min]) whose laid-out height fits [maxHeight]. [heightAt] measures the
     * wrapped block at a given size. Returns [min] when even that does not fit; the caller then clips lines.
     */
    fun fitTextSize(start: Float, min: Float, maxHeight: Float, heightAt: (Float) -> Float): Float {
        var size = max(start, min)
        while (size > min && heightAt(size) > maxHeight) size = max(min, size * 0.92f)
        return size
    }

    /** Power-of-two sample size that still yields at least [minWidth] x [minHeight]. */
    fun sampleSize(source: IntSize, minWidth: Int, minHeight: Int): Int {
        var sample = 1
        while (source.width / (sample * 2) >= minWidth && source.height / (sample * 2) >= minHeight) sample *= 2
        return sample
    }
}
