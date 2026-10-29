package id.asmaraputra.fieldstamp.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Dynamic fields FieldStamp can draw onto a photo. Text baked into an imported PNG is never edited;
 * only these fields are rendered, in a single text block.
 */
@Serializable
enum class StampField {
    DATE_TIME,
    PROJECT,
    ACTIVITY,
    CAPTION,
    CATEGORY,
    COORDINATES,
    LOCATION_NAME,
    CUSTOM_TEXT,
}

@Serializable
enum class StampAnchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT, CUSTOM }

/** Text area for [StampAnchor.CUSTOM], as fractions of the output (0..1, origin top-left). */
@Serializable
data class StampBox(
    val left: Float = 0.25f,
    val top: Float = 0.74f,
    val width: Float = 0.5f,
    val height: Float = 0.2f,
) {
    fun normalized(): StampBox {
        val l = left.coerceIn(0f, 0.95f)
        val t = top.coerceIn(0f, 0.95f)
        return StampBox(l, t, width.coerceIn(0.05f, 1f - l), height.coerceIn(0.03f, 1f - t))
    }
}

/** Panel behind the stamp text. SOLID hides whatever the template prints in that area (e.g. placeholder text). */
@Serializable
enum class BackdropStyle { SHADE, SOLID, NONE }

@Serializable
enum class StampTextColor { WHITE, BLACK }

@Serializable
data class StampFieldSetting(
    val field: StampField,
    val enabled: Boolean,
)

@Serializable
data class TemplateConfig(
    val fields: List<StampFieldSetting> = DEFAULT_FIELDS,
    val anchor: StampAnchor = StampAnchor.BOTTOM_LEFT,
    /** Text size as a percentage of the output's shorter edge. */
    val textSizePercent: Float = 3.2f,
    /** Inset from the edges as a percentage of the output's shorter edge. */
    val marginPercent: Float = 4f,
    val textColor: StampTextColor = StampTextColor.WHITE,
    /** Draw a translucent panel behind the text for legibility on busy photos. */
    val backdrop: Boolean = true,
    /** Null in configs saved before v1.1; then [backdrop] decides between SHADE and NONE. */
    val backdropStyle: BackdropStyle? = null,
    /** Opaque ARGB fill for [BackdropStyle.SOLID]. */
    val solidColor: Int = 0xFFFFFFFF.toInt(),
    val box: StampBox = StampBox(),
    val customText: String = "",
    val use24Hour: Boolean = true,
) {
    val enabledFields: List<StampField> get() = fields.filter { it.enabled }.map { it.field }

    val effectiveBackdrop: BackdropStyle get() = backdropStyle ?: if (backdrop) BackdropStyle.SHADE else BackdropStyle.NONE

    fun normalized(): TemplateConfig {
        // Keep every known field exactly once, preserving the user's order, so configs saved by
        // older versions pick up new fields as disabled entries.
        val seen = fields.distinctBy { it.field }
        val missing = StampField.entries.filter { f -> seen.none { it.field == f } }.map { StampFieldSetting(it, false) }
        return copy(
            fields = seen + missing,
            textSizePercent = textSizePercent.coerceIn(MIN_TEXT_PERCENT, MAX_TEXT_PERCENT),
            marginPercent = marginPercent.coerceIn(0f, 15f),
            box = box.normalized(),
            solidColor = solidColor or 0xFF000000.toInt(),
        )
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val MIN_TEXT_PERCENT = 1.8f
        const val MAX_TEXT_PERCENT = 6f

        val DEFAULT_FIELDS = listOf(
            StampFieldSetting(StampField.DATE_TIME, true),
            StampFieldSetting(StampField.LOCATION_NAME, true),
            StampFieldSetting(StampField.COORDINATES, true),
            StampFieldSetting(StampField.PROJECT, true),
            StampFieldSetting(StampField.ACTIVITY, true),
            StampFieldSetting(StampField.CAPTION, true),
            StampFieldSetting(StampField.CATEGORY, false),
            StampFieldSetting(StampField.CUSTOM_TEXT, false),
        )

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(value: String?): TemplateConfig =
            if (value.isNullOrBlank()) TemplateConfig()
            else runCatching { json.decodeFromString(serializer(), value).normalized() }.getOrDefault(TemplateConfig())
    }
}
