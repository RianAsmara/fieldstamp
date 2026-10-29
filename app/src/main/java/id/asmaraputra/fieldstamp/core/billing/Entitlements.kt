package id.asmaraputra.fieldstamp.core.billing

import id.asmaraputra.fieldstamp.core.settings.EntitlementCache

/** Features gated behind Pro. Everything not listed here is free. */
enum class ProFeature {
    /** More than [EntitlementPolicy.FREE_TEMPLATE_LIMIT] imported PNG templates. */
    UNLIMITED_TEMPLATES,
    /** Organization name and logo on the report cover. */
    REPORT_BRANDING,
    /** Large (1 per row) and compact (3 per row) report layouts. */
    REPORT_LAYOUTS,
    /** Export a report's photos as a ZIP organized by date. */
    ZIP_EXPORT,
}

enum class EntitlementSource { NONE, PLAY_VERIFIED, CACHED, DEBUG_OVERRIDE }

data class Entitlement(
    val isPro: Boolean,
    val source: EntitlementSource,
    val lastVerifiedAt: Long?,
) {
    fun allows(feature: ProFeature): Boolean = isPro

    companion object {
        val FREE = Entitlement(false, EntitlementSource.NONE, null)
    }
}

/**
 * Free-tier rules. Downgrading never removes access to existing data: photos, reports, PDFs and already
 * imported templates stay viewable, exportable, and deletable; only creating *new* Pro-only things is gated.
 */
object EntitlementPolicy {
    const val FREE_TEMPLATE_LIMIT = 1

    /** How long a Play-confirmed purchase is honored without reconnecting to Play. */
    const val OFFLINE_GRACE_MILLIS = 30L * 24 * 60 * 60 * 1000

    fun canImportTemplate(existingCount: Int, entitlement: Entitlement): Boolean =
        entitlement.isPro || existingCount < FREE_TEMPLATE_LIMIT

    /**
     * A cached purchase is trusted only while it is recent. The cache records what Play last reported;
     * it is not proof on its own, so it expires and must be re-confirmed by Play.
     */
    fun resolve(cache: EntitlementCache?, now: Long, debugOverride: Boolean): Entitlement {
        if (debugOverride) return Entitlement(true, EntitlementSource.DEBUG_OVERRIDE, cache?.lastVerifiedAt)
        if (cache == null || !cache.isPro) return Entitlement(false, EntitlementSource.NONE, cache?.lastVerifiedAt)
        val age = now - cache.lastVerifiedAt
        return if (age in 0..OFFLINE_GRACE_MILLIS) {
            Entitlement(true, EntitlementSource.CACHED, cache.lastVerifiedAt)
        } else {
            Entitlement(false, EntitlementSource.NONE, cache.lastVerifiedAt)
        }
    }
}
