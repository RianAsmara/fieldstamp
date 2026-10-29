package id.asmaraputra.fieldstamp.core.billing

import id.asmaraputra.fieldstamp.core.settings.EntitlementCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementPolicyTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test fun noCacheMeansFree() {
        assertFalse(EntitlementPolicy.resolve(null, now, false).isPro)
    }

    @Test fun recentPlayConfirmationIsHonoredOffline() {
        val e = EntitlementPolicy.resolve(EntitlementCache(true, now - 5 * day, "hash"), now, false)
        assertTrue(e.isPro)
        assertEquals(EntitlementSource.CACHED, e.source)
    }

    @Test fun staleCacheExpires() {
        val e = EntitlementPolicy.resolve(EntitlementCache(true, now - 31 * day, "hash"), now, false)
        assertFalse(e.isPro)
    }

    @Test fun cacheFromTheFutureIsNotTrusted() {
        assertFalse(EntitlementPolicy.resolve(EntitlementCache(true, now + day, "hash"), now, false).isPro)
    }

    @Test fun revocationRecordedByPlayIsRespected() {
        assertFalse(EntitlementPolicy.resolve(EntitlementCache(false, now, null), now, false).isPro)
    }

    @Test fun debugOverrideGrantsPro() {
        assertEquals(EntitlementSource.DEBUG_OVERRIDE, EntitlementPolicy.resolve(null, now, true).source)
    }

    @Test fun freeTierAllowsOneTemplate() {
        assertTrue(EntitlementPolicy.canImportTemplate(0, Entitlement.FREE))
        assertFalse(EntitlementPolicy.canImportTemplate(1, Entitlement.FREE))
        assertTrue(EntitlementPolicy.canImportTemplate(25, Entitlement(true, EntitlementSource.PLAY_VERIFIED, now)))
    }

    @Test fun proFeaturesAreGatedOnlyByEntitlement() {
        ProFeature.entries.forEach {
            assertFalse(Entitlement.FREE.allows(it))
            assertTrue(Entitlement(true, EntitlementSource.CACHED, now).allows(it))
        }
    }
}
